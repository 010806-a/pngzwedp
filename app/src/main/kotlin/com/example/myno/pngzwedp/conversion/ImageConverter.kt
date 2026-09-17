package com.example.myno.pngzwedp.conversion

import android.content.Context
import com.example.myno.pngzwedp.ConversionResult

interface ImageConverter {

    suspend fun convert(
        context: Context,
        request: ConversionRequest,
        onProgress: suspend (
            current: Int,
            total: Int,
            fileName: String
        ) -> Unit
    ): ConversionResult
}