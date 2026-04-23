package com.pbt.nabratts
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
class ModelDownloader {
    interface DownloadCallback {
        fun onProgress(modelName: String, progress: Int)
        fun onSuccess(modelName: String)
        fun onError(modelName: String, error: String)
    }
    companion object {
        private const val GITHUB_OWNER   = "mabdulhakim248-crypto"
        private const val GITHUB_REPO    = "nabratts-models"
        private const val RELEASE_TAG    = "v1.0"
        private const val GH_TOKEN       = "YOUR_GHP_TOKEN"
        private const val GITHUB_API     = "api.github.com"
        private const val MIN_FILE_BYTES = 1_000_000L
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT    = 300_000
    }
    private val modelFileNames = mapOf(
        "mixer128"  to "mixer128.onnx",
        "vocos22k"  to "vocos22.onnx",
        "vocos44k"  to "vocos44.onnx",
        "shakkelha" to "shakkelha.onnx"
    )
    private fun getAssetId(modelFileName: String): Long {
        val apiUrl = "https://$GITHUB_API/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/tags/$RELEASE_TAG"
        val conn = openGithubConnection(apiUrl, acceptJson = true)
        conn.connect()
        val httpCode = conn.responseCode
        if (httpCode != 200) {
            conn.errorStream?.close()
            conn.disconnect()
            throw Exception("لا يمكن الوصول لخادم GitHub (HTTP $httpCode). تحقق من اتصال الإنترنت.")
        }
        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val releaseJson = org.json.JSONObject(json)
        val assets      = releaseJson.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name") == modelFileName) {
                return asset.getLong("id")
            }
        }
        throw Exception("الملف '$modelFileName' غير موجود في الإصدار $RELEASE_TAG")
    }
    private fun downloadAsset(
        assetId:    Long,
        targetFile: File,
        callback:   DownloadCallback,
        modelKey:   String
    ) {
        var currentUrl    = "https://$GITHUB_API/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/assets/$assetId"
        var isGithubUrl   = true
        var hops          = 0
        while (hops < 20) {
            val conn = if (isGithubUrl) {
                openGithubConnection(currentUrl, acceptJson = false)
            } else {
                openPlainConnection(currentUrl)
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                    ?: throw Exception("خطأ في إعادة التوجيه: لا يوجد رأس Location")
                conn.disconnect()
                currentUrl  = if (loc.startsWith("http")) loc
                              else URL(URL(currentUrl), loc).toString()
                isGithubUrl = URL(currentUrl).host.endsWith(GITHUB_API)
                hops++
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                throw Exception(
                    "فشل التحميل (HTTP $code).\n" +
                    "تأكد من: اتصال الإنترنت، وأن الجهاز يثق بشهادات GitHub."
                )
            }
            val total  = conn.contentLengthLong
            val input  = BufferedInputStream(conn.inputStream, 65_536)
            try {
                val output = FileOutputStream(targetFile)
                try {
                    val buf    = ByteArray(65_536)
                    var done   = 0L
                    var n:     Int
                    var lastPct = -1
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                callback.onProgress(modelKey, pct)
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
            if (targetFile.length() < MIN_FILE_BYTES) {
                targetFile.delete()
                throw Exception(
                    "الملف المحمّل صغير جداً (${targetFile.length()} بايت).\n" +
                    "يبدو أن الخادم أرسل رسالة خطأ بدلاً من الملف الحقيقي."
                )
            }
            return
        }
        throw Exception("عدد كبير جداً من إعادات التوجيه للملف $modelKey")
    }
    private fun openGithubConnection(url: String, acceptJson: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization",     "token $GH_TOKEN")
        conn.setRequestProperty("Accept",
            if (acceptJson) "application/vnd.github.v3+json"
            else            "application/octet-stream")
        conn.setRequestProperty("User-Agent",        "NabraTTS/1.0 Android")
        conn.connectTimeout            = CONNECT_TIMEOUT
        conn.readTimeout               = READ_TIMEOUT
        conn.instanceFollowRedirects   = false
        return conn
    }
    private fun openPlainConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent",       "NabraTTS/1.0 Android")
        conn.connectTimeout           = CONNECT_TIMEOUT
        conn.readTimeout              = READ_TIMEOUT
        conn.instanceFollowRedirects  = false
        return conn
    }
    fun downloadModel(modelKey: String, targetFile: File, callback: DownloadCallback) {
        val fileName = modelFileNames[modelKey]
            ?: return callback.onError(modelKey, "مفتاح النموذج غير معروف: $modelKey")
        thread {
            try {
                if (targetFile.exists()) targetFile.delete()
                targetFile.parentFile?.mkdirs()
                val assetId = getAssetId(fileName)
                downloadAsset(assetId, targetFile, callback, modelKey)
                callback.onSuccess(modelKey)
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                callback.onError(modelKey, e.message ?: "فشل التحميل")
            }
        }
    }
}
