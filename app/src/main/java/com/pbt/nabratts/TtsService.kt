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

class TtsService : TextToSpeechService() {
    private val modelLock = Any()
    private var diacritizer: ArabicDiacritizer? = null
    private var tts: MixerTtsModel? = null
    private var currentVocosPath: String? = null
    @Volatile private var isStopped = false
    private val voiceFeatures: HashSet<String> = hashSetOf(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS)

    override fun onCreate() {
        super.onCreate()
        try {
            DictionaryManager.loadDefault(applicationContext)
            DictionaryManager.initEmojiAsync(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Dictionary init error: ${e.message}")
        }
        // Initialize models in background so service bind never blocks the main thread
        Thread {
            try {
                initModels()
            } catch (e: Exception) {
                Log.e(TAG, "Background model init error: ${e.message}")
            }
        }.start()
    }

    override fun sendBroadcast(intent: Intent?) {
        if (intent?.action == TextToSpeech.ACTION_TTS_QUEUE_PROCESSING_COMPLETED || 
            intent?.action == "android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED") {
            return
        }
        try {
            super.sendBroadcast(intent)
        } catch (_: SecurityException) {
        } catch (e: Exception) {
            Log.e(TAG, "Broadcast error: ${e.message}")
        }
    }

    private fun initModels() {
        val settings = SettingsManager(applicationContext)
        if (!settings.areAllModelsDownloaded()) {
            synchronized(modelLock) {
                diacritizer?.release()
                tts?.release()
                diacritizer = null
                tts = null
            }
            return
        }
        synchronized(modelLock) {
            try {
                diacritizer?.release()
                tts?.release()
                diacritizer = ArabicDiacritizer(applicationContext).also {
                    it.load(settings.getRawiPath())
                }
                tts = MixerTtsModel(applicationContext).also {
                    it.initSessions(settings.getMixerPath(), settings.getVocosPath())
                }
                currentVocosPath = settings.getVocosPath()
            } catch (e: Exception) {
                Log.e(TAG, "Model init error: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        synchronized(modelLock) {
            diacritizer?.release()
            tts?.release()
            diacritizer = null
            tts = null
        }
        super.onDestroy()
    }

    private val validVoiceNames = setOf("ar-Voice", "ar-SA-Voice", "ar-EG-Voice", "ar-AE-Voice", "system-Voice")

    private fun isArabicLanguage(lang: String?): Boolean {
        if (lang.isNullOrBlank()) return false
        val l = lang.lowercase(Locale.ROOT).trim()
        return l == "ar" || l == "ara" || l.startsWith("ar_") || l.startsWith("ar-")
    }

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val settings = SettingsManager(applicationContext)
        return if (isArabicLanguage(lang)) {
            if (settings.areAllModelsDownloaded()) TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            else TextToSpeech.LANG_AVAILABLE
        } else {
            // Support any device language (English, French, Turkish, etc.) so NabraTTS is fully active
            TextToSpeech.LANG_AVAILABLE
        }
    }

    override fun onGetLanguage(): Array<String> {
        val def = Locale.getDefault()
        return try {
            val l = def.isO3Language
            val c = try { def.isO3Country } catch (_: Exception) { "" }
            arrayOf(l, c, def.variant ?: "")
        } catch (_: Exception) {
            arrayOf("ara", "SAU", "")
        }
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetFeaturesForLanguage(lang: String?, country: String?, variant: String?): Set<String> =
        voiceFeatures

    override fun onGetVoices(): MutableList<Voice> {
        val voices = mutableListOf<Voice>()
        voices.add(
            Voice(
                "ar-Voice",
                Locale("ar"),
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                voiceFeatures
            )
        )
        voices.add(
            Voice(
                "ar-SA-Voice",
                Locale("ar", "SA"),
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                voiceFeatures
            )
        )
        voices.add(
            Voice(
                "ar-EG-Voice",
                Locale("ar", "EG"),
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                voiceFeatures
            )
        )
        voices.add(
            Voice(
                "ar-AE-Voice",
                Locale("ar", "AE"),
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                voiceFeatures
            )
        )

        // Add a voice corresponding to the device's default locale so Settings and TalkBack
        // find a matching voice when the phone's system language is not Arabic.
        val defaultLocale = Locale.getDefault()
        if (!isArabicLanguage(defaultLocale.language)) {
            voices.add(
                Voice(
                    "system-Voice",
                    defaultLocale,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_LOW,
                    false,
                    voiceFeatures
                )
            )
        } else {
            voices.add(
                Voice(
                    "system-Voice",
                    Locale("ar"),
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_LOW,
                    false,
                    voiceFeatures
                )
            )
        }

        return voices
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (voiceName != null && (voiceName in validVoiceNames || voiceName.startsWith("ar-") || voiceName == "system-Voice")) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }

    override fun onLoadVoice(voiceName: String?): Int =
        if (voiceName != null && (voiceName in validVoiceNames || voiceName.startsWith("ar-") || voiceName == "system-Voice")) {
            TextToSpeech.SUCCESS
        } else {
            TextToSpeech.ERROR
        }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        if (isArabicLanguage(lang)) {
            val c = country?.uppercase(Locale.ROOT)?.trim()
            return when (c) {
                "SA", "SAU" -> "ar-SA-Voice"
                "EG", "EGY" -> "ar-EG-Voice"
                "AE", "ARE" -> "ar-AE-Voice"
                else -> "ar-Voice"
            }
        }
        return "system-Voice"
    }

    override fun onStop() {
        isStopped = true
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        isStopped = false
        val text = request?.charSequenceText?.toString() ?: return
        val settings = SettingsManager(applicationContext)
        if (tts == null || diacritizer == null || currentVocosPath != settings.getVocosPath()) initModels()
        synchronized(modelLock) {
            if (tts == null) {
                Log.e(TAG, "Models not loaded")
                callback?.error()
                return
            }
        }

        val modelPace = settings.pace.coerceIn(0.5f, 2.0f)
        val finalPitchMul = settings.pitchMul.coerceIn(0.5f, 2.0f)
        val finalPitchAdd = settings.pitchAdd.coerceIn(-10.0f, 10.0f)
        val sampleRate = settings.getSampleRate()

        val voiceName = request.voiceName
        val speakerId = when (voiceName) {
            "ar-EG-Voice" -> 1
            "ar-AE-Voice" -> 2
            else -> settings.speakerId
        }

        try {
            val userDict = UserDictionary(applicationContext)
            if (settings.isSentenceBySentenceEnabled) {
                synthesizeSentenceBySentence(
                    text, settings, userDict, modelPace,
                    finalPitchMul, finalPitchAdd, sampleRate, speakerId, callback
                )
            } else {
                synthesizeWhole(
                    text, settings, userDict, modelPace,
                    finalPitchMul, finalPitchAdd, sampleRate, speakerId, callback
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}")
            callback?.error()
        }
    }

    private fun processArabicText(text: String, settings: SettingsManager, userDict: UserDictionary): String {
        if (text.isBlank()) return text

        val singleChar = ArabicTextNormalizer.getSingleCharName(text)
        if (singleChar != null && singleChar.isNotEmpty()) return singleChar

        var processed = if (settings.isReadEmojisEnabled && DictionaryManager.isEmojiLoaded()) {
            DictionaryManager.replaceEmojis(text)
        } else {
            text
        }

        processed = userDict.applyReplacements(processed)
        processed = DictionaryManager.applyDefaultRules(processed)

        var norm = ArabicTextNormalizer.normalize(processed, false)
        if (ArabicTextNormalizer.isPreDiacritizedSpelling(norm)) return norm

        val hasArabic = norm.any { it in '\u0600'..'\u06FF' }
        if (settings.isVowelizerEnabled && hasArabic && norm.trim().length > 2 && diacritizer != null) {
            try {
                val diacritized = diacritizer?.diacritizeWhole(norm) ?: norm
                var merged = ArabicDiacritizer.mergeUserDiacritics(norm, diacritized)
                merged = ArabicTextNormalizer.restoreHamza(norm, merged)
                merged = ArabicTextNormalizer.fixNaaPronoun(norm, merged)
                merged = ArabicTextNormalizer.dedupeTanween(merged)
                merged = ArabicTextNormalizer.fixBareHamzaVowel(merged)
                merged = ArabicTextNormalizer.swapTanweenFathaAlif(merged)

                if (settings.isPauseSukoonEnabled) {
                    merged = ArabicTextNormalizer.sukoonizeEndOfSentence(merged)
                }
                return merged
            } catch (e: Exception) {
                Log.e(TAG, "Diacritization error: ${e.message}")
            }
        }

        if (settings.isPauseSukoonEnabled) {
            norm = ArabicTextNormalizer.sukoonizeEndOfSentence(norm)
        }
        norm = ArabicTextNormalizer.swapTanweenFathaAlif(norm)
        return norm
    }

    private fun synthesizeWhole(
        text: String, settings: SettingsManager, userDict: UserDictionary,
        modelPace: Float, pitchMul: Float, pitchAdd: Float,
        sampleRate: Int, speaker: Int, callback: SynthesisCallback?
    ) {
        val processedText = processArabicText(text, settings, userDict)
        val pcmData = tts?.ttsBytes(
            processedText,
            pace    = modelPace,
            speaker = speaker,
            padd    = pitchAdd,
            pmul    = pitchMul,
            denoise = settings.denoise
        )
        if (pcmData != null && pcmData.isNotEmpty()) {
            callback?.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            writeToCallback(pcmData, callback)
            callback?.done()
        } else {
            callback?.error()
        }
    }

    private fun synthesizeSentenceBySentence(
        text: String, settings: SettingsManager, userDict: UserDictionary,
        modelPace: Float, pitchMul: Float, pitchAdd: Float,
        sampleRate: Int, speaker: Int, callback: SynthesisCallback?
    ) {
        val sentences = text.split(SENTENCE_SPLIT_RE)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (sentences.isEmpty()) { callback?.error(); return }

        callback?.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
        for (sentence in sentences) {
            if (isStopped) break
            val processedText = processArabicText(sentence, settings, userDict)
            if (processedText.isBlank()) continue

            val pcm = tts?.ttsBytes(
                processedText,
                pace    = modelPace,
                speaker = speaker,
                padd    = pitchAdd,
                pmul    = pitchMul,
                denoise = settings.denoise
            )
            if (pcm != null && pcm.isNotEmpty()) {
                writeToCallback(pcm, callback)
            }
        }
        callback?.done()
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
        private const val TAG = "TtsService"
        private val SENTENCE_SPLIT_RE = Regex("""[.،؛؟!?,;:\n]+""")
    }
}

class CheckVoiceData : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val returnIntent = Intent()
        val availableVoices = ArrayList<String>()
        // ISO-2 codes
        availableVoices.add("ar")
        availableVoices.add("ar-SA")
        availableVoices.add("ar-EG")
        availableVoices.add("ar-AE")
        availableVoices.add("ar-DZ")
        availableVoices.add("ar-MA")
        availableVoices.add("ar-IQ")
        availableVoices.add("ar-YE")
        availableVoices.add("ar-SY")
        availableVoices.add("ar-JO")
        availableVoices.add("ar-LB")
        availableVoices.add("ar-KW")
        availableVoices.add("ar-QA")
        availableVoices.add("ar-BH")
        availableVoices.add("ar-OM")
        availableVoices.add("ar-TN")
        availableVoices.add("ar-LY")
        availableVoices.add("ar-SD")
        availableVoices.add("ar-PS")

        // ISO-3 codes (required by AOSP TextToSpeechSettings check)
        availableVoices.add("ara")
        availableVoices.add("ara-SAU")
        availableVoices.add("ara-EGY")
        availableVoices.add("ara-ARE")
        availableVoices.add("ara-DZA")
        availableVoices.add("ara-MAR")
        // Include current device language so Settings passes on any device language
        val defaultLocale = Locale.getDefault()
        try {
            val lang2 = defaultLocale.language
            val lang3 = defaultLocale.isO3Language
            val country2 = defaultLocale.country
            val country3 = try { defaultLocale.isO3Country } catch (_: Exception) { "" }

            if (lang2.isNotEmpty() && !availableVoices.contains(lang2)) availableVoices.add(lang2)
            if (lang3.isNotEmpty() && !availableVoices.contains(lang3)) availableVoices.add(lang3)

            if (country2.isNotEmpty()) {
                val c2 = "$lang2-$country2"
                if (!availableVoices.contains(c2)) availableVoices.add(c2)
            }
            if (country3.isNotEmpty()) {
                val c3 = "$lang3-$country3"
                if (!availableVoices.contains(c3)) availableVoices.add(c3)
            }
        } catch (e: Exception) {
            Log.w("CheckVoiceData", "Error adding default locale: ${e.message}")
        }

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
        val sample = "هذا هو محرك نبرة لتحويل النص إلى كلام."
        returnIntent.putExtra("sampleText", sample)
        returnIntent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample)
        setResult(TextToSpeech.LANG_AVAILABLE, returnIntent)
        finish()
    }
}
