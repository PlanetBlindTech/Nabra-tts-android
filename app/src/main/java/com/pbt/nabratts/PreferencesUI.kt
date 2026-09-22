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
        binding.seekPace.contentDescription = "${getString(R.string.pace)}: ${String.format(java.util.Locale.US, "%.2fx", settingsManager.pace)}"

        val pitchAddProgress = (settingsManager.pitchAdd / 0.05f + 200f).toInt().coerceIn(0, 400)
        binding.seekPitchAdd.progress = pitchAddProgress
        binding.seekPitchAdd.contentDescription = "${getString(R.string.pitch)}: ${String.format(java.util.Locale.US, "%+.2f", settingsManager.pitchAdd)}"

        val denoiseProgress = (settingsManager.denoise * 10000f).toInt().coerceIn(0, 300)
        binding.seekDenoise.progress = denoiseProgress
        binding.seekDenoise.contentDescription = "${getString(R.string.denoise)}: ${String.format(java.util.Locale.US, "%.4f", settingsManager.denoise)}"

        binding.switchVowelizer.isChecked    = settingsManager.isVowelizerEnabled
        binding.switchWordByWord.isChecked   = settingsManager.isSentenceBySentenceEnabled
        binding.switchPauseSukoon.isChecked  = settingsManager.isPauseSukoonEnabled
        binding.switchReadEmojis.isChecked   = settingsManager.isReadEmojisEnabled
        updateQualitySpinner()
    }

    override fun onResume() {
        super.onResume()
        updateQualitySpinner()
    }

    private fun updateQualitySpinner() {
        val availableQualities = mutableListOf<Pair<String, String>>()
        if (settingsManager.isVocos22kDownloaded()) {
            availableQualities.add("22k" to getString(R.string.quality_22k))
        }
        if (settingsManager.isVocos44kDownloaded()) {
            availableQualities.add("44k" to getString(R.string.quality_44k))
        }

        if (availableQualities.isEmpty()) {
            val emptyList = listOf(getString(R.string.no_qualities_downloaded))
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emptyList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerQuality.adapter = adapter
            binding.spinnerQuality.isEnabled = false
            binding.spinnerQuality.onItemSelectedListener = null
        } else {
            binding.spinnerQuality.isEnabled = true
            val labels = availableQualities.map { it.second }
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerQuality.adapter = adapter

            var selectedIdx = availableQualities.indexOfFirst { it.first == settingsManager.vocosQuality }
            if (selectedIdx == -1) {
                selectedIdx = 0
                settingsManager.vocosQuality = availableQualities[0].first
            }
            binding.spinnerQuality.setSelection(selectedIdx)

            binding.spinnerQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, pos: Int, id: Long) {
                    if (pos in availableQualities.indices) {
                        settingsManager.vocosQuality = availableQualities[pos].first
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun setupListeners() {
        binding.seekPace.setOnSeekBarChangeListener(seekListener { progress ->
            val pace = 0.5f + progress / 100f
            settingsManager.pace = pace
            binding.seekPace.contentDescription = "${getString(R.string.pace)}: ${String.format(java.util.Locale.US, "%.2fx", pace)}"
        })
        binding.seekPitchAdd.setOnSeekBarChangeListener(seekListener { progress ->
            val pitchAdd = (progress - 200) * 0.05f
            settingsManager.pitchAdd = pitchAdd
            binding.seekPitchAdd.contentDescription = "${getString(R.string.pitch)}: ${String.format(java.util.Locale.US, "%+.2f", pitchAdd)}"
        })
        binding.seekDenoise.setOnSeekBarChangeListener(seekListener { progress ->
            val denoise = progress / 10000f
            settingsManager.denoise = denoise
            binding.seekDenoise.contentDescription = "${getString(R.string.denoise)}: ${String.format(java.util.Locale.US, "%.4f", denoise)}"
        })
        binding.switchVowelizer.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isVowelizerEnabled = isChecked
        }
        binding.switchWordByWord.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isSentenceBySentenceEnabled = isChecked
        }
        binding.switchPauseSukoon.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isPauseSukoonEnabled = isChecked
        }
        binding.switchReadEmojis.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isReadEmojisEnabled = isChecked
        }
    }
    private fun seekListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) = onChange(p)
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
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
        binding.toolbar.setNavigationOnClickListener { finish() }
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
