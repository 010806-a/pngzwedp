package com.example.myno.pngzwedp.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.myno.pngzwedp.R
import com.example.myno.pngzwedp.databinding.ActivityDecompileBinding
import com.example.myno.pngzwedp.diagnostics.AppLogger
import com.example.myno.pngzwedp.storage.OutputDirectoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory

class DecompileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDecompileBinding

    private var selectedUris: List<Uri> = emptyList()
    private var outputDirectoryUri: Uri? = null
    private var isConverting = false

    private enum class VectorSize {
        SIZE_24,
        SIZE_48,
        CUSTOM
    }

    private var vectorSize = VectorSize.SIZE_24

    private data class XmlResult(
        val name: String,
        val uri: Uri
    )

    private val xmlResults = mutableListOf<XmlResult>()

    private var pendingSaveXml: String? = null
    private var pendingSaveFileName: String? = null

    private val pickSvgFiles =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isEmpty()) {
                return@registerForActivityResult
            }

            val svgUris =
                uris.filter { isSvgUri(it) }

            if (svgUris.isEmpty()) {
                Toast.makeText(
                    this,
                    "没有找到可处理的 SVG 文件",
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            selectedUris = svgUris

            selectedUris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    AppLogger.w(
                        "DecompileActivity",
                        "获取 SVG 持久读取权限失败：$uri",
                        e
                    )
                }
            }

            xmlResults.clear()

            updateSelectedFiles()
            showReadyState()
        }

    private val pickOutputDirectory =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri == null) {
                return@registerForActivityResult
            }

            try {
                OutputDirectoryManager.takePersistablePermission(
                    this,
                    Intent().apply {
                        flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    },
                    uri
                )
            } catch (e: Exception) {
                AppLogger.w(
                    "DecompileActivity",
                    "保存输出目录权限失败",
                    e
                )
            }

            outputDirectoryUri = uri

            OutputDirectoryManager.saveOutputUri(
                this,
                uri
            )

            updateDirectoryFromUri(uri)
            showReadyState()
        }

    private val createXmlDocument =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument(
                "text/xml"
            )
        ) { uri ->

            val xml = pendingSaveXml
            val fileName = pendingSaveFileName

            pendingSaveXml = null
            pendingSaveFileName = null

            if (
                uri == null ||
                xml == null
            ) {
                return@registerForActivityResult
            }

            lifecycleScope.launch(
                Dispatchers.IO
            ) {
                try {

                    contentResolver
                        .openOutputStream(
                            uri,
                            "wt"
                        )
                        ?.bufferedWriter(
                            Charsets.UTF_8
                        )
                        ?.use { writer ->
                            writer.write(xml)
                        }
                        ?: throw IllegalStateException(
                            "无法打开保存文件"
                        )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DecompileActivity,
                            "XML 已保存",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                } catch (e: Exception) {

                    AppLogger.e(
                        "DecompileActivity",
                        "保存 XML 失败",
                        e
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@DecompileActivity,
                            "保存 XML 失败：${e.message ?: "未知错误"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityDecompileBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        setupViews()
        updateVectorSizeUi()
        updateOutputDirectory()
        updateSelectedFiles()
        updateConvertButton()
        runPageHealthCheck()
    }

    override fun onResume() {
        super.onResume()

        if (
            ::binding.isInitialized &&
            !isConverting
        ) {
            updateOutputDirectory()
        }
    }

    private fun setupViews() {

        binding.backButton.setOnClickListener {
            finish()
        }

        binding.selectFileButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            launchSvgPicker()
        }

        binding.dropZone.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            launchSvgPicker()
        }

        binding.selectAllButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            updateSelectedFiles()
        }

        binding.clearFilesButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            selectedUris = emptyList()
            xmlResults.clear()

            updateSelectedFiles()
            showReadyState()
        }

        binding.changeDirectoryButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            pickOutputDirectory.launch(null)
        }

        binding.openDirectoryButton.setOnClickListener {

            if (!isConverting) {
                openOutputDirectory()
            }
        }

        binding.originalVectorButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            vectorSize = VectorSize.SIZE_24
            updateVectorSizeUi()
        }

        binding.size48VectorButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            vectorSize = VectorSize.SIZE_48
            updateVectorSizeUi()
        }

        binding.customVectorButton.setOnClickListener {

            if (isConverting) {
                return@setOnClickListener
            }

            vectorSize = VectorSize.CUSTOM
            updateVectorSizeUi()

            Toast.makeText(
                this,
                "当前自定义尺寸为 48dp",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.convertButton.setOnClickListener {

            if (!isConverting) {
                convertSelectedFiles()
            }
        }
    }

    private fun launchSvgPicker() {

        pickSvgFiles.launch(
            arrayOf(
                "image/svg+xml",
                "text/xml",
                "application/xml",
                "*/*"
            )
        )
    }

    private fun updateVectorSizeUi() {

        setSegmentState(
            binding.originalVectorButton,
            vectorSize == VectorSize.SIZE_24
        )

        setSegmentState(
            binding.size48VectorButton,
            vectorSize == VectorSize.SIZE_48
        )

        setSegmentState(
            binding.customVectorButton,
            vectorSize == VectorSize.CUSTOM
        )

        binding.vectorSizeHint.text =
            when (vectorSize) {

                VectorSize.SIZE_24 ->
                    "Vector 输出尺寸为 24dp × 24dp"

                VectorSize.SIZE_48 ->
                    "Vector 输出尺寸为 48dp × 48dp"

                VectorSize.CUSTOM ->
                    "Vector 输出使用自定义尺寸"
            }
    }

    private fun setSegmentState(
        view: TextView,
        selected: Boolean
    ) {

        view.setBackgroundResource(
            if (selected) {
                R.drawable.bg_segment_selected
            } else {
                R.drawable.bg_segment_normal
            }
        )

        view.setTextColor(
            if (selected) {
                Color.WHITE
            } else {
                Color.rgb(
                    102,
                    107,
                    114
                )
            }
        )
    }

    private fun updateSelectedFiles() {

        val count = selectedUris.size

        binding.selectedFileCount.text =
            if (count == 0) {
                "尚未选择文件"
            } else {
                "已选择 $count 个文件"
            }

        binding.selectedFileHint.text =
            if (count == 0) {
                "支持一次选择多个 SVG 文件"
            } else {
                "将按顺序批量生成 Android Vector XML"
            }

        binding.selectAllButton.visibility =
            if (count > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.clearFilesButton.visibility =
            if (count > 0) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.fileListContainer.removeAllViews()

        if (count == 0) {

            binding.fileListContainer.visibility =
                View.GONE

        } else {

            binding.fileListContainer.visibility =
                View.VISIBLE

            val displayCount =
                minOf(
                    count,
                    36
                )

            repeat(displayCount) { index ->

                binding.fileListContainer.addView(
                    createFileRow(
                        index,
                        selectedUris[index]
                    )
                )
            }

            if (count > displayCount) {

                binding.fileListContainer.addView(
                    createMoreRow(
                        count - displayCount
                    )
                )
            }
        }

        binding.convertButton.text =
            if (count > 0) {
                "↓  开始批量转换  $count 个文件"
            } else {
                "↓  开始批量转换"
            }

        updateConvertButton()
    }

    private fun createFileRow(
        index: Int,
        uri: Uri
    ): View {

        val row =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                minimumHeight =
                    dp(58)

                setPadding(
                    dp(8),
                    dp(5),
                    dp(8),
                    dp(5)
                )

                setBackgroundResource(
                    R.drawable.bg_tool_item
                )
            }

        val checkBox =
            android.widget.CheckBox(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(36),
                        dp(36)
                    )

                isChecked = true
                isClickable = false

                buttonTintList =
                    ColorStateList(
                        arrayOf(
                            intArrayOf(
                                android.R.attr.state_checked
                            ),
                            intArrayOf()
                        ),
                        intArrayOf(
                            Color.rgb(
                                35,
                                37,
                                40
                            ),
                            Color.rgb(
                                190,
                                194,
                                200
                            )
                        )
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

                text = "<>"
                textSize = 10f

                setTextColor(
                    Color.rgb(
                        70,
                        74,
                        80
                    )
                )

                setBackgroundResource(
                    R.drawable.bg_tool_icon_gray
                )
            }

        val textContainer =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        weight = 1f
                        marginStart = dp(10)
                    }
            }

        val nameView =
            TextView(this).apply {

                text =
                    getDisplayName(uri)
                        ?: "SVG 文件 ${index + 1}"

                textSize = 12.5f

                setTextColor(
                    Color.rgb(
                        45,
                        48,
                        53
                    )
                )

                maxLines = 1

                ellipsize =
                    TextUtils.TruncateAt.END
            }

        val metadata =
            TextView(this).apply {

                text = "SVG · 待转换"
                textSize = 10f

                setTextColor(
                    Color.rgb(
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

        textContainer.addView(nameView)
        textContainer.addView(metadata)

        val status =
            TextView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(54),
                        dp(28)
                    )

                gravity =
                    Gravity.CENTER

                text = "待转换"
                textSize = 9.5f

                setTextColor(
                    Color.rgb(
                        105,
                        110,
                        118
                    )
                )
            }

        val remove =
            TextView(this).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(30),
                        dp(34)
                    )

                gravity =
                    Gravity.CENTER

                text = "×"
                textSize = 18f

                setTextColor(
                    Color.rgb(
                        145,
                        149,
                        155
                    )
                )

                setOnClickListener {

                    if (isConverting) {
                        return@setOnClickListener
                    }

                    selectedUris =
                        selectedUris.filterIndexed {
                                itemIndex,
                                _ ->
                            itemIndex != index
                        }

                    updateSelectedFiles()
                    showReadyState()
                }
            }

        row.addView(checkBox)
        row.addView(icon)
        row.addView(textContainer)
        row.addView(status)
        row.addView(remove)

        return row
    }

    /**
     * 文件列表和结果列表共用的“还有 N 个”提示行。
     *
     * 注意：这里整个 Activity 只保留这一份，
     * 修复之前重复定义 createMoreRow() 导致的 Kotlin 冲突。
     */
    private fun createMoreRow(
        count: Int
    ): View {

        return TextView(this).apply {

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(40)
                )

            gravity =
                Gravity.CENTER_VERTICAL

            setPadding(
                dp(14),
                0,
                dp(14),
                0
            )

            text =
                "还有 $count 个文件"

            textSize = 12f

            setTextColor(
                Color.rgb(
                    138,
                    143,
                    152
                )
            )
        }
    }

    private fun updateOutputDirectory() {

        val uri =
            OutputDirectoryManager.getOutputUri(
                this
            )

        if (uri == null) {

            outputDirectoryUri = null

            binding.directoryPath.text =
                "尚未设置输出目录"

            binding.directoryHint.text =
                "请选择 XML 保存位置"

            binding.openDirectoryButton.isEnabled =
                false

            updateConvertButton()

            return
        }

        val directory =
            try {
                DocumentFile.fromTreeUri(
                    this,
                    uri
                )
            } catch (_: Exception) {
                null
            }

        if (
            directory == null ||
            !directory.exists() ||
            !directory.isDirectory ||
            !directory.canWrite()
        ) {

            outputDirectoryUri = null

            binding.directoryPath.text =
                "原输出目录不可用"

            binding.directoryHint.text =
                "请重新选择输出目录"

            binding.openDirectoryButton.isEnabled =
                false

            updateConvertButton()

            return
        }

        outputDirectoryUri = uri

        updateDirectoryFromUri(uri)
    }

    private fun updateDirectoryFromUri(
        uri: Uri
    ) {

        val directory =
            DocumentFile.fromTreeUri(
                this,
                uri
            )

        binding.directoryPath.text =
            getRealDirectoryPath(uri)
                ?: directory?.name?.let {
                    "$it/"
                }
                ?: uri.toString()

        binding.directoryHint.text =
            "XML 文件将保存到这个目录"

        binding.openDirectoryButton.isEnabled =
            true

        updateConvertButton()
    }

    private fun updateConvertButton() {

        binding.convertButton.isEnabled =
            selectedUris.isNotEmpty() &&
                outputDirectoryUri != null &&
                hasValidOutputDirectory() &&
                !isConverting
    }

    private fun hasValidOutputDirectory(): Boolean {

        val uri =
            outputDirectoryUri
                ?: OutputDirectoryManager
                    .getOutputUri(this)
                ?: return false

        return try {

            val directory =
                DocumentFile.fromTreeUri(
                    this,
                    uri
                )

            directory != null &&
                directory.exists() &&
                directory.isDirectory &&
                directory.canWrite()

        } catch (_: Exception) {
            false
        }
    }

    private fun convertSelectedFiles() {

        if (selectedUris.isEmpty()) {

            Toast.makeText(
                this,
                "请先选择 SVG 文件",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val outputUri =
            outputDirectoryUri
                ?: OutputDirectoryManager
                    .getOutputUri(this)

        if (outputUri == null) {

            Toast.makeText(
                this,
                "请先选择输出目录",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val outputDirectory =
            DocumentFile.fromTreeUri(
                this,
                outputUri
            )

        if (
            outputDirectory == null ||
            !outputDirectory.exists() ||
            !outputDirectory.isDirectory ||
            !outputDirectory.canWrite()
        ) {

            Toast.makeText(
                this,
                "输出目录不可用，请重新选择",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        isConverting = true

        xmlResults.clear()

        binding.progressContainer.visibility =
            View.VISIBLE

        binding.resultContainer.visibility =
            View.GONE

        binding.selectFileButton.isEnabled =
            false

        binding.selectAllButton.isEnabled =
            false

        binding.clearFilesButton.isEnabled =
            false

        binding.changeDirectoryButton.isEnabled =
            false

        binding.openDirectoryButton.isEnabled =
            false

        binding.convertButton.isEnabled =
            false

        val total =
            selectedUris.size

        binding.progressBar.max =
            total

        binding.progressBar.progress =
            0

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            var successCount = 0
            var failedCount = 0

            selectedUris.forEachIndexed {
                    index,
                    uri ->

                val current =
                    index + 1

                val fileName =
                    getDisplayName(uri)
                        ?: "SVG 文件 $current.svg"

                withContext(Dispatchers.Main) {

                    binding.progressText.text =
                        "正在生成 XML"

                    binding.progressCount.text =
                        "$current / $total"

                    binding.progressCurrent.text =
                        "当前：$fileName"

                    binding.progressRemaining.text =
                        "剩余 ${total - current} 个"

                    binding.progressBar.progress =
                        index
                }

                try {

                    val result =
                        convertSingleFile(
                            uri,
                            outputDirectory
                        )

                    if (result != null) {

                        xmlResults.add(
                            XmlResult(
                                name =
                                    result.name
                                        ?: createOutputFileName(
                                            fileName
                                        ),
                                uri =
                                    result.uri
                            )
                        )

                        successCount++

                    } else {
                        failedCount++
                    }

                } catch (e: Exception) {

                    failedCount++

                    AppLogger.e(
                        "DecompileActivity",
                        "转换失败：$fileName",
                        e
                    )
                }
            }

            withContext(Dispatchers.Main) {

                binding.progressBar.progress =
                    total

                binding.progressContainer.visibility =
                    View.GONE

                isConverting = false

                binding.selectFileButton.isEnabled =
                    true

                binding.selectAllButton.isEnabled =
                    selectedUris.isNotEmpty()

                binding.clearFilesButton.isEnabled =
                    selectedUris.isNotEmpty()

                binding.changeDirectoryButton.isEnabled =
                    true

                binding.openDirectoryButton.isEnabled =
                    hasValidOutputDirectory()

                binding.resultContainer.visibility =
                    View.VISIBLE

                binding.resultTitle.text =
                    "转换结果"

                binding.resultText.text =
                    "$successCount 个 SVG 已生成对应 XML"

                binding.resultHint.text =
                    if (failedCount == 0) {
                        "所有文件已经处理完成"
                    } else {
                        "成功 $successCount 个 · 失败 $failedCount 个"
                    }

                populateResultList()

                updateConvertButton()

                Toast.makeText(
                    this@DecompileActivity,
                    "SVG → XML 完成",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun convertSingleFile(
        uri: Uri,
        outputDirectory: DocumentFile
    ): DocumentFile? {

        val inputStream =
            contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(
                    "无法读取 SVG"
                )

        val svgText =
            inputStream
                .bufferedReader(Charsets.UTF_8)
                .use {
                    it.readText()
                }

        val document =
            parseSvg(svgText)

        val vectorXml =
            convertSvgToVectorXml(document)

        val originalName =
            getDisplayName(uri)
                ?: "converted.svg"

        val outputName =
            createOutputFileName(
                originalName
            )

        val outputFile =
            createUniqueOutputFile(
                outputDirectory,
                outputName
            )

        val outputStream =
            contentResolver.openOutputStream(
                outputFile.uri,
                "wt"
            )
                ?: throw IllegalStateException(
                    "无法创建 XML 文件"
                )

        outputStream
            .bufferedWriter(Charsets.UTF_8)
            .use {
                it.write(vectorXml)
            }

        return outputFile
    }

    private fun parseSvg(
        svgText: String
    ): Document {

        val factory =
            DocumentBuilderFactory
                .newInstance()
                .apply {

                    isNamespaceAware =
                        true

                    try {
                        setFeature(
                            "http://xml.org/sax/features/external-general-entities",
                            false
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setFeature(
                            "http://xml.org/sax/features/external-parameter-entities",
                            false
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setFeature(
                            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                            false
                        )
                    } catch (_: Exception) {
                    }
                }

        return factory
            .newDocumentBuilder()
            .parse(
                svgText.byteInputStream(
                    Charsets.UTF_8
                )
            )
    }

    private fun convertSvgToVectorXml(
        document: Document
    ): String {

        val svgElement =
            document.documentElement
                ?: throw IllegalStateException(
                    "SVG 文档为空"
                )

        val viewBox =
            svgElement
                .getAttribute("viewBox")
                .takeIf {
                    it.isNotBlank()
                }

        val values =
            viewBox
                ?.trim()
                ?.split(
                    Regex("\\s+")
                )

        val viewportWidth =
            values
                ?.getOrNull(2)
                ?: "24"

        val viewportHeight =
            values
                ?.getOrNull(3)
                ?: "24"

        val dpSize =
            when (vectorSize) {

                VectorSize.SIZE_24 ->
                    "24dp"

                VectorSize.SIZE_48 ->
                    "48dp"

                VectorSize.CUSTOM ->
                    "48dp"
            }

        val writer =
            StringWriter()

        writer.append(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        )

        writer.append(
            "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        )

        writer.append(
            "    android:width=\"$dpSize\"\n"
        )

        writer.append(
            "    android:height=\"$dpSize\"\n"
        )

        writer.append(
            "    android:viewportWidth=\"$viewportWidth\"\n"
        )

        writer.append(
            "    android:viewportHeight=\"$viewportHeight\">\n"
        )

        val paths =
            svgElement.getElementsByTagNameNS(
                "*",
                "path"
            )

        for (index in 0 until paths.length) {

            val path =
                paths.item(index)

            val pathData =
                path.attributes
                    ?.getNamedItem("d")
                    ?.nodeValue
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: continue

            writer.append(
                "    <path\n"
            )

            writer.append(
                "        android:pathData=\""
            )

            writer.append(
                escapeXmlAttribute(pathData)
            )

            writer.append("\"")

            val fill =
                path.attributes
                    ?.getNamedItem("fill")
                    ?.nodeValue

            if (
                !fill.isNullOrBlank() &&
                fill != "none"
            ) {

                writer.append(
                    "\n        android:fillColor=\""
                )

                writer.append(
                    escapeXmlAttribute(
                        normalizeColor(fill)
                    )
                )

                writer.append("\"")
            }

            writer.append(
                " />\n"
            )
        }

        writer.append(
            "</vector>"
        )

        return writer.toString()
    }

    private fun populateResultList() {

        binding.resultListContainer.removeAllViews()

        val displayCount =
            minOf(
                xmlResults.size,
                36
            )

        repeat(displayCount) { index ->

            val result =
                xmlResults[index]

            val row =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL

                    minimumHeight =
                        dp(60)

                    setPadding(
                        dp(10),
                        dp(7),
                        dp(8),
                        dp(7)
                    )

                    setBackgroundResource(
                        R.drawable.bg_tool_item
                    )
                }

            val icon =
                TextView(this).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(38),
                            dp(38)
                        )

                    gravity =
                        Gravity.CENTER

                    text = "XML"
                    textSize = 8f

                    setTextColor(
                        Color.rgb(
                            70,
                            74,
                            80
                        )
                    )

                    setBackgroundResource(
                        R.drawable.bg_tool_icon_gray
                    )
                }

            val textContainer =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    layoutParams =
                        LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 1f
                            marginStart = dp(10)
                        }
                }

            val name =
                TextView(this).apply {

                    text = result.name

                    maxLines = 1

                    ellipsize =
                        TextUtils.TruncateAt.END

                    textSize = 12f

                    setTextColor(
                        Color.rgb(
                            45,
                            49,
                            55
                        )
                    )
                }

            val metadata =
                TextView(this).apply {

                    text =
                        "Android Vector · XML"

                    textSize = 10f

                    setTextColor(
                        Color.rgb(
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

            textContainer.addView(name)
            textContainer.addView(metadata)

            val success =
                TextView(this).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(30),
                            dp(30)
                        )

                    gravity =
                        Gravity.CENTER

                    text = "✓"
                    textSize = 15f

                    setTextColor(
                        Color.rgb(
                            48,
                            130,
                            82
                        )
                    )
                }

            val viewButton =
                TextView(this).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            dp(54),
                            dp(34)
                        )

                    gravity =
                        Gravity.CENTER

                    text = "查看"
                    textSize = 11f

                    setTextColor(
                        Color.rgb(
                            45,
                            49,
                            55
                        )
                    )

                    setBackgroundResource(
                        R.drawable.bg_tool_item
                    )

                    setOnClickListener {
                        showXmlPreview(result)
                    }
                }

            row.addView(icon)
            row.addView(textContainer)
            row.addView(success)
            row.addView(viewButton)

            binding.resultListContainer.addView(row)
        }

        if (
            xmlResults.size > displayCount
        ) {

            binding.resultListContainer.addView(
                createMoreRow(
                    xmlResults.size -
                        displayCount
                )
            )
        }
    }

    private fun showXmlPreview(
        result: XmlResult
    ) {

        lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                val xml =
                    contentResolver
                        .openInputStream(result.uri)
                        ?.bufferedReader(
                            Charsets.UTF_8
                        )
                        ?.use {
                            it.readText()
                        }
                        ?: throw IllegalStateException(
                            "无法读取 XML"
                        )

                withContext(Dispatchers.Main) {
                    showXmlDialog(
                        result.name,
                        xml
                    )
                }

            } catch (e: Exception) {

                AppLogger.e(
                    "DecompileActivity",
                    "读取 XML 失败",
                    e
                )

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        this@DecompileActivity,
                        "读取 XML 失败：${e.message ?: "未知错误"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showXmlDialog(
        fileName: String,
        xml: String
    ) {

        val textView =
            TextView(this).apply {

                text = xml

                textSize = 11f

                setTextColor(
                    Color.rgb(
                        40,
                        43,
                        48
                    )
                )

                setPadding(
                    dp(14),
                    dp(12),
                    dp(14),
                    dp(12)
                )

                typeface =
                    android.graphics.Typeface.MONOSPACE

                setTextIsSelectable(true)
            }

        val scrollView =
            ScrollView(this).apply {
                addView(textView)
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(fileName)
                .setView(scrollView)
                .setNegativeButton(
                    "关闭",
                    null
                )
                .setNeutralButton(
                    "复制 XML",
                    null
                )
                .setPositiveButton(
                    "保存 XML",
                    null
                )
                .create()

        dialog.setOnShowListener {

            dialog
                .getButton(
                    AlertDialog.BUTTON_NEUTRAL
                )
                .setOnClickListener {
                    copyXml(xml)
                }

            dialog
                .getButton(
                    AlertDialog.BUTTON_POSITIVE
                )
                .setOnClickListener {
                    saveXmlCopy(
                        fileName,
                        xml
                    )
                }
        }

        dialog.show()
    }

    private fun copyXml(
        xml: String
    ) {

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "XML",
                xml
            )
        )

        Toast.makeText(
            this,
            "XML 已复制",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun saveXmlCopy(
        originalName: String,
        xml: String
    ) {

        pendingSaveXml = xml

        pendingSaveFileName =
            createOutputFileName(
                originalName
            )

        createXmlDocument.launch(
            pendingSaveFileName
                ?: "converted.xml"
        )
    }

    private fun openOutputDirectory() {

        val treeUri =
            outputDirectoryUri
                ?: OutputDirectoryManager
                    .getOutputUri(this)

        if (treeUri == null) {

            Toast.makeText(
                this,
                "尚未设置输出目录",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val documentId =
                DocumentsContract
                    .getTreeDocumentId(
                        treeUri
                    )

            val documentUri =
                DocumentsContract
                    .buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                    )

            startActivity(
                Intent(
                    Intent.ACTION_VIEW
                ).apply {

                    setDataAndType(
                        documentUri,
                        DocumentsContract
                            .Document
                            .MIME_TYPE_DIR
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            )

        } catch (e: Exception) {

            try {

                startActivity(
                    Intent(
                        Intent.ACTION_OPEN_DOCUMENT_TREE
                    ).apply {

                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )

                        if (
                            android.os.Build.VERSION.SDK_INT >= 26
                        ) {
                            putExtra(
                                DocumentsContract
                                    .EXTRA_INITIAL_URI,
                                treeUri
                            )
                        }
                    }
                )

            } catch (fallbackException: Exception) {

                AppLogger.e(
                    "DecompileActivity",
                    "无法打开输出目录",
                    fallbackException
                )

                Toast.makeText(
                    this,
                    "无法打开输出目录",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun createUniqueOutputFile(
        directory: DocumentFile,
        originalName: String
    ): DocumentFile {

        var name = originalName
        var counter = 1

        while (
            directory.findFile(name) != null
        ) {

            val dot =
                originalName.lastIndexOf('.')

            name =
                if (dot > 0) {

                    val base =
                        originalName.substring(
                            0,
                            dot
                        )

                    val extension =
                        originalName.substring(
                            dot
                        )

                    "${base}_$counter$extension"

                } else {

                    "${originalName}_$counter"
                }

            counter++
        }

        return directory.createFile(
            "text/xml",
            name
        )
            ?: throw IllegalStateException(
                "无法创建输出文件"
            )
    }

    private fun createOutputFileName(
        originalName: String
    ): String {

        val dot =
            originalName.lastIndexOf('.')

        val base =
            if (dot > 0) {
                originalName.substring(
                    0,
                    dot
                )
            } else {
                originalName
            }

        return "$base.xml"
    }

    private fun isSvgUri(
        uri: Uri
    ): Boolean {

        val mimeType =
            contentResolver
                .getType(uri)
                ?.lowercase()

        if (
            mimeType == "image/svg+xml"
        ) {
            return true
        }

        val name =
            getDisplayName(uri)
                ?.lowercase()
                ?: return false

        return name.endsWith(".svg")
    }

    private fun getDisplayName(
        uri: Uri
    ): String? {

        return try {

            contentResolver
                .query(
                    uri,
                    arrayOf(
                        android.provider.OpenableColumns
                            .DISPLAY_NAME
                    ),
                    null,
                    null,
                    null
                )
                ?.use { cursor ->

                    if (cursor.moveToFirst()) {
                        cursor.getString(0)
                    } else {
                        null
                    }
                }

        } catch (_: Exception) {
            null
        }
    }

    private fun escapeXmlAttribute(
        value: String
    ): String {

        return value
            .replace(
                "&",
                "&amp;"
            )
            .replace(
                "\"",
                "&quot;"
            )
            .replace(
                "<",
                "&lt;"
            )
            .replace(
                ">",
                "&gt;"
            )
    }

    private fun normalizeColor(
        color: String
    ): String {

        val value = color.trim()

        if (
            value.startsWith("#")
        ) {
            return value
        }

        return when (
            value.lowercase()
        ) {

            "black" ->
                "#000000"

            "white" ->
                "#FFFFFF"

            "red" ->
                "#FF0000"

            "green" ->
                "#008000"

            "blue" ->
                "#0000FF"

            else ->
                value
        }
    }

    private fun showReadyState() {

        binding.progressContainer.visibility =
            View.GONE

        binding.resultContainer.visibility =
            View.GONE

        isConverting = false

        binding.selectFileButton.isEnabled =
            true

        binding.selectAllButton.isEnabled =
            selectedUris.isNotEmpty()

        binding.clearFilesButton.isEnabled =
            selectedUris.isNotEmpty()

        binding.changeDirectoryButton.isEnabled =
            true

        binding.openDirectoryButton.isEnabled =
            hasValidOutputDirectory()

        updateConvertButton()
    }

    private fun getRealDirectoryPath(
        uri: Uri
    ): String? {

        if (
            uri.authority !=
            "com.android.externalstorage.documents"
        ) {
            return null
        }

        val treeDocumentId =
            try {
                DocumentsContract
                    .getTreeDocumentId(uri)
            } catch (_: Exception) {
                return null
            }

        val separatorIndex =
            treeDocumentId.indexOf(':')

        if (
            separatorIndex <= 0
        ) {
            return null
        }

        val storageId =
            treeDocumentId.substring(
                0,
                separatorIndex
            )

        val relativePath =
            Uri.decode(
                treeDocumentId.substring(
                    separatorIndex + 1
                )
            ).trim('/')

        if (
            storageId.equals(
                "primary",
                ignoreCase = true
            )
        ) {

            return if (
                relativePath.isBlank()
            ) {
                "/storage/emulated/0/"
            } else {
                "/storage/emulated/0/$relativePath/"
            }
        }

        return null
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics.density
            ).toInt()

    private fun runPageHealthCheck() {

        binding.root.postDelayed({

            try {

                AppLogger.pageHealth(
                    activity =
                        "DecompileActivity",
                    width =
                        binding.root.width,
                    height =
                        binding.root.height,
                    visibility =
                        binding.root.visibility,
                    childCount =
                        binding.root.childCount
                )

            } catch (e: Exception) {

                AppLogger.e(
                    "PageHealth",
                    "SVG → XML 页面健康检查失败",
                    e
                )
            }

        }, 500L)
    }

    override fun onDestroy() {

        xmlResults.clear()

        pendingSaveXml = null
        pendingSaveFileName = null

        super.onDestroy()
    }
}

