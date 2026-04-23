package com.pbt.nabratts
import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
class NativeTtsService : TextToSpeechService() {
    private val modelLock = Any()
    private var shakkelha: ShakkelhaModel? = null
    private var tts: MixerTtsModel? = null
    private var currentVocosPath: String? = null
    @Volatile private var isStopped = false
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        initModels()
    }
    /**
     * Fix for SecurityException on Android 11+ (especially Samsung)
     * when the system tries to send internal broadcasts like 
     * android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED
     */
    override fun sendBroadcast(intent: Intent?) {
        if (intent?.action == TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED || 
            intent?.action == "android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED") {
            // Ignore this broadcast to avoid SecurityException on newer Android versions
            return
        }
        try {
            super.sendBroadcast(intent)
        } catch (e: SecurityException) {
            Log.w(TAG, "Caught SecurityException for broadcast: ${intent?.action}")
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast error: ${e.message}")
        }
    }
    private fun initModels() {
        val settings = SettingsManager(applicationContext)
        if (!settings.areAllModelsDownloaded()) return
        synchronized(modelLock) {
            try {
                shakkelha?.release()
                tts?.release()
                shakkelha = ShakkelhaModel(applicationContext).also {
                    it.initSession(settings.getShakkelhaPath())
                }
                tts = MixerTtsModel(applicationContext).also {
                    it.initSessions(settings.getMixerPath(), settings.getVocosPath())
                }
                currentVocosPath = settings.getVocosPath()
            } catch (e: Exception) {
                Log.e(TAG, "Error initialising models: ${e.message}")
            }
        }
    }
    override fun onDestroy() {
        synchronized(modelLock) {
            shakkelha?.release()
            tts?.release()
            shakkelha = null
            tts = null
        }
        super.onDestroy()
    }
    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int =
        if (lang?.startsWith("ar", ignoreCase = true) == true) TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        else TextToSpeech.LANG_AVAILABLE
    override fun onGetLanguage(): Array<String> = arrayOf("ar", "", "")
    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)
    override fun onGetVoices(): MutableList<Voice> {
        val voices = mutableListOf<Voice>()
        voices.add(
            Voice(
                "ar-Voice",
                Locale("ar"),
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                null
            )
        )
        return voices
    }
    override fun onIsValidVoiceName(voiceName: String?): Int {
        return if (voiceName == "ar-Voice") TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }
    override fun onLoadVoice(voiceName: String?): Int {
        return if (voiceName == "ar-Voice") TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }
    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String? {
        return if (lang?.startsWith("ar", ignoreCase = true) == true) "ar-Voice" else null
    }
    override fun onStop() {
        isStopped = true
    }
    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        isStopped = false
        val text = request?.charSequenceText?.toString() ?: return
        val settings = SettingsManager(applicationContext)
        if (tts == null || currentVocosPath != settings.getVocosPath()) initModels()
        synchronized(modelLock) {
            if (tts == null) {
                Log.e(TAG, "Models not loaded"); callback?.error(); return
            }
        }
        val modelPace     = settings.pace.coerceIn(0.5f, 2.0f)
        val finalPitchMul = settings.pitchMul.coerceIn(0.5f, 2.0f)
        val finalPitchAdd = settings.pitchAdd.coerceIn(-10.0f, 10.0f)
        val sampleRate    = settings.getSampleRate()
        Log.d(TAG, "Synthesizing: \"$text\"")
        Log.d(TAG, "pace=${settings.pace} → modelPace=$modelPace | " +
                   "pitchMul=$finalPitchMul | pitchAdd=$finalPitchAdd | sentenceBySentence=${settings.isSentenceBySentenceEnabled}")
        try {
            val userDict = UserDictionary(applicationContext)
            if (settings.isSentenceBySentenceEnabled) {
                synthesizeSentenceBySentence(
                    text, settings, userDict, modelPace,
                    finalPitchMul, finalPitchAdd, sampleRate, callback
                )
            } else {
                synthesizeWhole(
                    text, settings, userDict, modelPace,
                    finalPitchMul, finalPitchAdd, sampleRate, callback
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis ERROR: ${e.message}", e)
            callback?.error()
        }
    }
    private fun synthesizeWhole(
        text: String, settings: SettingsManager, userDict: UserDictionary,
        modelPace: Float, pitchMul: Float, pitchAdd: Float,
        sampleRate: Int, callback: SynthesisCallback?
    ) {
        val preprocessed = TextPreprocessor.preprocessText(text, userDict)
        val processedText = if (settings.isVowelizerEnabled) {
            shakkelha?.vowelize(preprocessed) ?: preprocessed
        } else preprocessed
        Log.d(TAG, "Preprocessed & Vowelised: $processedText")
        val pcmData = tts?.ttsBytes(
            processedText,
            pace    = modelPace,
            speaker = settings.speakerId,
            padd    = pitchAdd,
            pmul    = pitchMul,
            denoise = settings.denoise
        )
        if (pcmData != null && pcmData.isNotEmpty()) {
            Log.d(TAG, "Generated PCM: ${pcmData.size} bytes")
            callback?.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            writeToCallback(pcmData, callback)
            callback?.done()
            Log.d(TAG, "Synthesis SUCCESS at $sampleRate Hz")
        } else {
            Log.e(TAG, "PCM data is null or empty"); callback?.error()
        }
    }
    private fun synthesizeSentenceBySentence(
        text: String, settings: SettingsManager, userDict: UserDictionary,
        modelPace: Float, pitchMul: Float, pitchAdd: Float,
        sampleRate: Int, callback: SynthesisCallback?
    ) {
        val sentences = text.split(SENTENCE_SPLIT_RE)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) { callback?.error(); return }
        Log.d(TAG, "Sentence-by-sentence: ${sentences.size} sentence(s)")
        callback?.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
        for ((idx, sentence) in sentences.withIndex()) {
            if (isStopped) {
                Log.d(TAG, "Stopped at sentence $idx"); break
            }
            val preprocessed = TextPreprocessor.preprocessText(sentence, userDict)
            if (preprocessed.isBlank()) {
                Log.d(TAG, "Sentence $idx is blank after preprocessing, skipping")
                continue
            }
            val processedText = if (settings.isVowelizerEnabled) {
                shakkelha?.vowelize(preprocessed) ?: preprocessed
            } else preprocessed
            Log.d(TAG, "Sentence $idx: «$processedText»")
            val pcm = tts?.ttsBytes(
                processedText,
                pace    = modelPace,
                speaker = settings.speakerId,
                padd    = pitchAdd,
                pmul    = pitchMul,
                denoise = settings.denoise
            )
            if (pcm != null && pcm.isNotEmpty()) {
                Log.d(TAG, "Sentence $idx: ${pcm.size} bytes")
                writeToCallback(pcm, callback)
            } else {
                Log.w(TAG, "Empty PCM for sentence $idx")
            }
        }
        callback?.done()
        Log.d(TAG, "Sentence-by-sentence synthesis DONE")
    }
    private fun writeToCallback(pcmData: ByteArray, callback: SynthesisCallback?) {
        val maxBuf = callback?.maxBufferSize ?: 4096
        var offset = 0
        while (offset < pcmData.size) {
            if (isStopped) break
            val bytes = minOf(maxBuf, pcmData.size - offset)
            callback?.audioAvailable(pcmData, offset, bytes)
            offset += bytes
        }
    }
    companion object {
        private const val TAG = "NativeTtsService"
        private val SENTENCE_SPLIT_RE = Regex("""[.،؛؟!?,;:\n]+""")
    }
}
class CheckVoiceData : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val returnIntent = Intent()
        val availableVoices = ArrayList<String>()
        availableVoices.add("ar")
        availableVoices.add("ar-SA")
        availableVoices.add("ar-EG")
        availableVoices.add("ar-AE")
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices)
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList<String>())
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnIntent)
        finish()
    }
}
class GetSampleText : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val returnIntent = Intent()
        returnIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, "هذا هو محرك نبرة لتحويل النص إلى كلام.")
        setResult(TextToSpeech.LANG_AVAILABLE, returnIntent)
        finish()
    }
}
