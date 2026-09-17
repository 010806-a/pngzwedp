package com.example.myno.tuzhuantong.tools
import com.example.myno.tuzhuantong.vector.SvgToPngConverter
import com.example.myno.tuzhuantong.vector.SvgToVectorXmlConverter
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle

import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myno.tuzhuantong.R
import com.example.myno.tuzhuantong.databinding.ActivitySvgConverterBinding
import com.example.myno.tuzhuantong.diagnostics.AppLogger
import com.example.myno.tuzhuantong.storage.OutputDirectoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.roundToInt

class SvgConverterActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySvgConverterBinding

    private val svgToPngConverter = SvgToPngConverter()
    private val svgToVectorXmlConverter = SvgToVectorXmlConverter()

    private enum class OutputFormat {
        PNG,
        VECTOR_XML
    }

    private enum class PngSizeMode {
        ORIGINAL,
        CUSTOM
    }

    private data class SelectedSvg(
        val uri: Uri,
        val displayName: String
    )

    private data class ConversionResult(
        val sourceName: String,
        val outputName: String?,
        val success: Boolean,
        val reason: String? = null
    )

    private val selectedFiles = mutableListOf<SelectedSvg>()

    private var outputFormat = OutputFormat.PNG
    private var pngSizeMode = PngSizeMode.ORIGINAL
    private var keepAspectRatio = true

    private var customWidth = 512
    private var customHeight = 512

    private var converting = false

    private val filePicker =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNullOrEmpty()) {
                return@registerForActivityResult
            }

            addSelectedFiles(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySvgConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        updateOutputDirectory()
        updateUi()
    }
    private val pickOutputDirectory =
    registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->

        if (uri == null) {
            return@registerForActivityResult
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            AppLogger.e(
                "SvgConverterActivity",
                "保存输出目录权限失败",
                e
            )
        }

        OutputDirectoryManager.saveOutputUri(
            this,
            uri
        )

        updateOutputDirectory()
    }

    private fun initViews() {
    
    binding.startConversionButton.setOnClickListener {

    if (!converting) {
        startConversion()
    }
}

        binding.backButton.setOnClickListener {
            if (!converting) {
                finish()
            }
        }

        binding.selectZone.setOnClickListener {
            if (!converting) {
                openSvgPicker()
            }
        }

        binding.addMoreButton.setOnClickListener {
            if (!converting) {
                openSvgPicker()
            }
        }

        binding.clearFilesButton.setOnClickListener {
            if (!converting) {
                selectedFiles.clear()
                updateUi()
            }
        }

        binding.formatPngButton.setOnClickListener {
            if (!converting) {
                outputFormat = OutputFormat.PNG
                updateUi()
            }
        }

        binding.formatVectorButton.setOnClickListener {
            if (!converting) {
                outputFormat = OutputFormat.VECTOR_XML
                updateUi()
            }
        }

        binding.originalSizeButton.setOnClickListener {
            if (!converting) {
                pngSizeMode = PngSizeMode.ORIGINAL
                updateUi()
            }
        }

        binding.customSizeButton.setOnClickListener {
            if (!converting) {
                pngSizeMode = PngSizeMode.CUSTOM
                updateUi()
            }
        }

 binding.keepAspectCheckBox.isChecked = keepAspectRatio

binding.keepAspectCheckBox.setOnCheckedChangeListener { _, checked ->
    if (!converting) {
        keepAspectRatio = checked
        updatePngSettingsUi()
    }
}

        binding.viewDirectoryButton.setOnClickListener {
            if (!converting) {
                openOutputDirectory()
            }
        }
    }

    private fun openSvgPicker() {

        filePicker.launch(
            arrayOf(
                "image/svg+xml",
                "image/*",
                "text/xml",
                "application/xml"
            )
        )
    }

    private fun addSelectedFiles(uris: List<Uri>) {

        uris.forEach { uri ->

            val name = getDisplayName(uri)

            if (
                name.isNotBlank() &&
                name.lowercase().endsWith(".svg") &&
                selectedFiles.none { it.uri == uri }
            ) {
                selectedFiles.add(
                    SelectedSvg(
                        uri = uri,
                        displayName = name
                    )
                )
            }
        }

        updateUi()
    }

    private fun getDisplayName(uri: Uri): String {

        var name: String? = null

        try {

            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index =
                        cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )

                    if (index >= 0) {
                        name = cursor.getString(index)
                    }
                }
            }

        } catch (e: Exception) {

            AppLogger.e(
                "SvgConverterActivity",
                "读取 SVG 文件名失败",
                e
            )
        }

        return name ?: uri.lastPathSegment ?: "unknown.svg"
    }

    private fun updateUi() {

        binding.filePanel.visibility =
            if (selectedFiles.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        binding.fileCount.text =
            "已选择 ${selectedFiles.size} 个文件"

        updateFileList()
        updateFormatUi()
        updatePngSettingsUi()
        updateConvertButton()
    }

    private fun updateFileList() {

        binding.fileListContainer.removeAllViews()

        selectedFiles.forEachIndexed { index, file ->

            val row =
                LinearLayout(this).apply {

                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    setPadding(
                        dp(13),
                        dp(9),
                        dp(8),
                        dp(9)
                    )

                    minimumHeight = dp(60)
                }

            val icon =
                TextView(this).apply {

                    text = "SVG"

                    gravity = android.view.Gravity.CENTER

                    textSize = 9f

                    setTextColor(
                        ContextCompat.getColor(
                            this@SvgConverterActivity,
                            android.R.color.black
                        )
                    )

                    setBackgroundColor(
                        0xFFF1F1F1.toInt()
                    )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(38),
                            dp(38)
                        )
                }

            val textContainer =
                LinearLayout(this).apply {

                    orientation = LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            marginStart = dp(10)
                        }
                }

            val fileName =
                TextView(this).apply {

                    text = file.displayName

                    textSize = 13f

                    setTextColor(0xFF222222.toInt())

                    maxLines = 1

                    ellipsize =
                        android.text.TextUtils.TruncateAt.MIDDLE
                }

            val outputType =
                TextView(this).apply {

                    text =
                        when (outputFormat) {
                            OutputFormat.PNG ->
                                "将输出 PNG"

                            OutputFormat.VECTOR_XML ->
                                "将输出 Vector XML"
                        }

                    textSize = 11f

                    setTextColor(0xFF999999.toInt())

                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(3)
                        }
                }

            textContainer.addView(fileName)
            textContainer.addView(outputType)

            val remove =
                TextView(this).apply {

                    text = "×"

                    textSize = 22f

                    gravity = android.view.Gravity.CENTER

                    setTextColor(0xFF999999.toInt())

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(40),
                            dp(40)
                        )

                    setOnClickListener {

                        if (!converting) {

                            selectedFiles.removeAt(index)

                            updateUi()
                        }
                    }
                }

            row.addView(icon)
            row.addView(textContainer)
            row.addView(remove)

            binding.fileListContainer.addView(row)
        }
    }

    private fun updateFormatUi() {

        when (outputFormat) {

            OutputFormat.PNG -> {

                binding.formatPngButton.setBackgroundResource(
                    R.drawable.bg_segment_selected
                )

                binding.formatPngButton.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                binding.formatVectorButton.setBackgroundResource(
                    R.drawable.bg_segment_normal
                )

                binding.formatVectorButton.setTextColor(
                    0xFF666666.toInt()
                )

                binding.pngSettingsGroup.visibility = View.VISIBLE
            }

            OutputFormat.VECTOR_XML -> {

                binding.formatVectorButton.setBackgroundResource(
                    R.drawable.bg_segment_selected
                )

                binding.formatVectorButton.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                binding.formatPngButton.setBackgroundResource(
                    R.drawable.bg_segment_normal
                )

                binding.formatPngButton.setTextColor(
                    0xFF666666.toInt()
                )

                binding.pngSettingsGroup.visibility = View.GONE
            }
        }
    }

    private fun updatePngSettingsUi() {

        if (outputFormat != OutputFormat.PNG) {
            return
        }

        when (pngSizeMode) {

            PngSizeMode.ORIGINAL -> {

                binding.originalSizeButton.setBackgroundResource(
                    R.drawable.bg_segment_selected
                )

                binding.originalSizeButton.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                binding.customSizeButton.setBackgroundResource(
                    R.drawable.bg_segment_normal
                )

                binding.customSizeButton.setTextColor(
                    0xFF666666.toInt()
                )

                binding.sizeHint.text =
                    "保持 SVG 原始尺寸"

                removeCustomSizeInputs()
            }

            PngSizeMode.CUSTOM -> {

                binding.customSizeButton.setBackgroundResource(
                    R.drawable.bg_segment_selected
                )

                binding.customSizeButton.setTextColor(
                    0xFFFFFFFF.toInt()
                )

                binding.originalSizeButton.setBackgroundResource(
                    R.drawable.bg_segment_normal
                )

                binding.originalSizeButton.setTextColor(
                    0xFF666666.toInt()
                )

                binding.sizeHint.text =
                    if (keepAspectRatio) {
                        "按照原 SVG 比例自动计算高度"
                    } else {
                        "使用自定义宽度和高度"
                    }

                ensureCustomSizeInputs()
            }
        }
    }

    private var customSizeContainer: LinearLayout? = null
    private var widthInput: EditText? = null
    private var heightInput: EditText? = null

    private fun ensureCustomSizeInputs() {

        if (customSizeContainer != null) {
            return
        }

        val parent =
            binding.pngSettingsGroup

        val container =
            LinearLayout(this).apply {

                orientation = LinearLayout.HORIZONTAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(44)
                    ).apply {
                        bottomMargin = dp(5)
                    }
            }

        widthInput =
            createSizeInput(
                customWidth.toString(),
                "宽度"
            )

        heightInput =
            createSizeInput(
                customHeight.toString(),
                "高度"
            )

        container.addView(
            widthInput,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginEnd = dp(5)
            }
        )

        container.addView(
            heightInput,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            ).apply {
                marginStart = dp(5)
            }
        )

        val insertIndex =
            parent.indexOfChild(binding.keepAspectCheckBox)

        parent.addView(
            container,
            insertIndex
        )

        customSizeContainer = container
    }

    private fun createSizeInput(
        value: String,
        hint: String
    ): EditText {

        return EditText(this).apply {

            setText(value)

            this.hint = hint

            textSize = 13f

            setSingleLine(true)

            inputType =
                android.text.InputType.TYPE_CLASS_NUMBER

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )

            setBackgroundResource(
                R.drawable.bg_svg_file_panel
            )
        }
    }

    private fun removeCustomSizeInputs() {

        customSizeContainer?.let {

            binding.pngSettingsGroup.removeView(it)
        }

        customSizeContainer = null
        widthInput = null
        heightInput = null
    }

    private fun updateConvertButton() {

    val button = binding.startConversionButton

    if (converting) {

        button.text = "转换中…"

        button.setTextColor(
            0xFFFFFFFF.toInt()
        )

        button.setBackgroundResource(
            R.drawable.bg_svg_convert_button_running
        )

        button.isEnabled = false

        return
    }

    if (selectedFiles.isEmpty()) {

        button.text = "开始转换"

        button.setTextColor(
            0xFF999999.toInt()
        )

        button.setBackgroundResource(
            R.drawable.bg_svg_convert_button_disabled
        )

        button.isEnabled = false

        return
    }

    val formatText =
        when (outputFormat) {
            OutputFormat.PNG ->
                "转换为 PNG"

            OutputFormat.VECTOR_XML ->
                "转换为 Vector XML"
        }

    button.text =
        "$formatText · ${selectedFiles.size} 个"

    button.setTextColor(
        0xFFFFFFFF.toInt()
    )

    button.setBackgroundResource(
        R.drawable.bg_svg_convert_button_enabled
    )

    button.isEnabled = true
}

    private fun updateOutputDirectory() {

    val outputPath =
        OutputDirectoryManager
            .getOutputDirectoryPath(this)

    binding.directoryPath.text =
        outputPath
            ?: "尚未设置输出目录"
}


    /**
     * 对外提供给底部按钮使用。
     *
     * 下一步把固定底部按钮加入 XML 后，
     * 点击这里即可真正开始转换。
     */
 private fun startConversion() {

    if (converting) {
        return
    }

    if (selectedFiles.isEmpty()) {

        Toast.makeText(
            this,
            "请先选择 SVG 文件",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    /*
     * 转换前必须存在输出目录。
     *
     * 没有输出目录时：
     *
     * 1. 不开始转换
     * 2. 打开系统目录选择器
     * 3. 用户选择目录后返回当前页面
     * 4. 用户再次点击“开始转换”才真正开始
     */
    val outputUri =
        OutputDirectoryManager.getOutputUri(this)

    if (outputUri == null) {

        Toast.makeText(
            this,
            "请先选择输出目录",
            Toast.LENGTH_SHORT
        ).show()

        pickOutputDirectory.launch(null)

        return
    }

    /*
     * PNG 自定义尺寸校验
     */
    if (
        outputFormat == OutputFormat.PNG &&
        pngSizeMode == PngSizeMode.CUSTOM &&
        !readCustomSize()
    ) {
        return
    }

    converting = true

    lockUi(true)

    binding.progressGroup.visibility =
        View.VISIBLE

    binding.progressTitle.text =
        "正在转换"

    binding.progressCount.text =
        "0 / ${selectedFiles.size}"

    binding.progressBar.max = 100
    binding.progressBar.progress = 0

    binding.progressText.text =
        "准备转换…"

    binding.failureGroup.visibility =
        View.GONE

    binding.failureList.removeAllViews()

    binding.resultGroup.visibility =
        View.GONE

    lifecycleScope.launch {

        val results =
            performConversion(outputUri)

        showConversionFinished(results)
    }
}

private fun readCustomSize(): Boolean {
    val width = widthInput
        ?.text
        ?.toString()
        ?.trim()
        ?.toIntOrNull()

    val height = heightInput
        ?.text
        ?.toString()
        ?.trim()
        ?.toIntOrNull()

    if (width == null || height == null || width <= 0 || height <= 0) {
        Toast.makeText(
            this,
            "请输入有效的宽度和高度",
            Toast.LENGTH_SHORT
        ).show()
        return false
    }

    customWidth = width.coerceIn(1, 4096)
    customHeight = height.coerceIn(1, 4096)

    return true
}

private suspend fun performConversion(
    outputUri: Uri
):
    List<ConversionResult> =
    withContext(Dispatchers.IO) {

        val results =
            mutableListOf<ConversionResult>()

        val total =
            selectedFiles.size

        selectedFiles.forEachIndexed { index, file ->

            withContext(Dispatchers.Main) {

                val current =
                    index + 1

                binding.progressCount.text =
                    "$current / $total"

                binding.progressBar.progress =
                    (
                        (index.toFloat() / total) * 100f
                    ).roundToInt()

                binding.progressText.text =
                    "正在处理：${file.displayName}"
            }

            try {

                val outputName =
                    convertSingleFile(
                        file,
                        outputUri
                    )

                results.add(
                    ConversionResult(
                        sourceName = file.displayName,
                        outputName = outputName,
                        success = true
                    )
                )

            } catch (e: Exception) {

                AppLogger.e(
                    "SvgConverterActivity",
                    "SVG 转换失败：${file.displayName}",
                    e
                )

                results.add(
                    ConversionResult(
                        sourceName = file.displayName,
                        outputName = null,
                        success = false,
                        reason = getReadableError(e)
                    )
                )
            }
        }

        results
    }

  private suspend fun convertSingleFile(
    file: SelectedSvg,
    outputUri: Uri
): String {

        val baseName =
            file.displayName
                .substringBeforeLast(
                    ".",
                    file.displayName
                )

        val outputName =
            when (outputFormat) {

                OutputFormat.PNG ->
                    "$baseName.png"

                OutputFormat.VECTOR_XML ->
                    "$baseName.xml"
            }

        when (outputFormat) {

            OutputFormat.PNG -> {

                val bitmap =
                    contentResolver.openInputStream(
                        file.uri
                    )?.use { input ->

                        svgToPngConverter.convert(
                            this,
                            input
                        )
                    }
                        ?: throw IllegalStateException(
                            "无法读取 SVG 文件"
                        )

                val finalBitmap =
                    resizeBitmapIfNeeded(bitmap)

                try {

                    saveBitmap(
                        finalBitmap,
                        outputName,
                        outputUri
                    )

                } finally {

                    if (finalBitmap !== bitmap) {
                        finalBitmap.recycle()
                    }

                    bitmap.recycle()
                }
            }

            OutputFormat.VECTOR_XML -> {

                val xml =
                    contentResolver.openInputStream(
                        file.uri
                    )?.use { input ->

                        svgToVectorXmlConverter.convert(
                            input
                        )
                    }
                        ?: throw IllegalStateException(
                            "无法读取 SVG 文件"
                        )

                saveText(
                    xml,
                    outputName,
                    outputUri
                )
            }
        }

        return outputName
    }

    private fun resizeBitmapIfNeeded(
        bitmap: Bitmap
    ): Bitmap {

        if (
            pngSizeMode == PngSizeMode.ORIGINAL
        ) {
            return bitmap
        }

        val sourceWidth =
            bitmap.width.coerceAtLeast(1)

        val sourceHeight =
            bitmap.height.coerceAtLeast(1)

        var targetWidth =
            customWidth.coerceIn(1, 4096)

        var targetHeight =
            customHeight.coerceIn(1, 4096)

        if (keepAspectRatio) {

            targetHeight =
                (
                    targetWidth.toFloat() *
                        sourceHeight.toFloat() /
                        sourceWidth.toFloat()
                    )
                    .roundToInt()
                    .coerceIn(1, 4096)
        }

        if (
            targetWidth == sourceWidth &&
            targetHeight == sourceHeight
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

 private fun saveBitmap(
    bitmap: Bitmap,
    fileName: String,
    outputUri: Uri
) {

    val directory =
        androidx.documentfile.provider.DocumentFile
            .fromTreeUri(
                this,
                outputUri
            )
            ?: throw IllegalStateException(
                "输出目录不可用"
            )

    if (!directory.isDirectory) {
        throw IllegalStateException(
            "选择的输出位置不是有效目录"
        )
    }

    directory.findFile(fileName)?.delete()

    val outputFile =
        directory.createFile(
            "image/png",
            fileName
        )
            ?: throw IllegalStateException(
                "无法创建输出文件"
            )

    contentResolver.openOutputStream(
        outputFile.uri
    )?.use { stream ->

        if (
            !bitmap.compress(
                Bitmap.CompressFormat.PNG,
                100,
                stream
            )
        ) {
            throw IllegalStateException(
                "PNG 写入失败"
            )
        }

    } ?: throw IllegalStateException(
        "无法打开输出文件"
    )
}

 private fun saveText(
    content: String,
    fileName: String,
    outputUri: Uri
) {

    val directory =
        androidx.documentfile.provider.DocumentFile
            .fromTreeUri(
                this,
                outputUri
            )
            ?: throw IllegalStateException(
                "输出目录不可用"
            )

    if (!directory.isDirectory) {
        throw IllegalStateException(
            "选择的输出位置不是有效目录"
        )
    }

    directory.findFile(fileName)?.delete()

    val outputFile =
        directory.createFile(
            "application/xml",
            fileName
        )
            ?: throw IllegalStateException(
                "无法创建输出文件"
            )

    contentResolver.openOutputStream(
        outputFile.uri
    )?.use { stream ->

        stream.write(
            content.toByteArray(
                Charsets.UTF_8
            )
        )

    } ?: throw IllegalStateException(
        "无法写入 Vector XML"
    )
}

    private suspend fun showConversionFinished(
        results: List<ConversionResult>
    ) = withContext(Dispatchers.Main) {

        val total =
            results.size

        val successCount =
            results.count { it.success }

        val failedResults =
            results.filterNot { it.success }

        binding.progressCount.text =
            "$total / $total"

        binding.progressBar.progress =
            100

        /*
         * 全部成功：
         * 转换结束后直接隐藏进度卡。
         */
        if (failedResults.isEmpty()) {

            binding.progressGroup.visibility =
                View.GONE

        } else {

            /*
             * 有失败：
             * 保留进度卡，并把标题改成“转换完成”。
             */
            binding.progressGroup.visibility =
                View.VISIBLE

            binding.progressTitle.text =
                "转换完成"

            binding.progressText.text =
                "有 ${failedResults.size} 个文件转换失败"

            binding.failureGroup.visibility =
                View.VISIBLE

            binding.failureList.removeAllViews()

            failedResults.forEach { result ->

                val failureText =
                    TextView(this@SvgConverterActivity).apply {

                        text =
                            "✕ ${result.sourceName}　${result.reason}"

                        textSize = 11f

                        setTextColor(
                            0xFF555555.toInt()
                        )

                        setPadding(
                            0,
                            dp(4),
                            0,
                            dp(4)
                        )
                    }

                binding.failureList.addView(
                    failureText
                )
            }
        }

        /*
         * 最终结果永远显示。
         */
        binding.resultGroup.visibility =
            View.VISIBLE

        binding.resultSummary.text =
            "$total 个文件 · 成功 $successCount"

        binding.resultList.removeAllViews()

        results.forEach { result ->

            val row =
                createResultRow(result)

            binding.resultList.addView(row)
        }

        converting = false

        lockUi(false)

        updateUi()
    }

    private fun createResultRow(
        result: ConversionResult
    ): View {

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    android.view.Gravity.CENTER_VERTICAL

                setPadding(
                    dp(13),
                    dp(9),
                    dp(13),
                    dp(9)
                )

                minimumHeight = dp(52)
            }

        val status =
            TextView(this).apply {

                text =
                    if (result.success) {
                        "✓"
                    } else {
                        "✕"
                    }

                textSize = 15f

                setTextColor(
                    if (result.success) {
                        0xFF333333.toInt()
                    } else {
                        0xFF777777.toInt()
                    }
                )

                gravity =
                    android.view.Gravity.CENTER

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(28),
                        dp(40)
                    )
            }

        val text =
            TextView(this).apply {

                text =
                    if (result.success) {
                        result.outputName ?: result.sourceName
                    } else {
                        "${result.sourceName} · ${result.reason}"
                    }

                textSize = 12f

                setTextColor(
                    0xFF444444.toInt()
                )

                maxLines = 2

                ellipsize =
                    android.text.TextUtils.TruncateAt.END

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }

        row.addView(status)
        row.addView(text)

        return row
    }

    private fun getReadableError(
        throwable: Throwable
    ): String {

        val message =
            throwable.message
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        return when {

            message != null ->
                message

            throwable is java.io.IOException ->
                "文件读取失败"

            else ->
                "无法解析 SVG 文件"
        }
    }

private fun lockUi(
    locked: Boolean
) {

    binding.backButton.isEnabled = !locked

    binding.selectZone.isEnabled = !locked

    binding.addMoreButton.isEnabled = !locked

    binding.clearFilesButton.isEnabled = !locked

    binding.formatPngButton.isEnabled = !locked

    binding.formatVectorButton.isEnabled = !locked

    binding.originalSizeButton.isEnabled = !locked

    binding.customSizeButton.isEnabled = !locked

    binding.keepAspectCheckBox.isEnabled = !locked

    binding.startConversionButton.isEnabled = !locked

    /*
     * 转换期间动态文件删除按钮也会被禁用。
     * updateFileList() 会在转换完成后重新生成。
     */
}

 private fun openOutputDirectory() {

    val directoryUri =
        OutputDirectoryManager.getOutputUri(this)

    if (directoryUri == null) {

        Toast.makeText(
            this,
            "尚未设置输出目录",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    lifecycleScope.launch {

        val outputFileUri =
            withContext(Dispatchers.IO) {

                try {

                    val directory =
                        androidx.documentfile.provider.DocumentFile
                            .fromTreeUri(
                                this@SvgConverterActivity,
                                directoryUri
                            )

                    directory
                        ?.listFiles()
                        ?.firstOrNull {
                            it.isFile &&
                                it.length() > 0L
                        }
                        ?.uri

                } catch (e: Exception) {

                    AppLogger.e(
                        "SvgConverterActivity",
                        "读取输出目录失败",
                        e
                    )

                    null
                }
            }

        if (outputFileUri == null) {

            Toast.makeText(
                this@SvgConverterActivity,
                "输出目录中暂时没有文件",
                Toast.LENGTH_SHORT
            ).show()

            return@launch
        }

        val mimeType =
            contentResolver.getType(
                outputFileUri
            ) ?: "application/octet-stream"

        val intent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    outputFileUri,
                    mimeType
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                addFlags(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

        try {

            startActivity(
                Intent.createChooser(
                    intent,
                    "选择文件管理器"
                )
            )

        } catch (e: android.content.ActivityNotFoundException) {

            AppLogger.e(
                "SvgConverterActivity",
                "没有找到可以打开输出文件的应用",
                e
            )

            Toast.makeText(
                this@SvgConverterActivity,
                "手机上没有可以打开此文件的应用",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
    private fun dp(value: Int): Int {

        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }
}