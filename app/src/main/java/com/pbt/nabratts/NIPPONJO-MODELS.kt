package com.pbt.nabratts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
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
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val intraThreads = when {
            cpuCores <= 2 -> 1
            cpuCores <= 4 -> 2
            else -> minOf(4, cpuCores - 1)
        }

        val sessionOptions = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setMemoryPatternOptimization(true)
            setIntraOpNumThreads(intraThreads)
            setInterOpNumThreads(1)
            try {
                addXnnpack(emptyMap())
            } catch (_: Throwable) {}
        }

        sessionOptions.use { options ->
            ttsSession = env?.createSession(modelPath, options)
            vocoderSession = env?.createSession(vocoderPath, options)
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
        val bytes = ByteArray(audioData.size * 2)
        var byteIdx = 0
        for (i in audioData.indices) {
            val sample = (audioData[i] * 32767f).toInt().coerceIn(-32768, 32767)
            bytes[byteIdx++] = (sample and 0xFF).toByte()
            bytes[byteIdx++] = ((sample ushr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
