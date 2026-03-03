package com.example.openblackbox

import android.net.Uri

data class RecordingItem(
    val name: String,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
    val contentUri: Uri
)
