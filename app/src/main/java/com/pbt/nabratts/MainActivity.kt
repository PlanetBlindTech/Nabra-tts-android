package com.pbt.nabratts

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.pbt.nabratts.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private var diacritizer: ArabicDiacritizer? = null
    private var tts: MixerTtsModel? = null
    private var currentAudioTrack: AudioTrack? = null
    private val audioLock = Any()
    private enum class SynthState { IDLE, PROCESSING, PLAYING }
    @Volatile private var synthState = SynthState.IDLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        setSupportActionBar(binding.appBar.toolbar)

        thread {
            try {
                DictionaryManager.loadDefault(applicationContext)
                DictionaryManager.initEmojiAsync(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val voices = arrayOf(
            getString(R.string.voice_arabic_1),
            getString(R.string.voice_arabic_2),
            getString(R.string.voice_arabic_3),
            getString(R.string.voice_arabic_4)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, voices)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.appBar.content.spinnerVoice.adapter = adapter
        binding.appBar.content.spinnerVoice.setSelection(settingsManager.speakerId)

        binding.appBar.content.spinnerVoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (settingsManager.speakerId != position) {
                    settingsManager.speakerId = position
                    val text = binding.appBar.content.editTextInput.text.toString()
                    if (text.isNotEmpty() && settingsManager.areAllModelsDownloaded()) {
                        stopPlayback()
                        playSpeech(text, position)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        checkAndInitModels()

        binding.appBar.fabSynth.setOnClickListener {
            when (synthState) {
                SynthState.IDLE -> {
                    val text = binding.appBar.content.editTextInput.text.toString()
                    if (text.isNotEmpty()) {
                        if (!settingsManager.areAllModelsDownloaded()) {
                            checkAndInitModels()
                        } else {
                            val speaker = binding.appBar.content.spinnerVoice.selectedItemPosition
                            settingsManager.speakerId = speaker
                            playSpeech(text, speaker)
                        }
                    }
                }
                SynthState.PLAYING -> stopPlayback()
                SynthState.PROCESSING -> { }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settingsManager.forceReload()
        if (settingsManager.areAllModelsDownloaded() && (tts == null || diacritizer == null)) {
            initModels()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_model_manager -> {
                startActivity(Intent(this, ModelManagerActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_dictionary -> {
                startActivity(Intent(this, DictionaryActivity::class.java))
                true
            }
            R.id.action_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkAndInitModels() {
        if (!settingsManager.areAllModelsDownloaded()) {
            showMissingModelsDialog()
        } else {
            initModels()
        }
    }

    private fun initModels() {
        thread {
            try {
                diacritizer = ArabicDiacritizer(applicationContext).also {
                    it.load(settingsManager.getRawiPath())
                }
                tts = MixerTtsModel(applicationContext).also {
                    it.initSessions(settingsManager.getMixerPath(), settingsManager.getVocosPath())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showMissingModelsDialog() {
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setMessage(R.string.models_not_found_prompt)
            .setPositiveButton(R.string.ok) { _, _ ->
                startActivity(Intent(this, ModelManagerActivity::class.java))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setSynthState(state: SynthState) {
        synthState = state
        runOnUiThread {
            when (state) {
                SynthState.IDLE -> {
                    binding.appBar.fabProgress.visibility = View.GONE
                    binding.appBar.fabSynth.visibility    = View.VISIBLE
                    binding.appBar.fabSynth.isEnabled     = true
                    binding.appBar.fabSynth.setImageResource(R.drawable.ic_play_arrow)
                    binding.appBar.fabSynth.contentDescription = getString(R.string.speak)
                }
                SynthState.PROCESSING -> {
                    binding.appBar.fabSynth.isEnabled     = false
                    binding.appBar.fabSynth.contentDescription = getString(R.string.processing)
                    binding.appBar.fabProgress.visibility = View.VISIBLE
                }
                SynthState.PLAYING -> {
                    binding.appBar.fabProgress.visibility = View.GONE
                    binding.appBar.fabSynth.visibility    = View.VISIBLE
                    binding.appBar.fabSynth.isEnabled     = true
                    binding.appBar.fabSynth.setImageResource(R.drawable.ic_stop)
                    binding.appBar.fabSynth.contentDescription = getString(R.string.stop)
                }
            }
        }
    }

    private fun stopPlayback() {
        synchronized(audioLock) {
            try {
                currentAudioTrack?.stop()
            } catch (_: IllegalStateException) { }
            try {
                currentAudioTrack?.release()
            } catch (_: Exception) { }
            currentAudioTrack = null
        }
        setSynthState(SynthState.IDLE)
    }

    private fun playSpeech(text: String, speaker: Int) {
        setSynthState(SynthState.PROCESSING)
        thread {
            try {
                val userDict = UserDictionary(applicationContext)

                // 1. Read Emojis if enabled
                var processed = if (settingsManager.isReadEmojisEnabled && DictionaryManager.isEmojiLoaded()) {
                    DictionaryManager.replaceEmojis(text)
                } else {
                    text
                }

                // 2. User dictionary replacements
                processed = userDict.applyReplacements(processed)

                // 3. Default dictionary speech rules
                processed = DictionaryManager.applyDefaultRules(processed)

                // 4. Comprehensive Arabic Normalization
                var norm = ArabicTextNormalizer.normalize(processed, false)

                // 5. Diacritization & Post-processing
                val hasArabic = norm.any { it in '\u0600'..'\u06FF' }
                val finalSpeechText = if (settingsManager.isVowelizerEnabled && hasArabic && norm.trim().length > 2 && diacritizer != null) {
                    try {
                        val diacritized = diacritizer?.diacritizeWhole(norm) ?: norm
                        var merged = ArabicDiacritizer.mergeUserDiacritics(norm, diacritized)
                        merged = ArabicTextNormalizer.restoreHamza(norm, merged)
                        merged = ArabicTextNormalizer.fixNaaPronoun(norm, merged)
                        merged = ArabicTextNormalizer.dedupeTanween(merged)
                        merged = ArabicTextNormalizer.fixBareHamzaVowel(merged)
                        merged = ArabicTextNormalizer.swapTanweenFathaAlif(merged)
                        if (settingsManager.isPauseSukoonEnabled) {
                            merged = ArabicTextNormalizer.sukoonizeEndOfSentence(merged)
                        }
                        merged
                    } catch (e: Exception) {
                        e.printStackTrace()
                        norm
                    }
                } else {
                    if (settingsManager.isPauseSukoonEnabled) {
                        norm = ArabicTextNormalizer.sukoonizeEndOfSentence(norm)
                    }
                    ArabicTextNormalizer.swapTanweenFathaAlif(norm)
                }

                val modelPace = settingsManager.pace.coerceIn(0.5f, 2.0f)
                val pcm = tts?.ttsBytes(
                    finalSpeechText,
                    pace    = modelPace,
                    speaker = speaker,
                    pmul    = settingsManager.pitchMul.coerceIn(0.5f, 2.0f),
                    padd    = settingsManager.pitchAdd.coerceIn(-10.0f, 10.0f),
                    denoise = settingsManager.denoise
                )
                if (pcm != null && pcm.isNotEmpty() && synthState != SynthState.IDLE) {
                    playPcmData(pcm)
                } else {
                    setSynthState(SynthState.IDLE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                setSynthState(SynthState.IDLE)
            }
        }
    }

    private fun playPcmData(pcm: ByteArray) {
        val sampleRate = settingsManager.getSampleRate()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            setSynthState(SynthState.IDLE)
            return
        }

        synchronized(audioLock) {
            currentAudioTrack = audioTrack
        }

        audioTrack.write(pcm, 0, pcm.size)
        audioTrack.notificationMarkerPosition = pcm.size / 2
        audioTrack.setPlaybackPositionUpdateListener(object :
            AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(track: AudioTrack?) {
                synchronized(audioLock) {
                    if (currentAudioTrack === audioTrack) {
                        currentAudioTrack = null
                    }
                }
                try { audioTrack.release() } catch (_: Exception) { }
                setSynthState(SynthState.IDLE)
            }
            override fun onPeriodicNotification(track: AudioTrack?) {}
        })

        setSynthState(SynthState.PLAYING)
        audioTrack.play()

        val durationMs = (pcm.size.toLong() * 1000L) / (sampleRate * 2L) + 500L
        Thread.sleep(durationMs)
        synchronized(audioLock) {
            if (currentAudioTrack === audioTrack) {
                currentAudioTrack = null
                try { audioTrack.release() } catch (_: Exception) { }
                if (synthState == SynthState.PLAYING) {
                    setSynthState(SynthState.IDLE)
                }
            }
        }
    }

    override fun onDestroy() {
        stopPlayback()
        diacritizer?.release()
        tts?.release()
        diacritizer = null
        tts = null
        super.onDestroy()
    }
}
