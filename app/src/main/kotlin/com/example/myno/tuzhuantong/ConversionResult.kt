package com.example.myno.tuzhuantong

import android.net.Uri

data class ConversionItemResult(
    val inputUri: Uri,
    val inputName: String,
    val outputName: String?,
    val originalSize: Long,
    val outputSize: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

data class ConversionResult(
    val items: List<ConversionItemResult>
) {

    val successCount: Int
        get() = items.count {
            it.success
        }

    val failedCount: Int
        get() = items.count {
            !it.success
        }

    val originalTotalSize: Long
        get() = items.sumOf {
            it.originalSize
        }

    val outputTotalSize: Long
        get() = items.sumOf {
            it.outputSize
        }

    val savedBytes: Long
        get() =
            originalTotalSize -
                outputTotalSize

    val savedPercent: Double
        get() {

            if (originalTotalSize <= 0L) {
                return 0.0
            }

            return savedBytes
                .toDouble()
                .div(
                    originalTotalSize.toDouble()
                )
                .times(100.0)
        }
}