package com.example.myno.tuzhuantong.conversion

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.util.Locale

object FormatDetector {

    fun detect(
        context: Context,
        uri: Uri
    ): DetectedFormat {

        detectByMagicNumber(
            context,
            uri
        )?.let {
            return it
        }

        detectByContent(
            context,
            uri
        )?.let {
            return it
        }

        val mimeType =
            context.contentResolver.getType(uri)

        detectByMimeType(
            mimeType
        )?.let {
            return it
        }

        detectByExtension(
            getFileName(
                context,
                uri
            )
        )?.let {
            return it
        }

        return DetectedFormat(
            format = ImageFormat.UNKNOWN,
            mimeType = mimeType,
            confidence = 0,
            source = DetectionSource.UNKNOWN
        )
    }

    private fun detectByMagicNumber(
        context: Context,
        uri: Uri
    ): DetectedFormat? {

        return try {

            context.contentResolver
                .openInputStream(uri)
                ?.use { rawInput ->

                    val input =
                        BufferedInputStream(
                            rawInput
                        )

                    input.mark(64)

                    val header =
                        ByteArray(32)

                    val count =
                        input.read(header)

                    input.reset()

                    if (count <= 0) {
                        return@use null
                    }

                    if (
                        startsWith(
                            header,
                            count,
                            byteArrayOf(
                                0x89.toByte(),
                                0x50.toByte(),
                                0x4E.toByte(),
                                0x47.toByte(),
                                0x0D.toByte(),
                                0x0A.toByte(),
                                0x1A.toByte(),
                                0x0A.toByte()
                            )
                        )
                    ) {
                        return@use detected(
                            ImageFormat.PNG,
                            "image/png",
                            100
                        )
                    }

                    if (
                        startsWith(
                            header,
                            count,
                            byteArrayOf(
                                0xFF.toByte(),
                                0xD8.toByte(),
                                0xFF.toByte()
                            )
                        )
                    ) {
                        return@use detected(
                            ImageFormat.JPEG,
                            "image/jpeg",
                            100
                        )
                    }

                    if (
                        startsWith(
                            header,
                            count,
                            byteArrayOf(
                                0x47.toByte(),
                                0x49.toByte(),
                                0x46.toByte(),
                                0x38.toByte()
                            )
                        )
                    ) {
                        return@use detected(
                            ImageFormat.GIF,
                            "image/gif",
                            100
                        )
                    }

                    if (
                        startsWith(
                            header,
                            count,
                            byteArrayOf(
                                0x42.toByte(),
                                0x4D.toByte()
                            )
                        )
                    ) {
                        return@use detected(
                            ImageFormat.BMP,
                            "image/bmp",
                            100
                        )
                    }

                    if (
                        count >= 12 &&
                        header[0] == 'R'.code.toByte() &&
                        header[1] == 'I'.code.toByte() &&
                        header[2] == 'F'.code.toByte() &&
                        header[3] == 'F'.code.toByte() &&
                        header[8] == 'W'.code.toByte() &&
                        header[9] == 'E'.code.toByte() &&
                        header[10] == 'B'.code.toByte() &&
                        header[11] == 'P'.code.toByte()
                    ) {
                        return@use detected(
                            ImageFormat.WEBP,
                            "image/webp",
                            100
                        )
                    }

                    if (isAvifHeader(header, count)) {
                        return@use detected(
                            ImageFormat.AVIF,
                            "image/avif",
                            95
                        )
                    }

                    null
                }

        } catch (_: Exception) {
            null
        }
    }

