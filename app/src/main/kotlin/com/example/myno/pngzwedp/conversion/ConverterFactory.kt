package com.example.myno.pngzwedp.conversion

object ConverterFactory {

    fun create(
        targetFormat: ImageFormat
    ): ImageConverter {

        return when (targetFormat) {

            ImageFormat.PNG,
            ImageFormat.JPEG,
            ImageFormat.WEBP ->
                BitmapImageConverter()

            ImageFormat.BMP,
            ImageFormat.GIF ->
                BitmapImageConverter()

            ImageFormat.SVG,
            ImageFormat.ANDROID_VECTOR_XML ->
                throw UnsupportedOperationException(
                    "${targetFormat.displayName} 请使用对应的矢量转换工具"
                )

            ImageFormat.AVIF ->
                throw UnsupportedOperationException(
                    "AVIF 输出编码器尚未接入"
                )

            ImageFormat.UNKNOWN ->
                throw IllegalArgumentException(
                    "目标格式不能是未知格式"
                )
        }
    }
}