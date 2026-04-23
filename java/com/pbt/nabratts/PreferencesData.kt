package com.pbt.nabratts
import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import org.json.JSONArray
import org.json.JSONObject
class SettingsManager(context: Context) {
    private val settingsFile = File(context.filesDir, "tts_settings.properties")
    private val props = Properties()
    init { load() }
    private fun load() {
        if (settingsFile.exists()) {
            try { FileInputStream(settingsFile).use { props.load(it) } }
            catch (e: Exception) { e.printStackTrace() }
        }
    }
    private fun save() {
        try { FileOutputStream(settingsFile).use { props.store(it, "NabraTTS Settings") } }
        catch (e: Exception) { e.printStackTrace() }
    }
    var pace: Float
        get() = props.getProperty("pace", "1.0").toFloat()
        set(value) { props.setProperty("pace", value.toString()); save() }
    var pitchAdd: Float
        get() = props.getProperty("pitch_add", "0.0").toFloat()
        set(value) { props.setProperty("pitch_add", value.toString()); save() }
    var pitchMul: Float
        get() = props.getProperty("pitch_mul", "1.0").toFloat()
        set(value) { props.setProperty("pitch_mul", value.toString()); save() }
    var denoise: Float
        get() = props.getProperty("denoise", "0.005").toFloat()
        set(value) { props.setProperty("denoise", value.toString()); save() }
    var isVowelizerEnabled: Boolean
        get() = props.getProperty("vowelizer_enabled", "true").toBoolean()
        set(value) { props.setProperty("vowelizer_enabled", value.toString()); save() }
    var isSentenceBySentenceEnabled: Boolean
        get() = props.getProperty("word_by_word", "true").toBoolean()
        set(value) { props.setProperty("word_by_word", value.toString()); save() }
    var speakerId: Int
        get() = props.getProperty("speaker_id", "0").toInt()
        set(value) { props.setProperty("speaker_id", value.toString()); save() }
    var vocosQuality: String
        get() = props.getProperty("vocos_quality", "22k")
        set(value) { props.setProperty("vocos_quality", value); save() }
    fun getModelsDir(): File {
        val dir = File(settingsFile.parentFile, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    fun getMixerPath():     String = File(getModelsDir(), "mixer128.onnx").absolutePath
    fun getVocosPath():     String = File(getModelsDir(), "vocos${vocosQuality}.onnx").absolutePath
    fun getShakkelhaPath(): String = File(getModelsDir(), "shakkelha.onnx").absolutePath
    private fun isValidModel(path: String): Boolean {
        val f = File(path)
        return f.exists() && f.length() >= 500_000L
    }
    fun isMixerDownloaded():     Boolean = isValidModel(getMixerPath())
    fun isVocosDownloaded():     Boolean = isValidModel(getVocosPath())
    fun isShakkelhaDownloaded(): Boolean = isValidModel(getShakkelhaPath())
    fun areAllModelsDownloaded(): Boolean =
        isMixerDownloaded() && isVocosDownloaded() && isShakkelhaDownloaded()
    fun getSampleRate(): Int = if (vocosQuality == "44k") 44100 else 22050
    fun forceReload() { load() }
}
class UserDictionary(context: Context) {
    data class Entry(val original: String, val replacement: String)
    private val file = File(context.filesDir, "user_dictionary.json")
    private val entries = mutableListOf<Entry>()
    init { load() }
    private fun load() {
        entries.clear()
        if (!file.exists()) return
        try {
            val json = file.readText()
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entries.add(Entry(
                    original    = obj.getString("original"),
                    replacement = obj.getString("replacement")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    private fun save() {
        try {
            val arr = JSONArray()
            for (entry in entries) {
                val obj = JSONObject()
                obj.put("original", entry.original)
                obj.put("replacement", entry.replacement)
                arr.put(obj)
            }
            file.writeText(arr.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    fun getAll(): List<Entry> = entries.toList()
    fun add(original: String, replacement: String) {
        entries.add(Entry(original.trim(), replacement.trim()))
        save()
    }
    fun update(index: Int, original: String, replacement: String) {
        if (index in entries.indices) {
            entries[index] = Entry(original.trim(), replacement.trim())
            save()
        }
    }
    fun delete(index: Int) {
        if (index in entries.indices) {
            entries.removeAt(index)
            save()
        }
    }
    fun search(query: String): List<Pair<Int, Entry>> {
        if (query.isBlank()) return entries.mapIndexed { i, e -> i to e }
        val q = query.trim().lowercase()
        return entries.mapIndexed { i, e -> i to e }
            .filter { (_, e) ->
                e.original.lowercase().contains(q) ||
                e.replacement.lowercase().contains(q)
            }
    }
    fun applyReplacements(text: String): String {
        if (entries.isEmpty()) return text
        var result = text
        val sorted = entries.sortedByDescending { it.original.length }
        for (entry in sorted) {
            if (entry.original.isNotEmpty()) {
                result = result.replace(entry.original, entry.replacement)
            }
        }
        return result
    }
}
