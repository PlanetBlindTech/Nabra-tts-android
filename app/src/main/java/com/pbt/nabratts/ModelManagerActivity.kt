package com.pbt.nabratts

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.pbt.nabratts.databinding.ActivityModelManagerBinding
import java.io.File

class ModelManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelManagerBinding
    private lateinit var settingsManager: SettingsManager
    private val downloader = ModelDownloader()

    private var downloadDialog: AlertDialog? = null
    private var dialogProgressBar: ProgressBar? = null
    private var dialogPercentText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        setSupportActionBar(binding.toolbarModelManager)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarModelManager.setNavigationOnClickListener { finish() }

        setupViews()
        updateStatuses()
    }

    override fun onResume() {
        super.onResume()
        updateStatuses()
    }

    private fun setupViews() {
        binding.cardStandardQuality.setOnClickListener {
            if (downloader.isBatchRunning()) return@setOnClickListener
            if (settingsManager.isStandardQualityDownloaded()) {
                showDeleteDialog(
                    titleRes = R.string.confirm_delete_title,
                    messageRes = R.string.model_confirm_delete_std
                ) {
                    settingsManager.deleteStandardQuality()
                    updateStatuses()
                }
            } else {
                showDownloadConfirmDialog(
                    titleRes = R.string.nipponjo_standard_quality,
                    messageRes = R.string.model_confirm_download_std
                ) {
                    startStandardDownload()
                }
            }
        }

        binding.cardHighQuality.setOnClickListener {
            if (downloader.isBatchRunning()) return@setOnClickListener
            if (settingsManager.isHighQualityDownloaded()) {
                showDeleteDialog(
                    titleRes = R.string.confirm_delete_title,
                    messageRes = R.string.model_confirm_delete_high
                ) {
                    settingsManager.deleteHighQuality()
                    updateStatuses()
                }
            } else {
                showDownloadConfirmDialog(
                    titleRes = R.string.nipponjo_high_quality,
                    messageRes = R.string.model_confirm_download_high
                ) {
                    startHighDownload()
                }
            }
        }
    }

    private fun showDownloadConfirmDialog(titleRes: Int, messageRes: Int, onConfirm: () -> Unit) {
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.download) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(titleRes: Int, messageRes: Int, onConfirm: () -> Unit) {
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.delete) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startStandardDownload() {
        val items = mutableListOf<Pair<String, File>>()
        if (!settingsManager.isMixerDownloaded()) {
            items.add("mixer128" to File(settingsManager.getMixerPath()))
        }
        if (!settingsManager.isRawiDownloaded()) {
            items.add("rawi_ensemble" to File(settingsManager.getRawiPath()))
        }
        if (!settingsManager.isVocos22kDownloaded()) {
            items.add("vocos22k" to File(settingsManager.getVocos22kPath()))
        }

        startBatch(items) {
            settingsManager.vocosQuality = "22k"
        }
    }

    private fun startHighDownload() {
        val items = mutableListOf<Pair<String, File>>()
        if (!settingsManager.isMixerDownloaded()) {
            items.add("mixer128" to File(settingsManager.getMixerPath()))
        }
        if (!settingsManager.isRawiDownloaded()) {
            items.add("rawi_ensemble" to File(settingsManager.getRawiPath()))
        }
        if (!settingsManager.isVocos44kDownloaded()) {
            items.add("vocos44k" to File(settingsManager.getVocos44kPath()))
        }

        startBatch(items) {
            settingsManager.vocosQuality = "44k"
        }
    }

    private fun startBatch(items: List<Pair<String, File>>, onComplete: () -> Unit) {
        if (items.isEmpty()) {
            onComplete()
            updateStatuses()
            return
        }

        showDownloadProgressDialog {
            downloader.cancelBatch()
            updateStatuses()
            Toast.makeText(this@ModelManagerActivity, R.string.dict_cancel, Toast.LENGTH_SHORT).show()
        }

        downloader.downloadBatch(items, object : ModelDownloader.BatchCallback {
            override fun onProgress(progress: Int) {
                updateDownloadProgress(progress)
            }

            override fun onSuccess() {
                runOnUiThread {
                    dismissDownloadDialog()
                    onComplete()
                    updateStatuses()
                    Toast.makeText(this@ModelManagerActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    dismissDownloadDialog()
                    updateStatuses()
                    showErrorDialog(error)
                }
            }
        })
    }

    private fun showDownloadProgressDialog(onCancel: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        dialogProgressBar = dialogView.findViewById(R.id.pb_dialog)
        dialogPercentText = dialogView.findViewById(R.id.tv_dialog_percent)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_cancel)

        dialogProgressBar?.progress = 0
        dialogPercentText?.text = "0%"

        btnCancel.setOnClickListener {
            onCancel()
            dismissDownloadDialog()
        }

        downloadDialog = AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        downloadDialog?.show()
    }

    private fun updateDownloadProgress(progress: Int) {
        runOnUiThread {
            dialogProgressBar?.progress = progress
            dialogPercentText?.text = "$progress%"
        }
    }

    private fun dismissDownloadDialog() {
        runOnUiThread {
            try {
                downloadDialog?.dismiss()
            } catch (_: Exception) {}
            downloadDialog = null
            dialogProgressBar = null
            dialogPercentText = null
        }
    }

    private fun showErrorDialog(error: String) {
        AlertDialog.Builder(this, R.style.Theme_Nabra_Dialog)
            .setTitle(R.string.download_error)
            .setMessage(error)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun updateStatuses() {
        val isDownloading = downloader.isBatchRunning()
        binding.cardStandardQuality.isClickable = !isDownloading
        binding.cardHighQuality.isClickable = !isDownloading

        if (settingsManager.isStandardQualityDownloaded()) {
            binding.tvStandardStatus.text = getString(R.string.model_status_downloaded_action)
            binding.tvStandardStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            binding.tvStandardStatus.text = getString(R.string.model_status_missing_action)
            binding.tvStandardStatus.setTextColor(0xFFB0B0B0.toInt())
        }

        if (settingsManager.isHighQualityDownloaded()) {
            binding.tvHighStatus.text = getString(R.string.model_status_downloaded_action)
            binding.tvHighStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            binding.tvHighStatus.text = getString(R.string.model_status_missing_action)
            binding.tvHighStatus.setTextColor(0xFFB0B0B0.toInt())
        }
    }

    override fun onDestroy() {
        dismissDownloadDialog()
        if (downloader.isBatchRunning()) {
            downloader.cancelBatch()
        }
        super.onDestroy()
    }
}
