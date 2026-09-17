package com.example.myno.pngzwedp.conversion

enum class ImageFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    PNG(
        displayName = "PNG",
        extension = "png",
        mimeType = "image/png"
    ),

    JPEG(
        displayName = "JPEG",
        extension = "jpg",
        mimeType = "image/jpeg"
    ),

    WEBP(
        displayName = "WebP",
        extension = "webp",
        mimeType = "image/webp"
    ),

    BMP(
        displayName = "BMP",
        extension = "bmp",
        mimeType = "image/bmp"
    ),

    GIF(
        displayName = "GIF",
        extension = "gif",
        mimeType = "image/gif"
    ),

    SVG(
        displayName = "SVG",
        extension = "svg",
        mimeType = "image/svg+xml"
    ),

    ANDROID_VECTOR_XML(
        displayName = "Android Vector XML",
        extension = "xml",
        mimeType = "text/xml"
    ),

    AVIF(
        displayName = "AVIF",
        extension = "avif",
        mimeType = "image/avif"
    ),

    UNKNOWN(
        displayName = "未知",
        extension = "",
        mimeType = "application/octet-stream"
    )
}