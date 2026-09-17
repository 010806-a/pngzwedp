package com.example.myno.tuzhuantong.conversion

import android.graphics.Color
import android.net.Uri

data class ConversionRequest(
    val inputUris: List<Uri>,
    val targetFormat: ImageFormat,
    val quality: Int = 85,
    val preserveAlpha: Boolean = true,
    val outputDirectoryUri: Uri,
    val outputWidth: Int? = null,
    val outputHeight: Int? = null,
    val jpegBackgroundColor: Int = Color.WHITE
)