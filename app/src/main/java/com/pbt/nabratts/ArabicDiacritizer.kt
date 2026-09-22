package com.pbt.nabratts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Collections

class ArabicDiacritizer(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val inferLock = Any()

    private val charToIdx = HashMap<String, Int>()
    private val idxToDiac = HashMap<Int, String>()
    @Volatile
    private var isLoaded = false

    @Synchronized
    fun isLoaded(): Boolean = isLoaded && ortSession != null

    fun warmup() {
        if (!isLoaded()) return
        try {
            diacritizeWhole("بسم")
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun load(customPath: String? = null): Boolean {
        if (isLoaded && ortSession != null) return true
        return try {
            if (charToIdx.isEmpty() || idxToDiac.isEmpty()) {
                val vocabJson = loadVocab() ?: return false
                val charToIdxObj = vocabJson.getJSONObject("char_to_idx")
                val charKeys = charToIdxObj.keys()
                while (charKeys.hasNext()) {
                    val key = charKeys.next()
                    charToIdx[key] = charToIdxObj.getInt(key)
                }

                val diacToIdxObj = vocabJson.getJSONObject("diac_to_idx")
                val diacKeys = diacToIdxObj.keys()
                while (diacKeys.hasNext()) {
                    val key = diacKeys.next()
                    idxToDiac[diacToIdxObj.getInt(key)] = key
                }
            }

            val cpuCores = maxOf(2, Runtime.getRuntime().availableProcessors())
            ortEnv = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                setMemoryPatternOptimization(true)
                val intraThreads = if (cpuCores <= 2) 1 else if (cpuCores <= 4) 2 else minOf(4, cpuCores - 2)
                setIntraOpNumThreads(maxOf(1, intraThreads))
                setInterOpNumThreads(1)
                try {
                    addXnnpack(emptyMap())
                } catch (_: Throwable) {}
            }

            var modelFile: File? = null
            if (!customPath.isNullOrBlank()) {
                val candidate = File(customPath)
                if (candidate.exists() && candidate.length() >= 1_000_000) {
                    modelFile = candidate
                }
            }

            if (modelFile == null) {
                val candidate1 = File(File(context.filesDir, "models"), "rawi_ensemble.onnx")
                val candidate2 = File(File(context.filesDir, "model"), "rawi_ensemble.onnx")
                val candidate3 = File(context.cacheDir, "rawi_ensemble.onnx")
                when {
                    candidate1.exists() && candidate1.length() >= 1_000_000 -> modelFile = candidate1
                    candidate2.exists() && candidate2.length() >= 1_000_000 -> modelFile = candidate2
                    candidate3.exists() && candidate3.length() >= 1_000_000 -> modelFile = candidate3
                }
            }

            if (modelFile == null || !modelFile.exists()) {
                Log.e(TAG, "Rawi ensemble ONNX model not found")
                return false
            }

            ortSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            isLoaded = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Rawi diacritizer", e)
            isLoaded = false
            false
        }
    }

    fun diacritize(text: String): String = diacritizeWhole(text)

    fun diacritizeWhole(text: String): String {
        if (!isLoaded) {
            if (!load()) return text
        }
        if (text.isBlank()) return text

        synchronized(DIACRITICS_CACHE) {
            val cached = DIACRITICS_CACHE.get(text)
            if (cached != null) return cached
        }

        val bareSb = StringBuilder(text.length)
        for (i in text.indices) {
            val c = text[i]
            if (!isArabicDiacriticMark(c)) {
                bareSb.append(c)
            }
        }
        val bare = bareSb.toString()
        if (bare.isEmpty()) return text

        val unkId = unkForVocab()
        val tokenIds = LongArray(bare.length) { i ->
            val mapped = mapCharForVocab(bare[i])
            (charToIdx[mapped.toString()] ?: unkId).toLong()
        }
        val inputArray = arrayOf(tokenIds)

        return try {
            val env = ortEnv ?: return text
            val session = ortSession ?: return text
            val diacIds: LongArray?
            synchronized(inferLock) {
                val inputTensor = OnnxTensor.createTensor(env, inputArray)
                val results = session.run(Collections.singletonMap("input", inputTensor))
                val outputTensor = results[0]
                diacIds = extractIds(outputTensor.value)
                inputTensor.close()
                results.close()
            }
            if (diacIds == null) return text

            val out = StringBuilder()
            for (i in bare.indices) {
                val ch = bare[i]
                out.append(ch)
                if (i < diacIds.size && ch.isLetter()) {
                    val mark = idxToDiac[diacIds[i].toInt()]
                    if (!mark.isNullOrEmpty()) {
                        val sanitizedMark = ArabicTextNormalizer.sanitizeCharVowels(ch, mark)
                        out.append(sanitizedMark)
                    }
                }
            }
            val result = ArabicTextNormalizer.postSanitizeHamzaDiacritics(out.toString())
            synchronized(DIACRITICS_CACHE) {
                DIACRITICS_CACHE.put(text, result)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error during whole-string diacritization", e)
            text
        }
    }

    private fun unkForVocab(): Int = charToIdx["<UNK>"] ?: 1

    private fun mapCharForVocab(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        else -> c
    }

    private fun loadVocab(): JSONObject? {
        try {
            context.assets.open("model/vocab.json").use { inputStream ->
                val size = inputStream.available()
                val buffer = ByteArray(size)
                var read = 0
                while (read < size) {
                    val r = inputStream.read(buffer, read, size - read)
                    if (r == -1) break
                    read += r
                }
                return JSONObject(String(buffer, StandardCharsets.UTF_8))
            }
        } catch (_: Exception) {
        }
        return try {
            JSONObject(EMBEDDED_VOCAB_JSON)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing embedded vocab", e)
            null
        }
    }

    @Synchronized
    fun release() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing ONNX resources", e)
        }
        ortSession = null
        ortEnv = null
        isLoaded = false
    }

    data class CharWithDiac(var baseChar: Char, var diacritics: String)

    companion object {
        private const val TAG = "ArabicDiacritizer"
        private const val CACHE_SIZE = 1500
        private val DIACRITICS_CACHE = LruCache<String, String>(CACHE_SIZE)
        private const val TANWEEN_SET = "ًٌٍ"

        @JvmStatic
        fun isArabicDiacriticMark(c: Char): Boolean =
            (c in 'ً'..'ْ') || c == 'ٓ' || c == 'ٔ' || c == 'ٕ' || c == 'ٰ' || c == 'ـ'

        @JvmStatic
        fun isDiacriticChar(c: Char): Boolean =
            (c in 'ً'..'ْ') || c == 'ٓ' || c == 'ٔ' || c == 'ٕ' || c == 'ٰ'

        private fun isTanween(c: Char): Boolean = TANWEEN_SET.indexOf(c) != -1
        private fun isAlifChar(c: Char): Boolean = c == 'ا' || c == 'ى'

        private fun anyTanween(s: String): Boolean {
            for (i in s.indices) if (isTanween(s[i])) return true
            return false
        }

        private fun dropTanween(s: String): String {
            val b = StringBuilder()
            for (i in s.indices) if (!isTanween(s[i])) b.append(s[i])
            return b.toString()
        }

        private fun keepTanween(s: String): String {
            val b = StringBuilder()
            for (i in s.indices) if (isTanween(s[i])) b.append(s[i])
            return b.toString()
        }

        private fun dropShortVowels(s: String): String {
            val b = StringBuilder()
            for (i in s.indices) {
                val c = s[i]
                if (c != 'َ' && c != 'ُ' && c != 'ِ') b.append(c)
            }
            return b.toString()
        }

        private fun dedupeTanweenAlif(pairs: MutableList<CharWithDiac>) {
            val n = pairs.size
            for (i in 0 until n - 1) {
                val cur = pairs[i]
                val next = pairs[i + 1]
                val isLastAlif = (i + 1 == n - 1) && isAlifChar(next.baseChar)
                if (!isLastAlif) continue

                val consT = anyTanween(cur.diacritics)
                val alifT = anyTanween(next.diacritics)
                if (consT && alifT) {
                    cur.diacritics = dropTanween(cur.diacritics)
                } else if (consT && !alifT) {
                    val moved = keepTanween(cur.diacritics)
                    cur.diacritics = dropTanween(cur.diacritics)
                    next.diacritics = dropShortVowels(next.diacritics) + moved
                }
            }
        }

        @JvmStatic
        fun parseCharsWithDiac(text: String): MutableList<CharWithDiac> {
            val list = mutableListOf<CharWithDiac>()
            var i = 0
            val len = text.length
            while (i < len) {
                val base = text[i]
                i++
                val diac = StringBuilder()
                while (i < len && isDiacriticChar(text[i])) {
                    diac.append(text[i])
                    i++
                }
                list.add(CharWithDiac(base, diac.toString()))
            }
            return list
        }

        @JvmStatic
        fun mergeUserDiacritics(originalText: String?, modelDiacritized: String?): String {
            if (originalText.isNullOrEmpty() || modelDiacritized.isNullOrEmpty()) {
                return ArabicTextNormalizer.postSanitizeHamzaDiacritics(modelDiacritized ?: "")
            }

            val normOrig = Normalizer.normalize(originalText, Normalizer.Form.NFC)
            val normModel = Normalizer.normalize(modelDiacritized, Normalizer.Form.NFC)
            val origList = parseCharsWithDiac(normOrig)
            val modelList = parseCharsWithDiac(normModel)

            if (origList.size != modelList.size) {
                val oWords = normOrig.split(" ")
                val mWords = normModel.split(" ")
                if (oWords.size > 1 && oWords.size == mWords.size) {
                    val sb = StringBuilder()
                    for (i in oWords.indices) {
                        if (i > 0) sb.append(" ")
                        sb.append(mergeUserDiacritics(oWords[i], mWords[i]))
                    }
                    return ArabicTextNormalizer.postSanitizeHamzaDiacritics(sb.toString())
                }
                return ArabicTextNormalizer.postSanitizeHamzaDiacritics(normModel)
            }

            val merged = mutableListOf<CharWithDiac>()
            for (i in origList.indices) {
                val orig = origList[i]
                val model = modelList[i]
                var diac = if (orig.diacritics.isNotEmpty()) orig.diacritics else model.diacritics
                diac = ArabicTextNormalizer.sanitizeCharVowels(orig.baseChar, diac)
                merged.add(CharWithDiac(orig.baseChar, diac))
            }
            dedupeTanweenAlif(merged)

            val sb = StringBuilder()
            for (cd in merged) {
                sb.append(cd.baseChar)
                if (cd.diacritics.isNotEmpty()) sb.append(cd.diacritics)
            }
            val res = Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
            return ArabicTextNormalizer.postSanitizeHamzaDiacritics(res)
        }

        private fun extractIds(outputValue: Any?): LongArray? {
            if (outputValue == null) return null
            if (outputValue is Array<*> && outputValue.isNotEmpty() && outputValue[0] is LongArray) {
                return outputValue[0] as LongArray
            }
            if (outputValue is LongArray) return outputValue
            if (outputValue is Array<*> && outputValue.isNotEmpty() && outputValue[0] is IntArray) {
                val a = outputValue[0] as IntArray
                return LongArray(a.size) { idx -> a[idx].toLong() }
            }
            if (outputValue is IntArray) {
                return LongArray(outputValue.size) { idx -> outputValue[idx].toLong() }
            }
            return null
        }

        private const val EMBEDDED_VOCAB_JSON = "{\"char_to_idx\": {\"<PAD>\": 0, \"<UNK>\": 1, \"\\t\": 2, \" \": 3, \"!\": 4, \"\\\"\": 5, \"#\": 6, \"\$\": 7, \"%\": 8, \"&\": 9, \"'\": 10, \"(\": 11, \")\": 12, \"*\": 13, \"+\": 14, \",\": 15, \"-\": 16, \".\": 17, \"/\": 18, \"0\": 19, \"1\": 20, \"2\": 21, \"3\": 22, \"4\": 23, \"5\": 24, \"6\": 25, \"7\": 26, \"8\": 27, \"9\": 28, \":\": 29, \";\": 30, \"=\": 31, \">\": 32, \"?\": 33, \"@\": 34, \"A\": 35, \"B\": 36, \"C\": 37, \"D\": 38, \"E\": 39, \"F\": 40, \"G\": 41, \"H\": 42, \"I\": 43, \"J\": 44, \"K\": 45, \"L\": 46, \"M\": 47, \"N\": 48, \"O\": 49, \"P\": 50, \"Q\": 51, \"R\": 52, \"S\": 53, \"T\": 54, \"U\": 55, \"V\": 56, \"W\": 57, \"X\": 58, \"Y\": 59, \"Z\": 60, \"[\": 61, \"\\\\\": 62, \"]\": 63, \"^\": 64, \"_\": 65, \"`\": 66, \"a\": 67, \"b\": 68, \"c\": 69, \"d\": 70, \"e\": 71, \"f\": 72, \"g\": 73, \"h\": 74, \"i\": 75, \"j\": 76, \"k\": 77, \"l\": 78, \"m\": 79, \"n\": 80, \"o\": 81, \"p\": 82, \"q\": 83, \"r\": 84, \"s\": 85, \"t\": 86, \"u\": 87, \"v\": 88, \"w\": 89, \"x\": 90, \"y\": 91, \"z\": 92, \"{\": 93, \"|\": 94, \"}\": 95, \"~\": 96, \"«\": 97, \"´\": 98, \"»\": 99, \"¼\": 100, \"½\": 101, \"×\": 102, \"ı\": 103, \"β\": 104, \"А\": 105, \"В\": 106, \"Д\": 107, \"Е\": 108, \"К\": 109, \"М\": 110, \"Н\": 111, \"О\": 112, \"П\": 113, \"Р\": 114, \"С\": 115, \"Т\": 116, \"Х\": 117, \"а\": 118, \"б\": 119, \"е\": 120, \"и\": 121, \"к\": 122, \"л\": 123, \"о\": 124, \"с\": 125, \"т\": 126, \"у\": 127, \"ц\": 128, \"я\": 129, \"א\": 130, \"ב\": 131, \"ד\": 132, \"ה\": 133, \"ו\": 134, \"י\": 135, \"כ\": 136, \"ל\": 137, \"ם\": 138, \"מ\": 139, \"ס\": 140, \"ע\": 141, \"ק\": 142, \"ר\": 143, \"ת\": 144, \"،\": 145, \"؛\": 146, \"؟\": 147, \"ء\": 148, \"ا\": 149, \"ب\": 150, \"ة\": 151, \"ت\": 152, \"ث\": 153, \"ج\": 154, \"ح\": 155, \"خ\": 156, \"د\": 157, \"ذ\": 158, \"ر\": 159, \"ز\": 160, \"س\": 161, \"ش\": 162, \"ص\": 163, \"ض\": 164, \"ط\": 165, \"ظ\": 166, \"ع\": 167, \"غ\": 168, \"ـ\": 169, \"ف\": 170, \"ق\": 171, \"ك\": 172, \"ل\": 173, \"م\": 174, \"ن\": 175, \"ه\": 176, \"و\": 177, \"ى\": 178, \"ي\": 179, \"٠\": 180, \"١\": 181, \"٢\": 182, \"٣\": 183, \"٤\": 184, \"٥\": 185, \"٦\": 186, \"٧\": 187, \"٨\": 188, \"٩\": 189, \"٪\": 190, \"٬\": 191, \"ٱ\": 192, \"پ\": 193, \"چ\": 194, \"ژ\": 195, \"ڤ\": 196, \"ک\": 197, \"گ\": 198, \"ہ\": 199, \"ی\": 200, \"ے\": 201, \"۲\": 202, \"۵\": 203, \"۸\": 204, \"​\": 205, \"‌\": 206, \"‍\": 207, \"‎\": 208, \"‏\": 209, \"–\": 210, \"—\": 211, \"‘\": 212, \"’\": 213, \"“\": 214, \"”\": 215, \"•\": 216, \"…\": 217, \"‫\": 218, \"‬\": 219, \"‭\": 220, \"‼\": 221, \"⁧\": 222, \"⁩\": 223, \"₪\": 224, \"⅔\": 225, \"↓\": 226, \"﴾\": 227, \"﴿\": 228, \"ﺴ\": 229, \"ﻟ\": 230, \"ﻤ\": 231, \"ﻮ\": 232, \"ﻻ\": 233, \"🏻\": 234, \"🏼\": 235}, \"diac_to_idx\": {\"\": 0, \"̀\": 1, \"́\": 2, \"̃\": 3, \"̄\": 4, \"̇\": 5, \"̈\": 6, \"̊\": 7, \"̧\": 8, \"̨\": 9, \"ً\": 10, \"ًَ\": 11, \"ًّ\": 12, \"ًٔ\": 13, \"ٌ\": 14, \"ٌّ\": 15, \"ٌٔ\": 16, \"ٌٰ\": 17, \"ٍ\": 18, \"ٍّ\": 19, \"ٍّٔ\": 20, \"ٍٔ\": 21, \"ٍٕ\": 22, \"ٍ️\": 23, \"َ\": 24, \"ََ\": 25, \"ََّ\": 26, \"ََّٔ\": 27, \"ََٔ\": 28, \"َُ\": 29, \"َِ\": 30, \"َّ\": 31, \"َّٓ\": 32, \"َّٔ\": 33, \"َّٕ\": 34, \"َٓ\": 35, \"َٔ\": 36, \"َٕ\": 37, \"َٰ\": 38, \"ُ\": 39, \"ُُ\": 40, \"ُِ\": 41, \"ُّ\": 42, \"ُّٔ\": 43, \"ُٓ\": 44, \"ُٔ\": 45, \"ُٕ\": 46, \"ِ\": 47, \"ِِ\": 48, \"ِِّ\": 49, \"ِِٕ\": 50, \"ِّ\": 51, \"ِّٔ\": 52, \"ِّٰ\": 53, \"ِْ\": 54, \"ِٓ\": 55, \"ِٔ\": 56, \"ِٕ\": 57, \"ِ️\": 58, \"ّ\": 59, \"ّّ\": 60, \"ّْ\": 61, \"ّٔ\": 62, \"ْ\": 63, \"ْْ\": 64, \"ْٓ\": 65, \"ْٔ\": 66, \"ٓ\": 67, \"ٔ\": 68, \"ٔۡ\": 69, \"ٕ\": 70, \"ٰ\": 71, \"ۘ\": 72, \"ۡ\": 73, \"️\": 74}}"
    }
}
