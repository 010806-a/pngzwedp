package com.example.myno.tuzhuantong

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope

import com.example.myno.tuzhuantong.conversion.ConversionRequest
import com.example.myno.tuzhuantong.conversion.ConverterFactory
import com.example.myno.tuzhuantong.conversion.DetectedFormat
import com.example.myno.tuzhuantong.conversion.FormatDetector
import com.example.myno.tuzhuantong.conversion.ImageFormat
import com.example.myno.tuzhuantong.databinding.ActivityMainBinding
import com.example.myno.tuzhuantong.diagnostics.AppLogger
import com.example.myno.tuzhuantong.storage.OutputDirectoryManager
import com.example.myno.tuzhuantong.tools.SvgConverterActivity




import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /*
     * 当前选择的图片
     */
    private val selectedFiles =
        mutableListOf<Uri>()

    /*
     * 输出目录
     */
    private var outputDirectoryUri: Uri? =
        null

    /*
     * 当前输出格式
     */
    private var currentTargetFormat =
        ImageFormat.WEBP

    /*
     * 三个点工具菜单
     *
     * 当前只保留：
     *
     * B：SVG → PNG
     * D：反编译 / SVG → Android Vector XML
     */
    private var moreToolsPopup: PopupWindow? =
        null

    /*
     * ============================================================
     * 图片选择器
     * ============================================================
     */

    /*
     * Android 13+
     *
     * 使用系统照片选择器
     */
    private val pickImagesFromPhotoPicker =
        registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(
                50
            )
        ) { uris ->

            if (uris.isEmpty()) {
                return@registerForActivityResult
            }

            handleSelectedImages(
                uris
            )
        }

    /*
     * Android 12 及以下
     *
     * 使用系统文件选择器
     */
    private val pickImages =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isEmpty()) {
                return@registerForActivityResult
            }

            handleSelectedImages(
                uris
            )
        }

    /*
     * 选择输出目录
     */
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

            } catch (_: Exception) {
            }

            outputDirectoryUri =
                uri

            OutputDirectoryManager.saveOutputUri(
                this,
                uri
            )

            updateOutputDirectoryText()
        }


    /*
     * ============================================================
     * 三个点菜单
     * ============================================================
     */

    private fun setupMenu() {

        binding.menuButton.setOnClickListener {

            showToolMenu()
        }
    }


    /*
     * 显示三个点工具菜单
     *
     * 当前只有两个入口：
     *
     * 1. SVG → PNG
     * 2. 反编译
     */
    private fun showToolMenu() {

        moreToolsPopup?.dismiss()

        val popupView =
            layoutInflater.inflate(
                R.layout.popup_more_tools,
                null
            )

        moreToolsPopup =
            PopupWindow(
                popupView,
                dp(286),
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
            ).apply {

                /*
                 * PopupWindow 本身透明。
                 *
                 * 真正的灰白背景由：
                 *
                 * bg_more_tools_popup.xml
                 *
                 * 控制。
                 */
                setBackgroundDrawable(
                    ColorDrawable(
                        Color.TRANSPARENT
                    )
                )

                elevation =
                    dp(12).toFloat()

                isFocusable =
                    true

                isOutsideTouchable =
                    true
            }


        /*
         * --------------------------------------------------------
         * B：SVG → PNG
         * --------------------------------------------------------
         */
  /*
 * --------------------------------------------------------
 * SVG 转换
 *
 * SVG → PNG
 * SVG → Android Vector XML
 * --------------------------------------------------------
 */
popupView.findViewById<View>(
    R.id.toolSvgConverter
).setOnClickListener {

    AppLogger.i(
        "MainActivity",
        "点击工具：SVG 转换"
    )

    openTool(
        SvgConverterActivity::class.java
    )
}


        /*
         * --------------------------------------------------------
         * 关闭更多工具弹窗
         *
         * 对应 popup_more_tools.xml 中的：
         *
         * R.id.closeButton
         *
         * 点击右上角 X 后直接关闭 PopupWindow。
         * --------------------------------------------------------
         */
        popupView.findViewById<View>(
            R.id.closeButton
        ).setOnClickListener {

            AppLogger.i(
                "MainActivity",
                "关闭更多工具弹窗"
            )

            moreToolsPopup?.dismiss()
        }


        /*
         * 显示菜单
         */
        moreToolsPopup?.showAsDropDown(
            binding.menuButton,
            -dp(244),
            -dp(58)
        )
    }


    /*
     * 打开工具设置页面
     */
    private fun openTool(
    activityClass: Class<*>
) {

    AppLogger.i(
        "MainActivity",
        "准备打开工具页面，目标工具=${activityClass.name}"
    )

    moreToolsPopup?.dismiss()

    try {

        startActivity(
            Intent(
                this,
                activityClass
            )
        )

        AppLogger.i(
            "MainActivity",
            "工具页面已启动：${activityClass.name}"
        )

    } catch (e: ActivityNotFoundException) {

        AppLogger.e(
            "MainActivity",
            "找不到工具页面：${activityClass.name}",
            e
        )

        Toast.makeText(
            this,
            "无法打开工具页面",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {

        AppLogger.e(
            "MainActivity",
            "打开工具页面失败：${activityClass.name}",
            e
        )

        Toast.makeText(
            this,
            "打开工具失败，请查看诊断日志",
            Toast.LENGTH_LONG
        ).show()
    }
}
    /*
     * dp 转 px
     */
    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }


    /*
     * ============================================================
     * Activity 创建
     * ============================================================
     */

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        binding =
            ActivityMainBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupInitialState()

        setupImageSelection()

        setupOutputDirectory()

        setupFormatSelection()

        setupQuality()

        setupConvertButton()

        setupMenu()

        setupResultButtons()

        restoreOutputDirectory()
    }


    /*
     * ============================================================
     * 处理选择的图片
     * ============================================================
     */

    private fun handleSelectedImages(
        uris: List<Uri>
    ) {

        lifecycleScope.launch {

            val detectedFormats =
                withContext(
                    Dispatchers.IO
                ) {

                    uris.map { uri ->

                        uri to try {

                            FormatDetector
                                .detect(
                                    this@MainActivity,
                                    uri
                                )
                                .format

                        } catch (_: Exception) {

                            ImageFormat.UNKNOWN
                        }
                    }
                }


            /*
             * 当前批次已经存在文件时，
             * 新文件必须保持相同格式。
             */
            val currentFormat =
                if (
                    selectedFiles.isNotEmpty()
                ) {

                    try {

                        FormatDetector
                            .detect(
                                this@MainActivity,
                                selectedFiles.first()
                            )
                            .format

                    } catch (_: Exception) {

                        ImageFormat.UNKNOWN
                    }

                } else {

                    null
                }


            /*
             * 当前没有文件：
             *
             * 使用本次选择中第一个
             * 可以识别的格式。
             *
             * 当前已有文件：
             *
             * 使用当前批次格式。
             */
            val targetFormat =
                currentFormat
                    ?: detectedFormats
                        .firstOrNull {
                            it.second !=
                                ImageFormat.UNKNOWN
                        }
                        ?.second


            if (targetFormat == null) {

                Toast.makeText(
                    this@MainActivity,
                    "无法识别所选图片格式",
                    Toast.LENGTH_LONG
                ).show()

                return@launch
            }


            /*
             * 只接受与当前批次相同格式的文件。
             */
            val acceptedFiles =
                detectedFormats
                    .filter {
                        it.second ==
                            targetFormat
                    }
                    .map {
                        it.first
                    }


            /*
             * 其它格式文件
             */
            val rejectedFiles =
                detectedFormats
                    .filter {
                        it.second !=
                            targetFormat
                    }


            /*
             * 加入当前批次。
             *
             * 不清空 selectedFiles，
             * 支持连续选择。
             */
            acceptedFiles.forEach { uri ->

                if (
                    !selectedFiles.contains(
                        uri
                    )
                ) {

                    selectedFiles.add(
                        uri
                    )
                }
            }


            /*
             * 保存文件访问权限
             */
            takePersistablePermissions(
                acceptedFiles
            )


            /*
             * 刷新文件列表
             */
            refreshFileList()


            /*
             * 不同格式提示
             */
            if (
                rejectedFiles.isNotEmpty()
            ) {

                Toast.makeText(
                    this@MainActivity,
                    "已加入 ${acceptedFiles.size} 个" +
                        " ${targetFormat.displayName} 文件，" +
                        "${rejectedFiles.size} 个其它格式文件" +
                        "未加入，请下次单独选择",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    /*
     * 检测当前选择文件的真实格式
     *
     * 不依赖文件扩展名。
     */
    private fun detectSelectedFormats():
        Map<Uri, DetectedFormat> {

        val result =
            LinkedHashMap<
                Uri,
                DetectedFormat
            >()


        selectedFiles.forEach { uri ->

            val detected =
                try {

                    FormatDetector.detect(
                        this,
                        uri
                    )

                } catch (_: Exception) {

                    DetectedFormat(
                        format =
                            ImageFormat.UNKNOWN,
                        confidence =
                            0
                    )
                }


            result[uri] =
                detected
        }


        return result
    }


    /*
     * ============================================================
     * 初始状态
     * ============================================================
     */

    private fun setupInitialState() {

    binding.homePage.visibility =
        View.VISIBLE

    binding.convertPage.visibility =
        View.GONE

    binding.resultPage.visibility =
        View.GONE

    currentTargetFormat =
        ImageFormat.WEBP

    binding.outputFormatGroup.check(
        R.id.formatWebp
    )

    updateFormatUI(
        ImageFormat.WEBP
    )

    binding.transparentCheckBox.buttonTintList =
        null

    binding.transparentCheckBox.isChecked =
        true

    refreshFileList()
}


    /*
     * ============================================================
     * 图片选择
     * ============================================================
     */

    private fun setupImageSelection() {

        binding.dropArea.setOnClickListener {

            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
            ) {

                pickImagesFromPhotoPicker.launch(

                    PickVisualMediaRequest(
                        ActivityResultContracts
                            .PickVisualMedia
                            .ImageOnly
                    )
                )

            } else {

                pickImages.launch(
                    arrayOf(
                        "image/png",
                        "image/jpeg",
                        "image/webp",
                        "image/bmp",
                        "image/gif",
                        "image/avif"
                    )
                )
            }
        }
    }


    /*
     * ============================================================
     * 输出目录
     * ============================================================
     */

    private fun setupOutputDirectory() {

        binding.outputDirectoryCard.setOnClickListener {

            pickOutputDirectory.launch(
                null
            )
        }
    }


    /*
     * ============================================================
     * 输出格式
     * ============================================================
     */

    private fun setupFormatSelection() {

        binding.outputFormatGroup
            .setOnCheckedChangeListener {
                    _,
                    checkedId ->

                val format =
                    when (checkedId) {

                        R.id.formatPng ->
                            ImageFormat.PNG

                        R.id.formatJpeg ->
                            ImageFormat.JPEG

                        R.id.formatWebp ->
                            ImageFormat.WEBP

                        else ->
                            ImageFormat.WEBP
                    }


                currentTargetFormat =
                    format


                updateFormatUI(
                    format
                )
            }
    }


    /*
     * ============================================================
     * 根据输出格式更新 UI
     * ============================================================
     */

    private fun updateFormatUI(
        format: ImageFormat
    ) {

        when (format) {

            ImageFormat.PNG -> {

                binding.outputFormatDescription.text =
                    "无损 PNG，适合需要透明通道的图片"

                binding.outputSettingTitle.text =
                    "PNG 设置"

                binding.qualitySettingLayout.visibility =
                    View.GONE

                binding.qualitySeekBar.visibility =
                    View.GONE

                binding.transparentCheckBox.visibility =
                    View.VISIBLE

                binding.transparentCheckBox.isChecked =
                    true
            }


            ImageFormat.JPEG -> {

                binding.outputFormatDescription.text =
                    "JPEG，适合照片等不需要透明通道的图片"

                binding.outputSettingTitle.text =
                    "JPEG 设置"

                binding.qualitySettingLayout.visibility =
                    View.VISIBLE

                binding.qualitySeekBar.visibility =
                    View.VISIBLE

                binding.transparentCheckBox.visibility =
                    View.GONE

                binding.transparentCheckBox.isChecked =
                    false
            }


            ImageFormat.WEBP -> {

                binding.outputFormatDescription.text =
                    "WebP，兼顾图片质量与文件大小"

                binding.outputSettingTitle.text =
                    "WebP 设置"

                binding.qualitySettingLayout.visibility =
                    View.VISIBLE

                binding.qualitySeekBar.visibility =
                    View.VISIBLE

                binding.transparentCheckBox.visibility =
                    View.VISIBLE

                binding.transparentCheckBox.isChecked =
                    true
            }


            else -> {
                // 首页当前没有其他输出格式
            }
        }
    }


    /*
     * ============================================================
     * 质量
     * ============================================================
     */

    private fun setupQuality() {

        updateQualityText()

        binding.qualitySeekBar
            .setOnSeekBarChangeListener(

                object :
                    android.widget.SeekBar
                        .OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar:
                            android.widget.SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        updateQualityText()
                    }

                    override fun onStartTrackingTouch(
                        seekBar:
                            android.widget.SeekBar?
                    ) {
                    }

                    override fun onStopTrackingTouch(
                        seekBar:
                            android.widget.SeekBar?
                    ) {
                    }
                }
            )
    }


    /*
     * 更新质量文字
     */
    private fun updateQualityText() {

        binding.qualityText.text =
            (
                binding.qualitySeekBar.progress +
                    10
                ).toString()
    }


    /*
     * ============================================================
     * 开始转换
     * ============================================================
     */

    private fun setupConvertButton() {

        binding.convertButton.setOnClickListener {

            if (
                selectedFiles.isEmpty()
            ) {

                Toast.makeText(
                    this,
                    "请先选择图片",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }


            if (
                outputDirectoryUri == null
            ) {

                pickOutputDirectory.launch(
                    null
                )

                return@setOnClickListener
            }


            startConversion()
        }
    }


    /*
     * 真正开始转换
     */
    private fun startConversion() {

        if (
            selectedFiles.isEmpty()
        ) {

            Toast.makeText(
                this,
                "请先选择图片",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        val outputUri =
            outputDirectoryUri
                ?: OutputDirectoryManager
                    .getOutputUri(
                        this
                    )


        if (
            outputUri == null
        ) {

            Toast.makeText(
                this,
                "请先选择输出目录",
                Toast.LENGTH_LONG
            ).show()


            pickOutputDirectory.launch(
                null
            )

            return
        }


        outputDirectoryUri =
            outputUri


        /*
         * 转换前检测真实格式
         */
        val detectedFormats =
            detectSelectedFormats()


        /*
         * 无法识别
         */
        val unknownEntry =
            detectedFormats.entries
                .firstOrNull {

                    it.value.format ==
                        ImageFormat.UNKNOWN
                }


        if (
            unknownEntry != null
        ) {

            val fileName =
                getFileName(
                    unknownEntry.key
                )
                    ?: "未知文件"


            Toast.makeText(
                this,
                "无法识别文件格式：$fileName",
                Toast.LENGTH_LONG
            ).show()


            return
        }


        /*
         * 当前 Bitmap 转换支持：
         *
         * PNG
         * JPEG
         * WebP
         * BMP
         * GIF
         */
        val unsupportedEntry =
            detectedFormats.entries
                .firstOrNull { entry ->

                    when (
                        entry.value.format
                    ) {

                        ImageFormat.PNG,
                        ImageFormat.JPEG,
                        ImageFormat.WEBP,
                        ImageFormat.BMP,
                        ImageFormat.GIF ->
                            false

                        ImageFormat.AVIF,
                        ImageFormat.SVG,
                        ImageFormat.ANDROID_VECTOR_XML,
                        ImageFormat.UNKNOWN ->
                            true
                    }
                }


        if (
            unsupportedEntry != null
        ) {

            val fileName =
                getFileName(
                    unsupportedEntry.key
                )
                    ?: "未知文件"


            val formatName =
                unsupportedEntry
                    .value
                    .format
                    .displayName


            Toast.makeText(
                this,
                "$fileName：$formatName 暂不支持普通图片转换",
                Toast.LENGTH_LONG
            ).show()


            return
        }


        /*
         * 当前质量
         */
        val quality =
            binding.qualitySeekBar.progress +
                10


        val request =
            ConversionRequest(

                inputUris =
                    selectedFiles.toList(),

                targetFormat =
                    currentTargetFormat,

                quality =
                    quality,

                preserveAlpha =
                    binding.transparentCheckBox
                        .isChecked,

                outputDirectoryUri =
                    outputUri
            )


        /*
         * 进入转换页面
         */
        binding.homePage.visibility =
            View.GONE

        binding.convertPage.visibility =
            View.VISIBLE

        binding.resultPage.visibility =
            View.GONE


        binding.percentText.text =
            "0%"


        binding.progressBar.progress =
            0


        binding.currentFileText.text =
            "准备转换..."


        lifecycleScope.launch {

            try {

                val converter =
                    ConverterFactory.create(
                        currentTargetFormat
                    )


                val result =
                    converter.convert(
                        context =
                            this@MainActivity,

                        request =
                            request

                    ) { current,
                        total,
                        fileName ->

                        withContext(
                            Dispatchers.Main
                        ) {

                            val percent =
                                if (
                                    total > 0
                                ) {

                                    (
                                        current.toFloat() /
                                            total.toFloat() *
                                            100f
                                        ).toInt()

                                } else {

                                    0
                                }


                            binding.percentText.text =
                                "$percent%"


                            binding.progressBar.progress =
                                percent


                            binding.currentFileText.text =
                                fileName
                        }
                    }


                showResult(
                    result
                )

            } catch (e: Exception) {

                binding.convertPage.visibility =
                    View.GONE

                binding.resultPage.visibility =
                    View.GONE

                binding.homePage.visibility =
                    View.VISIBLE


                Toast.makeText(
                    this@MainActivity,
                    e.message
                        ?: "转换失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    /*
     * ============================================================
     * 刷新待转换文件列表
     * ============================================================
     */

    private fun refreshFileList() {

        binding.fileContainer
            .removeAllViews()


        if (
            selectedFiles.isEmpty()
        ) {

            binding.fileSection.visibility =
                View.GONE

            return
        }


        binding.fileSection.visibility =
            View.VISIBLE


        binding.fileCount.text =
            "${selectedFiles.size} 个文件"


        selectedFiles.forEach { uri ->

            val itemView =
                layoutInflater.inflate(
                    R.layout.item_png_file,
                    binding.fileContainer,
                    false
                )


            val thumbnail =
                itemView.findViewById<ImageView>(
                    R.id.fileThumbnail
                )


            val fileName =
                itemView.findViewById<TextView>(
                    R.id.fileName
                )


            val fileSize =
                itemView.findViewById<TextView>(
                    R.id.fileSize
                )


            val deleteButton =
                itemView.findViewById<ImageButton>(
                    R.id.deleteButton
                )


            val name =
                getFileName(
                    uri
                )
                    ?: "未知文件"


            val size =
                getFileSize(
                    uri
                )


            val detectedFormat =
                try {

                    FormatDetector
                        .detect(
                            this,
                            uri
                        )
                        .format

                } catch (_: Exception) {

                    null
                }


            fileName.text =
                name


            fileSize.text =
                if (
                    detectedFormat != null &&
                    detectedFormat.displayName !=
                        "未知"
                ) {

                    "${formatFileSize(size)} · " +
                        detectedFormat.displayName

                } else {

                    formatFileSize(
                        size
                    )
                }


            try {

                thumbnail.setImageURI(
                    uri
                )

            } catch (_: Exception) {

                thumbnail.setImageResource(
                    R.drawable.ic_image
                )
            }


            detectedFormat?.let { format ->

                thumbnail.contentDescription =
                    "${format.displayName} 图片"
            }


            deleteButton.setOnClickListener {

                selectedFiles.remove(
                    uri
                )

                refreshFileList()
            }


            binding.fileContainer.addView(
                itemView
            )
        }
    }


    /*
     * ============================================================
     * 显示转换结果
     * ============================================================
     */

    private fun showResult(
        result: ConversionResult
    ) {

        binding.homePage.visibility =
            View.GONE

        binding.convertPage.visibility =
            View.GONE

        binding.resultPage.visibility =
            View.VISIBLE


        binding.successCount.text =
            result.successCount.toString()


        binding.failedCount.text =
            result.failedCount.toString()


        binding.originalSize.text =
            formatFileSize(
                result.originalTotalSize
            )


        binding.webpSize.text =
            formatFileSize(
                result.outputTotalSize
            )


        binding.savedSize.text =
            formatFileSize(
                result.savedBytes
                    .coerceAtLeast(0L)
            )


        binding.savedPercent.text =
            String.format(
                Locale.getDefault(),
                "节省 %.1f%%",
                result.savedPercent
                    .coerceAtLeast(0.0)
            )


        binding.originalFormatText.text =
            "原始文件"


        binding.outputFormatText.text =
            currentTargetFormat.displayName


        binding.resultSubtitle.text =
            when {

                result.failedCount == 0 &&
                    result.savedBytes > 0L ->
                    "转换完成 · 文件大小减少"

                result.failedCount == 0 ->
                    "转换完成 · 文件已保存"

                result.successCount > 0 ->
                    "部分文件转换完成"

                else ->
                    "转换失败"
            }


        binding.resultTitle.text =
            when {

                result.failedCount == 0 ->
                    "转换完成"

                result.successCount > 0 ->
                    "部分完成"

                else ->
                    "转换失败"
            }


        binding.resultFileContainer
            .removeAllViews()


        result.items.forEach { item ->

            val itemView =
                layoutInflater.inflate(
                    R.layout.item_conversion_result,
                    binding.resultFileContainer,
                    false
                )


            val statusIcon =
                itemView.findViewById<ImageView>(
                    R.id.resultStatusIcon
                )


            val fileName =
                itemView.findViewById<TextView>(
                    R.id.resultFileName
                )


            val fileInfo =
                itemView.findViewById<TextView>(
                    R.id.resultFileInfo
                )


            val errorText =
                itemView.findViewById<TextView>(
                    R.id.resultFileError
                )


            fileName.text =
                item.inputName


            if (
                item.success
            ) {

                statusIcon.setImageResource(
                    R.drawable.ic_check_circle
                )


                fileInfo.text =
                    "${getInputFormatName(item.inputUri)} → " +
                        "${currentTargetFormat.displayName} · " +
                        "${formatFileSize(item.originalSize)} → " +
                        "${formatFileSize(item.outputSize)}"


                errorText.visibility =
                    View.GONE

            } else {

                statusIcon.setImageResource(
                    R.drawable.ic_close
                )


                fileInfo.text =
                    "${getInputFormatName(item.inputUri)} → " +
                        "${currentTargetFormat.displayName}"


                errorText.text =
                    item.errorMessage
                        ?: "转换失败"


                errorText.visibility =
                    View.VISIBLE
            }


            binding.resultFileContainer
                .addView(
                    itemView
                )
        }


        binding.resultPage.scrollTo(
            0,
            0
        )
    }


    /*
     * 获取输入文件真实格式
     */
    private fun getInputFormatName(
        uri: Uri
    ): String {

        return try {

            FormatDetector
                .detect(
                    this,
                    uri
                )
                .format
                .displayName

        } catch (_: Exception) {

            "未知"
        }
    }


    /*
     * ============================================================
     * 首页
     * ============================================================
     */

    private fun showHome() {

        selectedFiles.clear()

        binding.fileContainer
            .removeAllViews()

        binding.fileSection.visibility =
            View.GONE

        binding.homePage.visibility =
            View.VISIBLE

        binding.convertPage.visibility =
            View.GONE

        binding.resultPage.visibility =
            View.GONE
    }


    /*
     * 转换页面
     */
    private fun showConvert() {

        binding.homePage.visibility =
            View.GONE

        binding.convertPage.visibility =
            View.VISIBLE

        binding.resultPage.visibility =
            View.GONE
    }


    /*
     * ============================================================
     * 结果页按钮
     * ============================================================
     */

    private fun setupResultButtons() {

        binding.continueButton.setOnClickListener {

            showHome()
        }


        binding.openDirectoryButton.setOnClickListener {

            openOutputDirectory()
        }
    }


    /*
     * ============================================================
     * 打开输出目录
     * ============================================================
     */

    private fun openOutputDirectory() {

        val directoryUri =
            outputDirectoryUri


        if (
            directoryUri == null
        ) {

            Toast.makeText(
                this,
                "请先选择输出目录",
                Toast.LENGTH_SHORT
            ).show()

            return
        }


        lifecycleScope.launch {

            val outputFileUri =
                withContext(
                    Dispatchers.IO
                ) {

                    try {

                        val directory =
                            DocumentFile.fromTreeUri(
                                this@MainActivity,
                                directoryUri
                            )


                        directory
                            ?.listFiles()
                            ?.firstOrNull {

                                it.isFile &&
                                    it.length() > 0L
                            }
                            ?.uri

                    } catch (_: Exception) {

                        null
                    }
                }


            if (
                outputFileUri == null
            ) {

                Toast.makeText(
                    this@MainActivity,
                    "输出目录中暂时没有文件",
                    Toast.LENGTH_SHORT
                ).show()

                return@launch
            }


            val mimeType =
                contentResolver.getType(
                    outputFileUri
                )
                    ?: "application/octet-stream"


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
                }


            try {

                startActivity(
                    Intent.createChooser(
                        intent,
                        "选择文件管理器"
                    )
                )

            } catch (_: ActivityNotFoundException) {

                Toast.makeText(
                    this@MainActivity,
                    "手机上没有可以打开此文件的应用",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    /*
     * ============================================================
     * 恢复输出目录
     * ============================================================
     */

    private fun restoreOutputDirectory() {

        val savedUri =
            OutputDirectoryManager
                .getOutputUri(
                    this
                )
                ?: return


        outputDirectoryUri =
            savedUri


        updateOutputDirectoryText()
    }


    /*
     * 更新输出目录文字
     */
   private fun updateOutputDirectoryText() {
    val uri =
        outputDirectoryUri
            ?: return

    val fullPath =
        getRealDirectoryPath(uri)

    binding.outputDirectoryText.text =
        fullPath
            ?: (
                DocumentFile
                    .fromTreeUri(
                        this,
                        uri
                    )
                    ?.name
                    ?.let {
                        "$it/"
                    }
                    ?: uri.toString()
            )
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
        android.provider.DocumentsContract
            .getTreeDocumentId(uri)
            ?: return null

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
        treeDocumentId.substring(
            separatorIndex + 1
        )

    if (
        storageId.equals(
            "primary",
            ignoreCase = true
        )
    ) {
        val decodedPath =
            Uri.decode(
                relativePath
            ).trim('/')

        return if (
            decodedPath.isBlank()
        ) {
            "/storage/emulated/0/"
        } else {
            "/storage/emulated/0/$decodedPath/"
        }
    }

    return null
}


    /*
     * ============================================================
     * 持久化图片 Uri 权限
     * ============================================================
     */

    private fun takePersistablePermissions(
        uris: List<Uri>
    ) {

        uris.forEach { uri ->

            try {

                contentResolver
                    .takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

            } catch (_: Exception) {
            }
        }
    }


    /*
     * ============================================================
     * 获取文件名
     * ============================================================
     */

    private fun getFileName(
        uri: Uri
    ): String? {

        var result: String? =
            null


        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (
                cursor.moveToFirst()
            ) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )


                if (
                    index >= 0
                ) {

                    result =
                        cursor.getString(
                            index
                        )
                }
            }
        }


        return result
    }


    /*
     * ============================================================
     * 获取文件大小
     * ============================================================
     */

    private fun getFileSize(
        uri: Uri
    ): Long {

        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (
                cursor.moveToFirst()
            ) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )


                if (
                    index >= 0 &&
                    !cursor.isNull(index)
                ) {

                    return cursor.getLong(
                        index
                    )
                }
            }
        }


        return 0L
    }


    /*
     * ============================================================
     * 文件大小格式化
     * ============================================================
     */

    private fun formatFileSize(
        size: Long
    ): String {

        if (
            size <= 0L
        ) {

            return "0 B"
        }


        val kb =
            1024.0

        val mb =
            kb * 1024.0

        val gb =
            mb * 1024.0


        return when {

            size >= gb ->

                String.format(
                    Locale.getDefault(),
                    "%.2f GB",
                    size / gb
                )


            size >= mb ->

                String.format(
                    Locale.getDefault(),
                    "%.2f MB",
                    size / mb
                )


            size >= kb ->

                String.format(
                    Locale.getDefault(),
                    "%.1f KB",
                    size / kb
                )


            else ->
                "$size B"
        }
    }
}