    private fun detectByContent(
        context: Context,
        uri: Uri
    ): DetectedFormat? {

        return try {

            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->

                    val bytes =
                        ByteArray(4096)

                    val count =
                        input.read(bytes)

                    if (count <= 0) {
                        return@use null
                    }

                    val text =
                        String(
                            bytes,
                            0,
                            count,
                            Charsets.UTF_8
                        )
                            .trimStart(
                                '\uFEFF',
                                ' ',
                                '\n',
                                '\r',
                                '\t'
                            )

                    val lower =
                        text.lowercase(
                            Locale.ROOT
                        )

                    when {

                        lower.startsWith(
                            "<svg"
                        ) ||
                            lower.startsWith(
                                "<?xml"
                            ) &&
                            lower.contains(
                                "<svg"
                            ) -> {

                            detected(
                                ImageFormat.SVG,
                                "image/svg+xml",
                                90
                            )
                        }

                        lower.startsWith(
                            "<vector"
                        ) ||
                            lower.startsWith(
                                "<?xml"
                            ) &&
                            lower.contains(
                                "<vector"
                            ) -> {

                            detected(
                                ImageFormat.ANDROID_VECTOR_XML,
                                "text/xml",
                                90
                            )
                        }

                        else -> null
                    }
                }

        } catch (_: Exception) {
            null
        }
    }

    private fun detectByMimeType(
        mimeType: String?
    ): DetectedFormat? {

        val mime =
            mimeType
                ?.lowercase(Locale.ROOT)
                ?: return null

        val format =
            when (mime) {

                "image/png" ->
                    ImageFormat.PNG

                "image/jpeg",
                "image/jpg" ->
                    ImageFormat.JPEG

                "image/webp" ->
                    ImageFormat.WEBP

                "image/bmp",
                "image/x-ms-bmp" ->
                    ImageFormat.BMP

                "image/gif" ->
                    ImageFormat.GIF

                "image/svg+xml" ->
                    ImageFormat.SVG

                "image/avif" ->
                    ImageFormat.AVIF

                "text/xml",
                "application/xml" ->
                    ImageFormat.ANDROID_VECTOR_XML

                else ->
                    return null
            }

        return detected(
            format = format,
            mimeType = mimeType,
            confidence = 70
        )
    }

    private fun detectByExtension(
        fileName: String?
    ): DetectedFormat? {

        val extension =
            fileName
                ?.substringAfterLast(
                    '.',
                    ""
                )
                ?.lowercase(
                    Locale.ROOT
                )
                ?: return null

        val format =
            when (extension) {

                "png" ->
                    ImageFormat.PNG

                "jpg",
                "jpeg" ->
                    ImageFormat.JPEG

                "webp" ->
                    ImageFormat.WEBP

                "bmp",
                "dib" ->
                    ImageFormat.BMP

                "gif" ->
                    ImageFormat.GIF

                "svg" ->
                    ImageFormat.SVG

                "xml" ->
                    ImageFormat.ANDROID_VECTOR_XML

                "avif" ->
                    ImageFormat.AVIF

                else ->
                    return null
            }

        return detected(
            format = format,
            mimeType = format.mimeType,
            confidence = 50,
            source = DetectionSource.EXTENSION
        )
    }

    private fun isAvifHeader(
        header: ByteArray,
        count: Int
    ): Boolean {

        if (count < 12) {
            return false
        }

        if (
            header[4] != 'f'.code.toByte() ||
            header[5] != 't'.code.toByte() ||
            header[6] != 'y'.code.toByte() ||
            header[7] != 'p'.code.toByte()
        ) {
            return false
        }

        val brand =
            String(
                header,
                8,
                minOf(12, count),
                Charsets.US_ASCII
            )

        return brand == "avif" ||
            brand == "avis" ||
            brand == "mif1"
    }

    private fun startsWith(
        data: ByteArray,
        count: Int,
        signature: ByteArray
    ): Boolean {

        if (count < signature.size) {
            return false
        }

        for (i in signature.indices) {

            if (data[i] != signature[i]) {
                return false
            }
        }

        return true
    }

    private fun detected(
        format: ImageFormat,
        mimeType: String?,
        confidence: Int,
        source: DetectionSource =
            DetectionSource.MAGIC_NUMBER
    ): DetectedFormat {

        return DetectedFormat(
            format = format,
            mimeType = mimeType,
            confidence = confidence,
            source = source
        )
    }

    private fun getFileName(
        context: Context,
        uri: Uri
    ): String? {

        var name: String? = null

        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {
                    name =
                        cursor.getString(index)
                }
            }
        }

        return name
    }
}