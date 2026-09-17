package com.example.myno.pngzwedp.tools

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.myno.pngzwedp.databinding.ActivitySvgToPngBinding
import com.example.myno.pngzwedp.storage.OutputDirectoryManager
import com.example.myno.pngzwedp.diagnostics.AppLogger
import com.example.myno.pngzwedp.vector.SvgToPngConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

class SvgToPngActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySvgToPngBinding

    private val selectedUris = mutableListOf<Uri>()

    private var isConverting = false

    private enum class OutputSize {
        ORIGINAL,
        SIZE_1024,
        CUSTOM
    }

    private var outputSize = OutputSize.ORIGINAL
    private var customWidth = 1024
    private var customHeight = 1024

    private val selectFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNullOrEmpty()) {
                return@registerForActivityResult
            }

            var addedCount = 0

            uris.forEach { uri ->

                if (!isSvgUri(uri)) {
                    return@forEach
                }

                if (selectedUris.contains(uri)) {
                    return@forEach
                }

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                selectedUris.add(uri)
                addedCount++
            }

            if (addedCount == 0) {
                Toast.makeText(
                    this,
                    "没有添加新的 SVG 文件",
                    Toast.LENGTH_SHORT
                ).show()
            }

            updateSelectedFiles()
        }

    private val selectOutputDirectoryLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            OutputDirectoryManager.takePersistablePermission(
                this,
                intent,
                uri
            )

            OutputDirectoryManager.saveOutputUri(
                this,
                uri
            )

            updateOutputDirectory()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySvgToPngBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        updateWorkspaceLayout()
        updateSelectedFiles()
        updateOutputDirectory()
        updateSizeUi()
        updateConvertButton()
        healthCheck()
    }

    private fun setupViews() {

        binding.backButton.setOnClickListener {
            finish()
        }

        /*
         * 整个选择区域都是唯一入口。
         * 不再存在额外的“选择文件”按钮。
         */
        binding.dropZone.setOnClickListener {
            if (isConverting) return@setOnClickListener

            AppLogger.i(
                "SvgToPngActivity",
                "打开 SVG 文件选择器"
            )

            selectFilesLauncher.launch(
                arrayOf(
                    "image/svg+xml",
                    "image/svg",
                    "text/xml",
                    "application/xml",
                    "*/*"
                )
            )
        }

        binding.clearFilesButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            selectedUris.clear()
            updateSelectedFiles()

            Toast.makeText(
                this,
                "已清空文件",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.changeDirectoryButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            selectOutputDirectoryLauncher.launch(null)
        }

        binding.openDirectoryButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            openOutputDirectory()
        }

        binding.originalSizeButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            outputSize = OutputSize.ORIGINAL
            updateSizeUi()
        }

        binding.size1024Button.setOnClickListener {
            if (isConverting) return@setOnClickListener

            outputSize = OutputSize.SIZE_1024
            updateSizeUi()
        }

        binding.customSizeButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            showCustomSizeDialog()
        }

        binding.convertButton.setOnClickListener {
            if (isConverting) return@setOnClickListener

            startConversion()
        }
    }

