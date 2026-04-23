package com.pbt.nabratts
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.pbt.nabratts.databinding.ActivityAboutBinding
import com.pbt.nabratts.databinding.ActivitySettingsBinding
class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private val modelKeys = arrayOf("mixer128", "vocos", "shakkelha")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settingsManager = SettingsManager(this)
        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }
        loadSettings()
        setupListeners()
        binding.btnEnableEngine.setOnClickListener { openTtsSettings() }
    }
    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            catch (ex: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
    private fun loadSettings() {
        val paceProgress = ((settingsManager.pace - 0.5f) * 100f).toInt().coerceIn(0, 150)
        binding.seekPace.progress = paceProgress
        binding.tvPaceValue.text  = String.format("%.2fx", settingsManager.pace)
        val pitchAddProgress = (settingsManager.pitchAdd / 0.05f + 200f).toInt().coerceIn(0, 400)
        binding.seekPitchAdd.progress = pitchAddProgress
        binding.tvPitchAddValue.text  = String.format("%+.2f", settingsManager.pitchAdd)
        val pitchMulProgress = ((settingsManager.pitchMul - 0.5f) * 100f).toInt().coerceIn(0, 150)
        binding.seekPitchMul.progress = pitchMulProgress
        binding.tvPitchMulValue.text  = String.format("%.2fx", settingsManager.pitchMul)
        val denoiseProgress = (settingsManager.denoise * 10000f).toInt().coerceIn(0, 300)
        binding.seekDenoise.progress = denoiseProgress
        binding.tvDenoiseValue.text  = String.format("%.4f", settingsManager.denoise)
        binding.switchVowelizer.isChecked    = settingsManager.isVowelizerEnabled
        binding.switchWordByWord.isChecked   = settingsManager.isSentenceBySentenceEnabled
        val qualityLabels = arrayOf(
            getString(R.string.quality_22k),
            getString(R.string.quality_44k)
        )
        val qualityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, qualityLabels)
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerQuality.adapter = qualityAdapter
        binding.spinnerQuality.setSelection(if (settingsManager.vocosQuality == "44k") 1 else 0)
        val modelLabels = arrayOf(
            getString(R.string.mixer_model),
            getString(R.string.vocos_model),
            getString(R.string.shakkelha_model)
        )
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelLabels)
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerModel.adapter = modelAdapter
        updateSelectedModelStatus()
    }
    private fun setupListeners() {
        binding.seekPace.setOnSeekBarChangeListener(seekListener { progress ->
            val pace = 0.5f + progress / 100f
            binding.tvPaceValue.text = String.format("%.2fx", pace)
            settingsManager.pace = pace
        })
        binding.seekPitchAdd.setOnSeekBarChangeListener(seekListener { progress ->
            val pitchAdd = (progress - 200) * 0.05f
            binding.tvPitchAddValue.text = String.format("%+.2f", pitchAdd)
            settingsManager.pitchAdd = pitchAdd
        })
        binding.seekPitchMul.setOnSeekBarChangeListener(seekListener { progress ->
            val pitchMul = 0.5f + progress / 100f
            binding.tvPitchMulValue.text = String.format("%.2fx", pitchMul)
            settingsManager.pitchMul = pitchMul
        })
        binding.seekDenoise.setOnSeekBarChangeListener(seekListener { progress ->
            val denoise = progress / 10000f
            binding.tvDenoiseValue.text = String.format("%.4f", denoise)
            settingsManager.denoise = denoise
        })
        binding.switchVowelizer.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isVowelizerEnabled = isChecked
        }
        binding.switchWordByWord.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSentenceBySentenceEnabled = isChecked
        }
        binding.spinnerQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                settingsManager.vocosQuality = if (pos == 1) "44k" else "22k"
                updateSelectedModelStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                updateSelectedModelStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.btnModelAction.setOnClickListener {
            val modelIndex = binding.spinnerModel.selectedItemPosition
            val isDownloaded = isModelDownloaded(modelIndex)
            if (isDownloaded) {
                deleteModel(modelIndex)
            } else {
                downloadModel(modelIndex)
            }
        }
    }
    private fun seekListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }
    private fun isModelDownloaded(index: Int): Boolean = when (index) {
        0 -> settingsManager.isMixerDownloaded()
        1 -> settingsManager.isVocosDownloaded()
        2 -> settingsManager.isShakkelhaDownloaded()
        else -> false
    }
    private fun getModelName(index: Int): String = when (index) {
        0 -> getString(R.string.mixer_model)
        1 -> getString(R.string.vocos_model)
        2 -> getString(R.string.shakkelha_model)
        else -> ""
    }
    private fun updateSelectedModelStatus() {
        val modelIndex = binding.spinnerModel.selectedItemPosition
        val downloaded = isModelDownloaded(modelIndex)
        val name       = getModelName(modelIndex)
        binding.tvModelStatus.text = "$name: " +
            if (downloaded) getString(R.string.status_downloaded)
            else getString(R.string.status_missing)
        if (downloaded) {
            binding.btnModelAction.text = getString(R.string.delete)
            binding.btnModelAction.setTextColor(0xFFF44336.toInt())
        } else {
            binding.btnModelAction.text = getString(R.string.download)
            binding.btnModelAction.setTextColor(0xFFFFFFFF.toInt())
        }
    }
    private fun deleteModel(modelIndex: Int) {
        val file = when (modelIndex) {
            0 -> java.io.File(settingsManager.getMixerPath())
            1 -> java.io.File(settingsManager.getVocosPath())
            2 -> java.io.File(settingsManager.getShakkelhaPath())
            else -> return
        }
        if (file.exists()) file.delete()
        updateSelectedModelStatus()
    }
    private fun downloadModel(modelIndex: Int) {
        binding.pbDownload.visibility         = View.VISIBLE
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.btnModelAction.isEnabled      = false
        val downloader = ModelDownloader()
        val key = when (modelIndex) {
            0 -> "mixer128"
            1 -> "vocos" + settingsManager.vocosQuality
            2 -> "shakkelha"
            else -> return
        }
        val file = when (modelIndex) {
            0 -> java.io.File(settingsManager.getMixerPath())
            1 -> java.io.File(settingsManager.getVocosPath())
            2 -> java.io.File(settingsManager.getShakkelhaPath())
            else -> return
        }
        val callback = object : ModelDownloader.DownloadCallback {
            override fun onProgress(modelName: String, progress: Int) {
                runOnUiThread {
                    binding.pbDownload.progress     = progress
                    binding.tvDownloadProgress.text = "$modelName: $progress%"
                }
            }
            override fun onSuccess(modelName: String) {
                runOnUiThread {
                    binding.pbDownload.visibility         = View.GONE
                    binding.tvDownloadProgress.text       = getString(R.string.download_complete)
                    binding.btnModelAction.isEnabled      = true
                    updateSelectedModelStatus()
                }
            }
            override fun onError(modelName: String, error: String) {
                runOnUiThread {
                    binding.pbDownload.visibility         = View.GONE
                    binding.tvDownloadProgress.text       = "${getString(R.string.download_error)} ($modelName)"
                    binding.btnModelAction.isEnabled      = true
                }
            }
        }
        downloader.downloadModel(key, file, callback)
    }
}
class DictionaryActivity : AppCompatActivity() {
    private lateinit var dictionary: UserDictionary
    private lateinit var adapter: EntryAdapter
    private lateinit var rvEntries: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: TextInputEditText
    private var displayedEntries = listOf<Pair<Int, UserDictionary.Entry>>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)
        dictionary = UserDictionary(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.dictionary_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        rvEntries = findViewById(R.id.rv_entries)
        tvEmpty   = findViewById(R.id.tv_empty)
        etSearch  = findViewById(R.id.et_search)
        adapter = EntryAdapter(
            onItemLongClick = { position -> showEntryOptionsDialog(position) }
        )
        rvEntries.layoutManager = LinearLayoutManager(this)
        rvEntries.adapter = adapter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshList()
            }
        })
        findViewById<MaterialButton>(R.id.btn_add_entry).setOnClickListener {
            showAddDialog()
        }
        refreshList()
    }
    private fun refreshList() {
        val query = etSearch.text?.toString() ?: ""
        displayedEntries = dictionary.search(query)
        adapter.submitList(displayedEntries)
        if (displayedEntries.isEmpty()) {
            rvEntries.visibility = View.GONE
            tvEmpty.visibility   = View.VISIBLE
        } else {
            rvEntries.visibility = View.VISIBLE
            tvEmpty.visibility   = View.GONE
        }
    }
    private fun showAddDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_dictionary_entry, null)
        val etOriginal    = dialogView.findViewById<TextInputEditText>(R.id.et_original)
        val etReplacement = dialogView.findViewById<TextInputEditText>(R.id.et_replacement)
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(R.string.dict_add)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val orig = etOriginal.text?.toString()?.trim() ?: ""
                val repl = etReplacement.text?.toString()?.trim() ?: ""
                if (orig.isNotEmpty() && repl.isNotEmpty()) {
                    dictionary.add(orig, repl)
                    refreshList()
                }
            }
            .setNegativeButton(R.string.dict_cancel, null)
            .show()
    }
    private fun showEditDialog(realIndex: Int, entry: UserDictionary.Entry) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_dictionary_entry, null)
        val etOriginal    = dialogView.findViewById<TextInputEditText>(R.id.et_original)
        val etReplacement = dialogView.findViewById<TextInputEditText>(R.id.et_replacement)
        etOriginal.setText(entry.original)
        etReplacement.setText(entry.replacement)
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(R.string.dict_edit)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val orig = etOriginal.text?.toString()?.trim() ?: ""
                val repl = etReplacement.text?.toString()?.trim() ?: ""
                if (orig.isNotEmpty() && repl.isNotEmpty()) {
                    dictionary.update(realIndex, orig, repl)
                    refreshList()
                }
            }
            .setNegativeButton(R.string.dict_cancel, null)
            .show()
    }
    private fun showEntryOptionsDialog(displayPosition: Int) {
        if (displayPosition !in displayedEntries.indices) return
        val (realIndex, entry) = displayedEntries[displayPosition]
        val options = arrayOf(
            getString(R.string.dict_edit),
            getString(R.string.dict_delete)
        )
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(entry.original)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(realIndex, entry)
                    1 -> {
                        dictionary.delete(realIndex)
                        refreshList()
                    }
                }
            }
            .setNegativeButton(R.string.dict_cancel, null)
            .show()
    }
    private inner class EntryAdapter(
        private val onItemLongClick: (Int) -> Unit
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {
        private var items = listOf<Pair<Int, UserDictionary.Entry>>()
        fun submitList(newItems: List<Pair<Int, UserDictionary.Entry>>) {
            items = newItems
            notifyDataSetChanged()
        }
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvOriginal:    TextView = view.findViewById(R.id.tv_original)
            val tvReplacement: TextView = view.findViewById(R.id.tv_replacement)
            init {
                view.setOnLongClickListener {
                    onItemLongClick(adapterPosition)
                    true
                }
            }
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dictionary_entry, parent, false)
            return VH(view)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val (_, entry) = items[position]
            holder.tvOriginal.text    = entry.original
            holder.tvReplacement.text = "→ ${entry.replacement}"
        }
        override fun getItemCount(): Int = items.size
    }
}
class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.btnYoutube.setOnClickListener {
            openUrl("https://youtube.com/@planetblindtech?si=PLR4cp13TihMeBNH")
        }
        binding.btnTelegram.setOnClickListener {
            openUrl("https://t.me/mohammad_loay222")
        }
        binding.btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:planetblindtec@gmail.com")
            }
            startActivity(intent)
        }
    }
    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
