package com.example.myno.pngzwedp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PngToWebpConverter {

    suspend fun convert(
        context: Context,
        files: List<Uri>,
        outputDirectoryUri: Uri,
        quality: Int,
        preserveAlpha: Boolean,
        onProgress: suspend (
            current: Int,
            total: Int,
            fileName: String
        ) -> Unit
    ): ConversionResult =
        withContext(Dispatchers.IO) {

            val results =
                mutableListOf<ConversionItemResult>()

            val directory =
                DocumentFile.fromTreeUri(
                    context,
                    outputDirectoryUri
                )

            if (
                directory == null ||
                !directory.isDirectory
            ) {
                return@withContext ConversionResult(
                    files.map {
                        ConversionItemResult(
                            inputUri = it,
                            inputName =
                                getFileName(
                                    context,
                                    it
                                ) ?: "image.png",
                            outputName = null,
                            originalSize =
                                getFileSize(
                                    context,
                                    it
                                ),
                            outputSize = 0L,
                            success = false,
                            errorMessage =
                                "输出目录无效"
                        )
                    }
                )
            }

            files.forEachIndexed { index, uri ->

                val fileName =
                    getFileName(
                        context,
                        uri
                    ) ?: "image_$index.png"

                onProgress(
                    index,
                    files.size,
                    fileName
                )

                val result =
                    convertSingle(
                        context = context,
                        uri = uri,
                        directory = directory,
                        quality = quality,
                        preserveAlpha = preserveAlpha
                    )

                results.add(result)

                onProgress(
                    index + 1,
                    files.size,
                    fileName
                )
            }

            ConversionResult(results)
        }

    private fun convertSingle(
        context: Context,
        uri: Uri,
        directory: DocumentFile,
        quality: Int,
        preserveAlpha: Boolean
    ): ConversionItemResult {

        val inputName =
            getFileName(
                context,
                uri
            ) ?: "image.png"

        val originalSize =
            getFileSize(
                context,
                uri
            )

        var bitmap: Bitmap? = null
        var encodedBitmap: Bitmap? = null

        return try {

            bitmap =
                context.contentResolver
                    .openInputStream(uri)
                    ?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }

            if (bitmap == null) {
                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage = "无法读取 PNG 图片"
                )
            }

            /*
             * 保留透明通道：
             * 直接使用原始 Bitmap。
             *
             * 不保留透明通道：
             * 转换成 RGB_565，并用白色填充透明区域。
             */
            encodedBitmap =
                if (preserveAlpha) {
                    bitmap
                } else {
                    removeAlpha(bitmap)
                }

            val outputName =
                createOutputFileName(inputName)

            directory
                .findFile(outputName)
                ?.delete()

            val outputFile =
                directory.createFile(
                    "image/webp",
                    outputName
                )

            if (outputFile == null) {
                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage = "无法创建 WebP 文件"
                )
            }

            val outputStream =
                context.contentResolver
                    .openOutputStream(
                        outputFile.uri
                    )

            if (outputStream == null) {

                outputFile.delete()

                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage = "无法打开输出文件"
                )
            }

            val success =
                outputStream.use { stream ->

                    if (
                        android.os.Build.VERSION.SDK_INT >= 30
                    ) {
                        /*
                         * Android 11+：
                         *
                         * 保留透明通道 → WEBP_LOSSLESS
                         * 不保留透明通道 → WEBP_LOSSY
                         *
                         * 无损 WebP 可以完整保留 Alpha。
                         */
                        val compressFormat =
                            if (preserveAlpha) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            }

                        encodedBitmap.compress(
                            compressFormat,
                            quality.coerceIn(10, 100),
                            stream
                        )

                    } else {
                        /*
                         * Android 5.0 ~ 10：
                         *
                         * 旧版 Android 的 WEBP 编码器支持
                         * Bitmap 的 Alpha 通道。
                         */
                        encodedBitmap.compress(
                            Bitmap.CompressFormat.WEBP,
                            quality.coerceIn(10, 100),
                            stream
                        )
                    }
                }

            if (!success) {

                outputFile.delete()

                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage = "WebP 编码失败"
                )
            }

            val outputSize =
                getDocumentFileSize(
                    context,
                    outputFile
                )

            ConversionItemResult(
                inputUri = uri,
                inputName = inputName,
                outputName = outputName,
                originalSize = originalSize,
                outputSize = outputSize,
                success = true
            )

        } catch (e: Exception) {

            ConversionItemResult(
                inputUri = uri,
                inputName = inputName,
                outputName = null,
                originalSize = originalSize,
                outputSize = 0L,
                success = false,
                errorMessage =
                    e.message ?: "转换失败"
            )

        } finally {

            if (
                encodedBitmap != null &&
                encodedBitmap !== bitmap
            ) {
                encodedBitmap.recycle()
            }

            bitmap?.recycle()
        }
    }

    private fun removeAlpha(
        bitmap: Bitmap
    ): Bitmap {

        val result =
            Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                Bitmap.Config.RGB_565
            )

        val canvas =
            android.graphics.Canvas(result)

        canvas.drawColor(
            android.graphics.Color.WHITE
        )

        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            null
        )

        return result
    }

    private fun createOutputFileName(
        inputName: String
    ): String {

        val dot =
            inputName.lastIndexOf('.')

        val baseName =
            if (dot > 0) {
                inputName.substring(
                    0,
                    dot
                )
            } else {
                inputName
            }

        return "$baseName.webp"
    }

    private fun getFileName(
        context: Context,
        uri: Uri
    ): String? {

        var result: String? = null

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
                    result =
                        cursor.getString(index)
                }
            }
        }

        return result
    }

    private fun getFileSize(
        context: Context,
        uri: Uri
    ): Long {

        var size = 0L

        context.contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (
                    index >= 0 &&
                    !cursor.isNull(index)
                ) {
                    size =
                        cursor.getLong(index)
                }
            }
        }

        return size
    }

    private fun getDocumentFileSize(
        context: Context,
        file: DocumentFile
    ): Long {

        context.contentResolver
            .openAssetFileDescriptor(
                file.uri,
                "r"
            )
            ?.use {
                return it.length
            }

        return 0L
    }
}