package com.example.myno.tuzhuantong.conversion

import android.content.Context
import com.example.myno.tuzhuantong.ConversionResult

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