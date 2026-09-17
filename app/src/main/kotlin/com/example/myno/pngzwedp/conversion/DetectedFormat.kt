package com.example.myno.pngzwedp.conversion

data class DetectedFormat(
    val format: ImageFormat,
    val mimeType: String? = null,
    val confidence: Int = 0,
    val source: DetectionSource = DetectionSource.UNKNOWN
)

enum class DetectionSource {
    MAGIC_NUMBER,
    MIME_TYPE,
    EXTENSION,
    CONTENT,
    UNKNOWN
}