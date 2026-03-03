package com.example.openblackbox

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageRepository {

    private const val INTERNAL_FOLDER_NAME = "OpenBlackBox"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val sizeFormat = DecimalFormat("#.##")

    fun getInternalRecordingDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(base, INTERNAL_FOLDER_NAME).also { it.mkdirs() }
    }

    fun createInternalOutputFile(context: Context, fileName: String): File {
        return File(getInternalRecordingDir(context), fileName)
    }

    fun listRecordings(
        context: Context,
        storageMode: StorageMode,
        externalTreeUri: Uri?
    ): List<RecordingItem> {
        return when (storageMode) {
            StorageMode.INTERNAL -> listInternalRecordings(context)
            StorageMode.EXTERNAL_TREE -> {
                if (externalTreeUri == null) {
                    emptyList()
                } else {
                    listExternalRecordings(context, externalTreeUri)
                }
            }
        }.sortedByDescending { it.modifiedAtMillis }
    }

    fun deleteRecording(context: Context, item: RecordingItem): Boolean {
        return runCatching {
            val deletedRows = context.contentResolver.delete(item.contentUri, null, null)
            if (deletedRows > 0) {
                true
            } else {
                DocumentFile.fromSingleUri(context, item.contentUri)?.delete() == true
            }
        }.getOrDefault(false)
    }

    fun deleteOldestRecording(
        context: Context,
        storageMode: StorageMode,
        externalTreeUri: Uri?
    ): String? {
        return when (storageMode) {
            StorageMode.INTERNAL -> deleteOldestInternalRecording(context)
            StorageMode.EXTERNAL_TREE -> {
                if (externalTreeUri == null) {
                    null
                } else {
                    deleteOldestExternalRecording(context, externalTreeUri)
                }
            }
        }
    }

    fun formatDate(millis: Long): String = dateFormat.format(Date(millis))

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${sizeFormat.format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "${sizeFormat.format(mb)} MB"
        val gb = mb / 1024.0
        return "${sizeFormat.format(gb)} GB"
    }

    private fun listInternalRecordings(context: Context): List<RecordingItem> {
        val root = getInternalRecordingDir(context)
        val files = root.listFiles()?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }
            ?.toList()
            ?: emptyList()

        return files.mapNotNull { file ->
            runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                RecordingItem(
                    name = file.name,
                    modifiedAtMillis = file.lastModified(),
                    sizeBytes = file.length(),
                    contentUri = uri
                )
            }.getOrNull()
        }
    }

    private fun listExternalRecordings(context: Context, treeUri: Uri): List<RecordingItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return root.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter { doc ->
                val name = doc.name?.lowercase(Locale.ROOT) ?: return@filter false
                name.endsWith(".mp4") || doc.type == "video/mp4"
            }
            .map { doc ->
                RecordingItem(
                    name = doc.name ?: "recording.mp4",
                    modifiedAtMillis = doc.lastModified(),
                    sizeBytes = doc.length(),
                    contentUri = doc.uri
                )
            }
            .toList()
    }

    private fun deleteOldestInternalRecording(context: Context): String? {
        val oldest = getInternalRecordingDir(context)
            .listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }
            ?.minByOrNull { it.lastModified() }
            ?: return null
        return if (oldest.delete()) oldest.name else null
    }

    private fun deleteOldestExternalRecording(context: Context, treeUri: Uri): String? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val oldest = root.listFiles()
            .asSequence()
            .filter { it.isFile }
            .filter { doc ->
                val name = doc.name?.lowercase(Locale.ROOT) ?: return@filter false
                name.endsWith(".mp4") || doc.type == "video/mp4"
            }
            .minByOrNull { it.lastModified() }
            ?: return null
        val name = oldest.name ?: "recording.mp4"
        return if (oldest.delete()) name else null
    }
}