private fun updateWorkspaceLayout() {

    binding.workspace.post {

      val workspaceWidth = binding.workspace.width

        if (workspaceWidth >= dp(720)) {

            binding.workspace.orientation =
                LinearLayout.HORIZONTAL

            binding.fileCard.layoutParams =
                (binding.fileCard.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 1.05f
                    setMargins(
                        0,
                        0,
                        dp(8),
                        0
                    )
                }

            binding.settingsCard.layoutParams =
                (binding.settingsCard.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0.95f
                    setMargins(
                        dp(8),
                        0,
                        0,
                        0
                    )
                }

        } else {

            binding.workspace.orientation =
                LinearLayout.VERTICAL

            binding.fileCard.layoutParams =
                (binding.fileCard.layoutParams as LinearLayout.LayoutParams).apply {
                    width = LinearLayout.LayoutParams.MATCH_PARENT
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                    setMargins(
                        0,
                        0,
                        0,
                        dp(8)
                    )
                }

            binding.settingsCard.layoutParams =
                (binding.settingsCard.layoutParams as LinearLayout.LayoutParams).apply {
                    width = LinearLayout.LayoutParams.MATCH_PARENT
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    weight = 0f
                    setMargins(
                        0,
                        dp(8),
                        0,
                        0
                    )
                }
        }

        binding.fileCard.requestLayout()
        binding.settingsCard.requestLayout()
    }
}

    private fun updateSelectedFiles() {

        binding.fileListContainer.removeAllViews()

        val count = selectedUris.size

        binding.selectedFileCount.text =
            when (count) {
                0 -> "未选择文件"
                1 -> "已选择 1 个 SVG 文件"
                else -> "已选择 $count 个 SVG 文件"
            }

        binding.clearFilesButton.visibility =
            if (count > 0) View.VISIBLE else View.INVISIBLE

        selectedUris.forEachIndexed { index, uri ->

            binding.fileListContainer.addView(
                createFileRow(
                    index,
                    uri
                )
            )
        }

        updateConvertButton()
    }

    private fun createFileRow(
        index: Int,
        uri: Uri
    ): View {

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    dp(10),
                    dp(10),
                    dp(10),
                    dp(10)
                )

                setBackgroundResource(
                    com.example.myno.pngzwedp.R.drawable.bg_drop_zone
                )
            }

        val icon =
            TextView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(34),
                        dp(34)
                    )

                gravity =
                    Gravity.CENTER

                text = "SVG"

                textSize = 9f

                setTextColor(
                    android.graphics.Color.rgb(
                        80,
                        85,
                        92
                    )
                )

                setBackgroundResource(
                    com.example.myno.pngzwedp.R.drawable.bg_tool_icon_gray
                )
            }

        val info =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(
                            dp(10),
                            0,
                            dp(8),
                            0
                        )
                    }
            }

        val name =
            TextView(this).apply {

                text =
                    getFileName(uri)

                textSize =
                    13f

                setTextColor(
                    android.graphics.Color.rgb(
                        41,
                        43,
                        46
                    )
                )

                maxLines = 1
                ellipsize =
                    android.text.TextUtils.TruncateAt.MIDDLE
            }

        val metadata =
            TextView(this).apply {

                text =
                    "SVG 文件"

                textSize =
                    10f

                setTextColor(
                    android.graphics.Color.rgb(
                        138,
                        143,
                        152
                    )
                )

                setPadding(
                    0,
                    dp(3),
                    0,
                    0
                )
            }

        info.addView(name)
        info.addView(metadata)

        val remove =
            TextView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(34),
                        dp(34)
                    )

                gravity =
                    Gravity.CENTER

                text = "×"

                textSize = 20f

                setTextColor(
                    android.graphics.Color.rgb(
                        120,
                        125,
                        132
                    )
                )

                setBackgroundResource(
                    com.example.myno.pngzwedp.R.drawable.bg_segment_normal
                )

                setOnClickListener {

                    if (isConverting) return@setOnClickListener

                    if (index in selectedUris.indices) {
                        selectedUris.removeAt(index)
                        updateSelectedFiles()
                    }
                }
            }

        container.addView(icon)
        container.addView(info)
        container.addView(remove)

        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    0,
                    if (index == 0) 0 else dp(8),
                    0,
                    0
                )
            }

        container.layoutParams = params

        return container
    }

    private fun updateOutputDirectory() {

        val uri =
            OutputDirectoryManager.getOutputUri(this)

        if (uri == null) {

            binding.directoryPath.text =
                "尚未设置输出目录"

            binding.directoryHint.text =
                "点击“选择”设置 PNG 保存位置"

            binding.openDirectoryButton.visibility =
                View.GONE

            return
        }

        binding.directoryPath.text =
            getRealDirectoryPath(uri)

        binding.directoryHint.text =
            "PNG 文件将保存到此目录"

        binding.openDirectoryButton.visibility =
            View.VISIBLE
    }

    private fun updateSizeUi() {

        binding.originalSizeButton.apply {
            setBackgroundResource(
                if (outputSize == OutputSize.ORIGINAL)
                    com.example.myno.pngzwedp.R.drawable.bg_segment_selected
                else
                    com.example.myno.pngzwedp.R.drawable.bg_segment_normal
            )

            setTextColor(
                if (outputSize == OutputSize.ORIGINAL)
                    android.graphics.Color.WHITE
                else
                    android.graphics.Color.rgb(85, 90, 98)
            )
        }

        binding.size1024Button.apply {
            setBackgroundResource(
                if (outputSize == OutputSize.SIZE_1024)
                    com.example.myno.pngzwedp.R.drawable.bg_segment_selected
                else
                    com.example.myno.pngzwedp.R.drawable.bg_segment_normal
            )

            setTextColor(
                if (outputSize == OutputSize.SIZE_1024)
                    android.graphics.Color.WHITE
                else
                    android.graphics.Color.rgb(85, 90, 98)
            )
        }

        binding.customSizeButton.apply {
            setBackgroundResource(
                if (outputSize == OutputSize.CUSTOM)
                    com.example.myno.pngzwedp.R.drawable.bg_segment_selected
                else
                    com.example.myno.pngzwedp.R.drawable.bg_segment_normal
            )

            setTextColor(
                if (outputSize == OutputSize.CUSTOM)
                    android.graphics.Color.WHITE
                else
                    android.graphics.Color.rgb(85, 90, 98)
            )
        }

        binding.sizeHint.text =
            when (outputSize) {
                OutputSize.ORIGINAL ->
                    "保持 SVG 原始尺寸"

                OutputSize.SIZE_1024 ->
                    "最长边调整为 1024 像素"

                OutputSize.CUSTOM ->
                    "${customWidth} × ${customHeight} 像素"
            }
    }

    private fun showCustomSizeDialog() {

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(24),
                    dp(8),
                    dp(24),
                    0
                )
            }

        val widthInput =
            EditText(this).apply {

                hint = "宽度"

                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER

                setText(
                    customWidth.toString()
                )

                selectAll()
            }

        val heightInput =
            EditText(this).apply {

                hint = "高度"

                inputType =
                    android.text.InputType.TYPE_CLASS_NUMBER

                setText(
                    customHeight.toString()
                )
            }

        container.addView(widthInput)

        container.addView(
            heightInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        AlertDialog.Builder(this)
            .setTitle("自定义输出尺寸")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->

                val width =
                    widthInput.text
                        .toString()
                        .trim()
                        .toIntOrNull()

                val height =
                    heightInput.text
                        .toString()
                        .trim()
                        .toIntOrNull()

                if (
                    width == null ||
                    height == null ||
                    width <= 0 ||
                    height <= 0
                ) {

                    Toast.makeText(
                        this,
                        "请输入有效的宽度和高度",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                if (
                    width > 8192 ||
                    height > 8192
                ) {

                    Toast.makeText(
                        this,
                        "尺寸不能超过 8192 × 8192",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                customWidth = width
                customHeight = height
                outputSize = OutputSize.CUSTOM

                updateSizeUi()
            }
            .show()
    }

    private fun updateConvertButton() {

        binding.convertButton.alpha =
            if (
                !isConverting &&
                selectedUris.isNotEmpty()
            ) {
                1f
            } else {
                0.5f
            }

        binding.convertButton.isClickable =
            !isConverting &&
                selectedUris.isNotEmpty()
    }

    private fun startConversion() {

        if (selectedUris.isEmpty()) {

            Toast.makeText(
                this,
                "请先选择 SVG 文件",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val outputUri =
            OutputDirectoryManager.getOutputUri(this)

        if (outputUri == null) {

            Toast.makeText(
                this,
                "请先选择输出目录",
                Toast.LENGTH_SHORT
            ).show()

            selectOutputDirectoryLauncher.launch(null)

            return
        }

        if (!hasValidOutputDirectory(outputUri)) {

            Toast.makeText(
                this,
                "输出目录不可用，请重新选择",
                Toast.LENGTH_SHORT
            ).show()

            selectOutputDirectoryLauncher.launch(null)

            return
        }

        if (isConverting) {
            return
        }

        convertFiles(
            outputUri,
            selectedUris.toList()
        )
    }

    private fun convertFiles(
        outputUri: Uri,
        uris: List<Uri>
    ) {

        isConverting = true

        binding.progressContainer.visibility =
            View.VISIBLE

        binding.resultContainer.visibility =
            View.GONE

        binding.convertButton.text =
            "正在转换…"

        updateConvertButton()

        binding.progressBar.max =
            uris.size

        binding.progressBar.progress =
            0

        binding.progressCount.text =
            "0 / ${uris.size}"

        lifecycleScope.launch {

            var successCount = 0
            var failedCount = 0

            uris.forEachIndexed { index, uri ->

                val currentIndex =
                    index + 1

                binding.progressCount.text =
                    "$currentIndex / ${uris.size}"

                binding.progressCurrent.text =
                    getFileName(uri)

                binding.progressRemaining.text =
                    if (currentIndex < uris.size) {
                        "剩余 ${uris.size - currentIndex}"
                    } else {
                        "即将完成"
                    }

                val success =
                    withContext(Dispatchers.IO) {
                        convertSingleFile(
                            uri,
                            outputUri
                        )
                    }

                if (success) {
                    successCount++
                } else {
                    failedCount++
                }

                binding.progressBar.progress =
                    currentIndex
            }

            isConverting = false

            binding.progressContainer.visibility =
                View.GONE

            binding.convertButton.text =
                "开始转换"

            updateConvertButton()

            showResult(
                successCount,
                failedCount,
                uris.size,
                outputUri
            )
        }
    }

    private suspend fun convertSingleFile(
    uri: Uri,
    outputUri: Uri
): Boolean {

        return try {

            val outputDirectory =
                DocumentFile.fromTreeUri(
                    this,
                    outputUri
                )
                    ?: return false

            val inputStream =
                contentResolver.openInputStream(uri)
                    ?: return false

            inputStream.use { stream ->

                val bitmap =
                    SvgToPngConverter()
                        .convert(
                            this,
                            stream
                        )

                val finalBitmap =
                    prepareOutputBitmap(bitmap)

                val fileName =
                    createUniqueOutputName(
                        outputDirectory,
                        getFileName(uri)
                    )

                val outputFile =
                    outputDirectory.createFile(
                        "image/png",
                        fileName
                    )
                        ?: return false

                val outputStream =
                    contentResolver.openOutputStream(
                        outputFile.uri
                    )
                        ?: return false

                outputStream.use { out ->

                    finalBitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        out
                    )
                }

                if (finalBitmap !== bitmap) {
                    finalBitmap.recycle()
                }

                true
            }

        } catch (e: Exception) {

            AppLogger.e(
                "SvgToPngActivity",
                "SVG 转 PNG 失败：${getFileName(uri)}",
                e
            )

            false
        }
    }

    private fun prepareOutputBitmap(
        bitmap: Bitmap
    ): Bitmap {

        return when (outputSize) {

            OutputSize.ORIGINAL -> {
                bitmap
            }

            OutputSize.SIZE_1024 -> {
                scaleBitmap(
                    bitmap,
                    1024,
                    1024
                )
            }

            OutputSize.CUSTOM -> {
                scaleBitmap(
                    bitmap,
                    customWidth,
                    customHeight
                )
            }
        }
    }

    private fun scaleBitmap(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {

        if (
            bitmap.width == targetWidth &&
            bitmap.height == targetHeight
        ) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(
            bitmap,
            targetWidth,
            targetHeight,
            true
        )
    }

    private fun createUniqueOutputName(
        directory: DocumentFile,
        sourceName: String
    ): String {

        val baseName =
            sourceName
                .substringBeforeLast(
                    '.',
                    sourceName
                )
                .ifBlank {
                    "image"
                }

        var candidate =
            "$baseName.png"

        var index = 1

        while (
            directory.findFile(candidate) != null
        ) {

            candidate =
                "${baseName}_$index.png"

            index++
        }

        return candidate
    }

    private fun showResult(
        successCount: Int,
        failedCount: Int,
        totalCount: Int,
        outputUri: Uri
    ) {

        binding.resultContainer.visibility =
            View.VISIBLE

        binding.resultTitle.text =
            if (failedCount == 0) {
                "转换完成"
            } else {
                "转换完成，部分文件失败"
            }

        binding.resultText.text =
            if (failedCount == 0) {
                "所有 SVG 文件均已成功转换为 PNG"
            } else {
                "成功 $successCount 个，失败 $failedCount 个"
            }

        binding.resultSuccessCount.text =
            "成功 $successCount"

        binding.resultFailedCount.text =
            "失败 $failedCount"

        binding.resultTotalCount.text =
            "共 $totalCount"

        binding.resultDirectoryPath.text =
            getRealDirectoryPath(outputUri)
    }

    private fun openOutputDirectory() {

        val uri =
            OutputDirectoryManager.getOutputUri(this)
                ?: return

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW
                ).apply {

                    setDataAndType(
                        uri,
                        "vnd.android.document/directory"
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }

            startActivity(intent)

        } catch (e: ActivityNotFoundException) {

            try {

                startActivity(
                    Intent(
                        Intent.ACTION_OPEN_DOCUMENT_TREE
                    )
                )

            } catch (e2: Exception) {

                Toast.makeText(
                    this,
                    "无法打开文件目录",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "无法打开输出目录",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hasValidOutputDirectory(
        uri: Uri
    ): Boolean {

        return try {

            DocumentFile
                .fromTreeUri(
                    this,
                    uri
                )
                ?.canWrite() == true

        } catch (_: Exception) {
            false
        }
    }

    private fun getRealDirectoryPath(
        uri: Uri
    ): String {

        val documentFile =
            DocumentFile.fromTreeUri(
                this,
                uri
            )

        if (documentFile != null) {

            val name =
                documentFile.name

            if (!name.isNullOrBlank()) {
                return name
            }
        }

        return "已设置输出目录"
    }

    private fun isSvgUri(
        uri: Uri
    ): Boolean {

        val name =
            getFileName(uri)

        if (
            name.endsWith(
                ".svg",
                ignoreCase = true
            )
        ) {
            return true
        }

        val type =
            contentResolver
                .getType(uri)
                ?.lowercase()

        return type == "image/svg+xml" ||
            type == "image/svg" ||
            type == "text/xml" ||
            type == "application/xml"
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var result: String? = null

        if (uri.scheme == "content") {

            contentResolver.query(
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
        }

        if (!result.isNullOrBlank()) {
            return result!!
        }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.ifBlank {
                "未命名.svg"
            }
            ?: "未命名.svg"
    }

    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }

    private fun healthCheck() {

        AppLogger.i(
            "SvgToPngActivity",
            "SVG→PNG 页面初始化完成"
        )
    }
}