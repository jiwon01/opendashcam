package com.example.openblackbox

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.example.openblackbox.databinding.ActivityRecordingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RecordingsActivity : LocalizedAppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var settings: AppSettings
    private lateinit var adapter: RecordingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.recordings_title)

        settings = AppSettings(this)
        adapter = RecordingAdapter(
            onPlayClick = { playRecording(it) },
            onShareClick = { shareRecording(it) },
            onDeleteClick = { confirmDeleteRecording(it) }
        )

        binding.recyclerRecordings.layoutManager = GridLayoutManager(this, calculateSpanCount())
        binding.recyclerRecordings.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        val items = StorageRepository.listRecordings(
            context = this,
            storageMode = settings.getStorageMode(),
            externalTreeUri = settings.getExternalTreeUri()
        )
        adapter.submitList(items)
        binding.textEmpty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun playRecording(item: RecordingItem) {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.contentUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, "recording_video", item.contentUri)
        }
        try {
            startActivity(playIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.toast_no_video_player), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecording(item: RecordingItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, item.contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_video_title)))
        } catch (exception: Exception) {
            Toast.makeText(
                this,
                exception.message ?: getString(R.string.toast_share_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDeleteRecording(item: RecordingItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_video_title)
            .setMessage(getString(R.string.delete_video_message, item.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteRecording(item)
            }
            .show()
    }

    private fun deleteRecording(item: RecordingItem) {
        val deleted = StorageRepository.deleteRecording(this, item)
        if (deleted) {
            Toast.makeText(this, getString(R.string.toast_delete_success), Toast.LENGTH_SHORT).show()
            loadRecordings()
        } else {
            Toast.makeText(this, getString(R.string.toast_delete_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateSpanCount(): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        return when {
            screenWidthDp >= 1000 -> 4
            screenWidthDp >= 700 -> 3
            else -> 2
        }
    }
}
