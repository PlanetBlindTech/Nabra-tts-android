package com.pbt.nabratts
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
class MixerTtsModel(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var ttsSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null
    fun initSessions(
        modelPath: String,
        vocoderPath: String
    ) {
        env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { sessionOptions ->
            val ttsBytes = java.io.File(modelPath).readBytes()
            ttsSession = env?.createSession(ttsBytes, sessionOptions)
            val vocoderBytes = java.io.File(vocoderPath).readBytes()
            vocoderSession = env?.createSession(vocoderBytes, sessionOptions)
        }
    }
    fun release() {
        ttsSession?.close()
        vocoderSession?.close()
        ttsSession = null
        vocoderSession = null
    }
    fun ttsBytes(
        text: String,
        pace: Float = 1.0f,
        speaker: Int = 0,
        pmul: Float = 1.0f,
        padd: Float = 0.0f,
        denoise: Float = 0.005f
    ): ByteArray? {
        val currentEnv = env ?: return null
        if (ttsSession == null || vocoderSession == null) return null
        val tokenIds = ArTokenizer.tokenizerPhon(text)
        val tokenShape = longArrayOf(1, tokenIds.size.toLong())
        val tokenTensor = OnnxTensor.createTensor(currentEnv, LongBuffer.wrap(tokenIds), tokenShape)
        val paceTensor = OnnxTensor.createTensor(currentEnv, floatArrayOf(pace))
        val speakerTensor = OnnxTensor.createTensor(currentEnv, intArrayOf(speaker))
        val pmulTensor = OnnxTensor.createTensor(currentEnv, floatArrayOf(pmul))
        val paddTensor = OnnxTensor.createTensor(currentEnv, floatArrayOf(padd))
        try {
            val ttsInputs = mapOf(
                "token_ids" to tokenTensor,
                "pace" to paceTensor,
                "speaker" to speakerTensor,
                "pitch_mul" to pmulTensor,
                "pitch_add" to paddTensor
            )
            val ttsResult = ttsSession?.run(ttsInputs) ?: return null
            try {
                val melSpecOrt = ttsResult.get(0) as? OnnxTensor ?: return null
                val denoiseTensor = OnnxTensor.createTensor(currentEnv, floatArrayOf(denoise))
                try {
                    val vocoderInputs = mapOf(
                        "mel_spec" to melSpecOrt,
                        "denoise" to denoiseTensor
                    )
                    val vocoderResult = vocoderSession?.run(vocoderInputs) ?: return null
                    try {
                        val waveOutOrt = vocoderResult.get(0) as? OnnxTensor ?: return null
                        val waveData = waveOutOrt.value as Array<*>
                        val floatArray = (waveData[0] as FloatArray)
                        return convertToPCM16(floatArray)
                    } finally {
                        vocoderResult.close()
                    }
                } finally {
                    denoiseTensor.close()
                }
            } finally {
                ttsResult.close()
            }
        } finally {
            tokenTensor.close()
            paceTensor.close()
            speakerTensor.close()
            pmulTensor.close()
            paddTensor.close()
        }
    }
    private fun convertToPCM16(audioData: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(audioData.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (sample in audioData) {
            buffer.putShort((sample * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        return buffer.array()
    }
}
class ShakkelhaModel(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private val arabicLettersList = "ءآأؤإئابةتثجحخدذرزسشصضطظعغفقكلمنهوىي"
    private val diacriticsList = listOf('َ', 'ً', 'ِ', 'ٍ', 'ُ', 'ٌ', 'ْ', 'ّ')
    private val rnnBigCharactersMapping = mapOf(
        "<PAD>" to 0, "<SOS>" to 1, "<EOS>" to 2, "<UNK>" to 3,
        "\n" to 4, " " to 5, "!" to 6, "\"" to 7, "&" to 8, "'" to 9,
        "(" to 10, ")" to 11, "*" to 12, "+" to 13, "," to 14, "-" to 15,
        "." to 16, "/" to 17, "0" to 18, "1" to 19, "2" to 20, "3" to 21,
        "4" to 22, "5" to 23, "6" to 24, "7" to 25, "8" to 26, "9" to 27,
        ":" to 28, ";" to 29, "=" to 30, "[" to 31, "]" to 32, "_" to 33,
        "`" to 34, "{" to 35, "}" to 36, "~" to 37, "«" to 38, "»" to 39,
        "،" to 40, "؛" to 41, "؟" to 42, "ء" to 43, "آ" to 44, "أ" to 45,
        "ؤ" to 46, "إ" to 47, "ئ" to 48, "ا" to 49, "ب" to 50, "ة" to 51,
        "ت" to 52, "ث" to 53, "ج" to 54, "ح" to 55, "خ" to 56, "د" to 57,
        "ذ" to 58, "ر" to 59, "ز" to 60, "س" to 61, "ش" to 62, "ص" to 63,
        "ض" to 64, "ط" to 65, "ظ" to 66, "ع" to 67, "غ" to 68, "ف" to 69,
        "ق" to 70, "ك" to 71, "ل" to 72, "م" to 73, "ن" to 74, "ه" to 75,
        "و" to 76, "ى" to 77, "ي" to 78
    )
    private val rnnRevClassesMapping = mapOf(
        0 to "", 1 to "َ", 2 to "ً", 3 to "ُ", 4 to "ٌ", 5 to "ِ",
        6 to "ٍ", 7 to "ْ", 8 to "ّ", 9 to "َّ", 10 to "ًّ", 11 to "ُّ",
        12 to "ٌّ", 13 to "ِّ", 14 to "ٍّ", 15 to "<PAD>", 16 to "<SOS>",
        17 to "<EOS>", 18 to "<N/A>"
    )
    fun initSession(modelPath: String) {
        env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { opts ->
            val bytes = java.io.File(modelPath).readBytes()
            session = env?.createSession(bytes, opts)
        }
    }
    fun release() {
        session?.close()
        session = null
    }
    private fun removeDiacritics(data: String): String {
        var str = data
        diacriticsList.forEach { diacritic ->
            str = str.replace(diacritic.toString(), "")
        }
        return str
    }
    private fun encode(inputText: String): LongArray {
        val x = mutableListOf<Long>()
        x.add(rnnBigCharactersMapping["<SOS>"]!!.toLong())
        for (char in inputText) {
            if (diacriticsList.contains(char)) continue
            val charStr = char.toString()
            x.add((rnnBigCharactersMapping[charStr] ?: rnnBigCharactersMapping["<UNK>"]!!).toLong())
        }
        x.add(rnnBigCharactersMapping["<EOS>"]!!.toLong())
        return x.toLongArray()
    }
    private fun decode(probs: Array<Array<FloatArray>>, inputText: String): String {
        val probsList = probs[0].drop(1)
        val output = StringBuilder()
        val cleanText = removeDiacritics(inputText)
        for (i in cleanText.indices) {
            val charStr = cleanText[i].toString()
            output.append(charStr)
            if (!arabicLettersList.contains(charStr)) continue
            if (i < probsList.size) {
                var maxIdx = 0
                var maxVal = probsList[i][0]
                for (j in 1 until probsList[i].size) {
                    if (probsList[i][j] > maxVal) {
                        maxVal = probsList[i][j]
                        maxIdx = j
                    }
                }
                val mapping = rnnRevClassesMapping[maxIdx]
                if (mapping != null && !mapping.contains("<")) {
                    output.append(mapping)
                }
            }
        }
        return output.toString()
    }
    fun vowelize(text: String): String {
        val currentEnv = env ?: return text
        val currentSession = session ?: return text
        val inputIds = encode(text)
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(currentEnv, LongBuffer.wrap(inputIds), shape)
        try {
            val result = currentSession.run(mapOf("input" to inputTensor))
            try {
                val outputTensor = result.get(0) as? OnnxTensor
                @Suppress("UNCHECKED_CAST")
                val probsOut = outputTensor?.value as? Array<Array<FloatArray>>
                return if (probsOut != null) decode(probsOut, text) else text
            } finally {
                result.close()
            }
        } finally {
            inputTensor.close()
        }
    }
}
