package com.pbt.nabratts

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class ModelDownloader {
    interface DownloadCallback {
        fun onProgress(modelName: String, progress: Int)
        fun onSuccess(modelName: String)
        fun onError(modelName: String, error: String)
    }

    interface BatchCallback {
        fun onProgress(progress: Int)
        fun onSuccess()
        fun onError(error: String)
    }

    companion object {
        private const val HF_USER = "Mohamad-I8"
        private const val HF_REPO = "nabratts-models"
        private const val HF_BASE = "https://huggingface.co/$HF_USER/$HF_REPO/resolve/main"
        private const val RAWI_HF_BASE = "https://huggingface.co/TigreGotico/rawi-ensemble/resolve/main"

        private const val MIN_FILE_BYTES = 500_000L
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 600_000
        private const val MAX_REDIRECTS = 10
    }

    val modelUrls = mapOf(
        "mixer128" to "$HF_BASE/mixer128.onnx",
        "vocos22k" to "$HF_BASE/vocos22.onnx",
        "vocos44k" to "$HF_BASE/vocos44.onnx",
        "rawi_ensemble" to "$RAWI_HF_BASE/rawi_ensemble.onnx"
    )

    private val cancelledKeys = Collections.synchronizedSet(HashSet<String>())
    private val activeThreads = ConcurrentHashMap<String, Thread>()

    @Volatile private var batchThread: Thread? = null
    @Volatile private var isBatchCancelled = false

    fun cancel(modelKey: String) {
        cancelledKeys.add(modelKey)
        activeThreads[modelKey]?.interrupt()
    }

    fun isDownloading(modelKey: String): Boolean = activeThreads.containsKey(modelKey)

    fun cancelBatch() {
        isBatchCancelled = true
        batchThread?.interrupt()
        for (key in modelUrls.keys) {
            cancelledKeys.add(key)
        }
    }

    fun isBatchRunning(): Boolean = batchThread?.isAlive == true

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 NabraTTS/1.0")
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = false
            conn.connect()

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        URL(URL(currentUrl), location).toString()
                    }
                    redirects++
                    continue
                }
            }
            return conn
        }
        throw Exception("تجاوز عدد مرات إعادة التوجيه المسموح بها ($redirects)")
    }

    private fun downloadFileInternal(
        url: String,
        targetFile: File,
        modelKey: String,
        onProgress: (Int) -> Unit
    ) {
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tmpFile.exists()) tmpFile.delete()

        val conn = openConnectionWithRedirects(url)
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw Exception("فشل التحميل (رمز الاستجابة: $code). يرجى التحقق من اتصال الإنترنت.")
        }

        val total = conn.contentLengthLong
        val input = BufferedInputStream(conn.inputStream, 65_536)
        try {
            val output = FileOutputStream(tmpFile)
            try {
                val buf = ByteArray(65_536)
                var done = 0L
                var n: Int
                var lastPct = -1
                while (input.read(buf).also { n = it } != -1) {
                    if (cancelledKeys.contains(modelKey) || isBatchCancelled || Thread.currentThread().isInterrupted) {
                        throw InterruptedException("تم إلغاء التحميل بواسطة المستخدم")
                    }
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
                output.flush()
            } finally {
                output.close()
            }
        } finally {
            input.close()
            conn.disconnect()
        }

        if (tmpFile.length() < MIN_FILE_BYTES) {
            tmpFile.delete()
            throw Exception("الملف المحمّل غير مكتمل (${tmpFile.length()} بايت).")
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    fun downloadModel(modelKey: String, targetFile: File, callback: DownloadCallback) {
        val downloadUrl = modelUrls[modelKey]
            ?: return callback.onError(modelKey, "مفتاح النموذج غير معروف: $modelKey")

        cancelledKeys.remove(modelKey)
        val t = thread(start = false) {
            try {
                targetFile.parentFile?.mkdirs()
                downloadFileInternal(downloadUrl, targetFile, modelKey) { pct ->
                    callback.onProgress(modelKey, pct)
                }
                callback.onSuccess(modelKey)
            } catch (e: InterruptedException) {
                val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                if (tmpFile.exists()) tmpFile.delete()
            } catch (e: Exception) {
                val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                if (tmpFile.exists()) tmpFile.delete()
                callback.onError(modelKey, e.message ?: "فشل التحميل")
            } finally {
                activeThreads.remove(modelKey)
                cancelledKeys.remove(modelKey)
            }
        }
        activeThreads[modelKey] = t
        t.start()
    }

    fun downloadBatch(
        items: List<Pair<String, File>>,
        callback: BatchCallback
    ) {
        if (items.isEmpty()) {
            callback.onProgress(100)
            callback.onSuccess()
            return
        }

        isBatchCancelled = false
        for ((key, _) in items) {
            cancelledKeys.remove(key)
        }

        val totalItems = items.size
        val t = thread(start = false) {
            try {
                for ((index, item) in items.withIndex()) {
                    if (isBatchCancelled || Thread.currentThread().isInterrupted) {
                        throw InterruptedException("تم إلغاء التحميل")
                    }
                    val (modelKey, targetFile) = item
                    val downloadUrl = modelUrls[modelKey]
                        ?: throw Exception("مفتاح النموذج غير معروف: $modelKey")

                    targetFile.parentFile?.mkdirs()
                    downloadFileInternal(downloadUrl, targetFile, modelKey) { filePct ->
                        val overallPct = ((index * 100) + filePct) / totalItems
                        callback.onProgress(overallPct.coerceIn(0, 100))
                    }
                }
                if (isBatchCancelled || Thread.currentThread().isInterrupted) {
                    throw InterruptedException("تم إلغاء التحميل")
                }
                callback.onProgress(100)
                callback.onSuccess()
            } catch (e: InterruptedException) {
                for ((_, targetFile) in items) {
                    val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
                    if (tmp.exists()) tmp.delete()
                }
            } catch (e: Exception) {
                for ((_, targetFile) in items) {
                    val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
                    if (tmp.exists()) tmp.delete()
                }
                callback.onError(e.message ?: "فشل التحميل")
            } finally {
                batchThread = null
                isBatchCancelled = false
            }
        }
        batchThread = t
        t.start()
    }
}
