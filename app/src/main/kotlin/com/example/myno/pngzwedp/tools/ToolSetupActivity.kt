package com.example.myno.pngzwedp.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.example.myno.pngzwedp.databinding.ActivityToolSetupBinding
import com.example.myno.pngzwedp.diagnostics.AppLogger
import com.example.myno.pngzwedp.storage.OutputDirectoryManager

class ToolSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolSetupBinding

    private var nextActivity: String? = null

    private data class ToolInfo(
        val title: String,
        val description: String
    )

    /**
     * 当前应用允许进入的工具。
     *
     * 目前只保留：
     * 1. SVG → PNG
     * 2. SVG → XML
     */
    private val toolInfoMap =
        mapOf(
            SvgToPngActivity::class.java.name to
                ToolInfo(
                    "SVG → PNG",
                    "将 SVG 转换为 PNG 图片"
                ),

            DecompileActivity::class.java.name to
                ToolInfo(
                    "SVG → XML",
                    "将 SVG 转换为 Android Studio 可使用的 Vector XML"
                )
        )

    private val pickOutputDirectory =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            AppLogger.i(
                "ToolSetupActivity",
                "输出目录选择器返回：$uri"
            )

            if (uri == null) {
                AppLogger.w(
                    "ToolSetupActivity",
                    "用户取消选择输出目录"
                )
                return@registerForActivityResult
            }

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                AppLogger.i(
                    "ToolSetupActivity",
                    "已获取输出目录持久化权限"
                )
            } catch (e: Exception) {
                AppLogger.w(
                    "ToolSetupActivity",
                    "获取输出目录持久化权限失败",
                    e
                )
            }

            OutputDirectoryManager.saveOutputUri(
                this,
                uri
            )

            AppLogger.i(
                "ToolSetupActivity",
                "输出目录已保存：$uri"
            )

            updateDirectory(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onCreate"
        )

        super.onCreate(savedInstanceState)

        binding =
            ActivityToolSetupBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        nextActivity =
            intent.getStringExtra(
                EXTRA_NEXT_ACTIVITY
            )

        AppLogger.i(
            "ToolSetupActivity",
            "nextActivity=$nextActivity"
        )

        setupToolInfo()
        setupViews()

        AppLogger.checkpoint(
            "ToolSetupActivity",
            "工具设置页面初始化完成"
        )
    }

    private fun setupToolInfo() {

        val info =
            nextActivity?.let {
                toolInfoMap[it]
            }

        if (info == null) {

            binding.titleText.text =
                "工具设置"

            binding.subtitleText.text =
                "使用工具前设置输出目录"

            binding.toolNameText.text =
                "工具"

            binding.toolDescriptionText.text =
                "使用工具前设置输出目录"

            AppLogger.w(
                "ToolSetupActivity",
                "没有找到对应工具信息：$nextActivity"
            )

            return
        }

        binding.titleText.text =
            info.title

        binding.subtitleText.text =
            "工具设置"

        binding.toolNameText.text =
            info.title

        binding.toolDescriptionText.text =
            info.description

        AppLogger.i(
            "ToolSetupActivity",
            "当前工具：${info.title}"
        )
    }

    private fun setupViews() {

        binding.backButton.setOnClickListener {

            AppLogger.i(
                "ToolSetupActivity",
                "点击返回按钮"
            )

            finish()
        }

        binding.directoryCard.setOnClickListener {

            AppLogger.i(
                "ToolSetupActivity",
                "点击选择输出目录"
            )

            pickOutputDirectory.launch(null)
        }

        binding.continueButton.setOnClickListener {

            AppLogger.i(
                "ToolSetupActivity",
                "点击使用此目录并继续"
            )

            openTargetActivity()
        }

        val currentDirectory =
            OutputDirectoryManager.getOutputUri(
                this
            )

        if (currentDirectory != null) {

            AppLogger.i(
                "ToolSetupActivity",
                "发现已有输出目录：$currentDirectory"
            )

            updateDirectory(
                currentDirectory
            )
        } else {

            AppLogger.w(
                "ToolSetupActivity",
                "当前尚未设置输出目录"
            )
        }
    }

    private fun updateDirectory(uri: Uri) {

        AppLogger.checkpoint(
            "ToolSetupActivity",
            "开始更新输出目录 UI：$uri"
        )

        val document =
            try {
                DocumentFile.fromTreeUri(
                    this,
                    uri
                )
            } catch (e: Exception) {

                AppLogger.e(
                    "ToolSetupActivity",
                    "读取 DocumentFile 失败",
                    e
                )

                null
            }

        binding.directoryName.text =
            document?.name
                ?: "已选择输出目录"

        binding.directoryPath.text =
            uri.toString()

        AppLogger.i(
            "ToolSetupActivity",
            "输出目录 UI 更新完成，name=${document?.name}"
        )
    }

    private fun openTargetActivity() {

        val className =
            nextActivity

        AppLogger.i(
            "ToolSetupActivity",
            "准备打开目标工具，className=$className"
        )

        if (className == null) {

            AppLogger.e(
                "ToolSetupActivity",
                "nextActivity 为空，无法打开目标工具"
            )

            Toast.makeText(
                this,
                "工具信息无效，请返回重试",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        /*
         * 白名单检查。
         *
         * 即使外部传入了其他 Activity，
         * 这里也只允许当前保留的两个工具继续打开。
         */
        if (!toolInfoMap.containsKey(className)) {

            AppLogger.e(
                "ToolSetupActivity",
                "目标工具不在允许的工具列表中：$className"
            )

            Toast.makeText(
                this,
                "该工具已移除",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        try {

            val targetClass =
                Class.forName(className)

            AppLogger.i(
                "ToolSetupActivity",
                "Class.forName 成功：${targetClass.name}"
            )

            val intent =
                Intent(
                    this,
                    targetClass
                )

            startActivity(intent)

            AppLogger.i(
                "ToolSetupActivity",
                "目标工具已打开"
            )

        } catch (e: Exception) {

            AppLogger.e(
                "ToolSetupActivity",
                "打开目标工具失败，className=$className",
                e
            )

            Toast.makeText(
                this,
                "打开工具失败，请查看诊断日志",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onStart() {

        super.onStart()

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onStart"
        )
    }

    override fun onResume() {

        super.onResume()

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onResume"
        )
    }

    override fun onPause() {

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onPause"
        )

        super.onPause()
    }

    override fun onStop() {

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onStop"
        )

        super.onStop()
    }

    override fun onDestroy() {

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onDestroy"
        )

        super.onDestroy()
    }

    companion object {

        const val EXTRA_NEXT_ACTIVITY =
            "next_activity"
    }
}