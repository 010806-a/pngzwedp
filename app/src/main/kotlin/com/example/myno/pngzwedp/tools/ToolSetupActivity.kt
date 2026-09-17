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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        AppLogger.lifecycle(
            "ToolSetupActivity",
            "onCreate"
        )

        super.onCreate(
            savedInstanceState
        )

        binding =
            ActivityToolSetupBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

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

        binding.titleText.text =
            "工具设置"

        binding.subtitleText.text =
            "设置工具输出目录"

        binding.toolNameText.text =
            "输出目录"

        binding.toolDescriptionText.text =
            "选择一个目录，用于保存工具生成的文件"

        AppLogger.i(
            "ToolSetupActivity",
            "工具设置页面使用通用输出目录配置"
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

            pickOutputDirectory.launch(
                null
            )
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

    private fun updateDirectory(
        uri: Uri
    ) {

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
            "输出目录 UI 更新完成"
        )
    }

    private fun openTargetActivity() {

        val className =
            nextActivity

        if (className.isNullOrBlank()) {

            Toast.makeText(
                this,
                "工具信息无效，请返回重试",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        /*
         * ToolSetupActivity 是旧的通用工具设置页面。
         *
         * 不再直接引用：
         *
         * SvgToPngActivity
         * DecompileActivity
         *
         * 这里只允许打开当前项目中实际存在的 Activity。
         */
        val targetClass =
            try {
                Class.forName(
                    className
                )
            } catch (e: Exception) {

                AppLogger.e(
                    "ToolSetupActivity",
                    "找不到目标 Activity：$className",
                    e
                )

                null
            }

        if (targetClass == null) {

            Toast.makeText(
                this,
                "该工具已移除或无法使用",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        try {

            startActivity(
                Intent(
                    this,
                    targetClass
                )
            )

            AppLogger.i(
                "ToolSetupActivity",
                "目标工具已打开：$className"
            )

        } catch (e: Exception) {

            AppLogger.e(
                "ToolSetupActivity",
                "打开目标工具失败：$className",
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