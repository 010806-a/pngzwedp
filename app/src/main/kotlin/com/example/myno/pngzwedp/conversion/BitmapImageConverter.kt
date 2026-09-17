package com.example.myno.pngzwedp.conversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.myno.pngzwedp.ConversionItemResult
import com.example.myno.pngzwedp.ConversionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BitmapImageConverter : ImageConverter {

    override suspend fun convert(
        context: Context,
        request: ConversionRequest,
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
                    request.outputDirectoryUri
                )

            if (
                directory == null ||
                !directory.isDirectory
            ) {
                return@withContext ConversionResult(
                    request.inputUris.map { uri ->

                        val name =
                            getFileName(
                                context,
                                uri
                            ) ?: "image"

                        ConversionItemResult(
                            inputUri = uri,
                            inputName = name,
                            outputName = null,
                            originalSize =
                                getFileSize(
                                    context,
                                    uri
                                ),
                            outputSize = 0L,
                            success = false,
                            errorMessage = "输出目录无效"
                        )
                    }
                )
            }

            request.inputUris.forEachIndexed { index, uri ->

                val fileName =
                    getFileName(
                        context,
                        uri
                    ) ?: "image_$index"

                onProgress(
                    index,
                    request.inputUris.size,
                    fileName
                )

                val result =
                    convertSingle(
                        context = context,
                        uri = uri,
                        directory = directory,
                        targetFormat = request.targetFormat,
                        quality = request.quality,
                        preserveAlpha = request.preserveAlpha,
                        jpegBackgroundColor =
                            request.jpegBackgroundColor,
                        outputWidth =
                            request.outputWidth,
                        outputHeight =
                            request.outputHeight
                    )

                results.add(result)

                onProgress(
                    index + 1,
                    request.inputUris.size,
                    fileName
                )
            }

            ConversionResult(results)
        }

    private fun convertSingle(
        context: Context,
        uri: Uri,
        directory: DocumentFile,
        targetFormat: ImageFormat,
        quality: Int,
        preserveAlpha: Boolean,
        jpegBackgroundColor: Int,
        outputWidth: Int?,
        outputHeight: Int?
    ): ConversionItemResult {

        val inputName =
            getFileName(
                context,
                uri
            ) ?: "image"

        val originalSize =
            getFileSize(
                context,
                uri
            )

        var bitmap: Bitmap? = null
        var preparedBitmap: Bitmap? = null

        return try {

            /*
             * 目标格式必须是普通 Bitmap 输出格式。
             */
            if (
                targetFormat != ImageFormat.PNG &&
                targetFormat != ImageFormat.JPEG &&
                targetFormat != ImageFormat.WEBP
            ) {
                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage =
                        "${targetFormat.displayName} 暂不支持 Bitmap 直接输出"
                )
            }

            /*
             * 读取文件真实格式。
             *
             * 不根据文件扩展名判断。
             */
            val detectedFormat =
                FormatDetector.detect(
                    context,
                    uri
                )

            /*
             * 普通 Bitmap 转换器目前支持：
             *
             * PNG
             * JPEG
             * WebP
             * BMP
             * GIF
             */
            val supportedInput =
                when (detectedFormat.format) {

                    ImageFormat.PNG,
                    ImageFormat.JPEG,
                    ImageFormat.WEBP,
                    ImageFormat.BMP,
                    ImageFormat.GIF -> true

                    else -> false
                }

            if (!supportedInput) {

                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage =
                        "${detectedFormat.format.displayName} 暂不支持普通图片转换"
                )
            }

            /*
             * BitmapFactory 根据文件内容进行解码。
             *
             * BMP / GIF 在 Android 支持的情况下
             * 会进入这里。
             *
             * GIF 当前转换为静态 Bitmap，
             * 不保留 GIF 动画。
             */
            bitmap =
                decodeBitmap(
                    context,
                    uri
                )

            if (bitmap == null) {

                return ConversionItemResult(
                    inputUri = uri,
                    inputName = inputName,
                    outputName = null,
                    originalSize = originalSize,
                    outputSize = 0L,
                    success = false,
                    errorMessage =
                        "无法读取 ${detectedFormat.format.displayName} 图片"
                )
            }

            preparedBitmap =
                prepareBitmap(
                    bitmap = bitmap,
                    targetFormat = targetFormat,
                    preserveAlpha = preserveAlpha,
                    jpegBackgroundColor =
                        jpegBackgroundColor,
                    outputWidth = outputWidth,
                    outputHeight = outputHeight
                )

            val outputName =
                createOutputFileName(
                    inputName,
                    targetFormat
                )

            directory
                .findFile(outputName)
                ?.delete()

            val outputFile =
                directory.createFile(
                    targetFormat.mimeType,
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
                    errorMessage =
                        "无法创建输出文件"
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
                    errorMessage =
                        "无法打开输出文件"
                )
            }

            val success =
                outputStream.use { stream ->

                    val format =
                        getCompressFormat(
                            targetFormat
                        )

                    preparedBitmap.compress(
                        format,
                        quality.coerceIn(
                            10,
                            100
                        ),
                        stream
                    )
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
                    errorMessage =
                        "${targetFormat.displayName} 编码失败"
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
                preparedBitmap != null &&
                preparedBitmap !== bitmap
            ) {
                preparedBitmap.recycle()
            }

            bitmap?.recycle()
        }
    }

    private fun decodeBitmap(
        context: Context,
        uri: Uri
    ): Bitmap? {

        return context.contentResolver
            .openInputStream(uri)
            ?.use { input ->

                BitmapFactory.decodeStream(
                    input
                )
            }
    }

    private fun prepareBitmap(
        bitmap: Bitmap,
        targetFormat: ImageFormat,
        preserveAlpha: Boolean,
        jpegBackgroundColor: Int,
        outputWidth: Int?,
        outputHeight: Int?
    ): Bitmap {

        var result = bitmap

        if (
            outputWidth != null &&
            outputHeight != null &&
            outputWidth > 0 &&
            outputHeight > 0 &&
            (
                bitmap.width != outputWidth ||
                bitmap.height != outputHeight
            )
        ) {
            result =
                Bitmap.createScaledBitmap(
                    bitmap,
                    outputWidth,
                    outputHeight,
                    true
                )
        }

        val needOpaqueBackground =
            targetFormat == ImageFormat.JPEG

        if (
            needOpaqueBackground ||
            !preserveAlpha
        ) {

            val opaque =
                Bitmap.createBitmap(
                    result.width,
                    result.height,
                    Bitmap.Config.ARGB_8888
                )

            val canvas =
                Canvas(opaque)

            canvas.drawColor(
                if (needOpaqueBackground) {
                    jpegBackgroundColor
                } else {
                    Color.WHITE
                }
            )

            canvas.drawBitmap(
                result,
                0f,
                0f,
                null
            )

            if (result !== bitmap) {
                result.recycle()
            }

            result = opaque
        }

        return result
    }

    private fun getCompressFormat(
        targetFormat: ImageFormat
    ): Bitmap.CompressFormat {

        return when (targetFormat) {

            ImageFormat.PNG ->
                Bitmap.CompressFormat.PNG

            ImageFormat.JPEG ->
                Bitmap.CompressFormat.JPEG

            ImageFormat.WEBP -> {

                if (
                    android.os.Build.VERSION.SDK_INT >= 30
                ) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }

            else ->
                throw IllegalArgumentException(
                    "不支持的 Bitmap 输出格式"
                )
        }
    }

    private fun createOutputFileName(
        inputName: String,
        targetFormat: ImageFormat
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

        return "$baseName.${targetFormat.extension}"
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
                    return cursor.getLong(index)
                }
            }
        }

        return 0L
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