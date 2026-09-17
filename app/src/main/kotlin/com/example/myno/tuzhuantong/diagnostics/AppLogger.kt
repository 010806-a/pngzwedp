package com.example.myno.tuzhuantong.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val DIRECTORY_NAME = "diagnostics"
    private const val MAX_LOG_FILES = 50

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext

        val directory = ensureDirectory()

        // 初始化时直接创建测试日志
        write(
            "INFO",
            "AppLogger",
            "诊断日志系统初始化成功\n日志目录：${directory.absolutePath}"
        )
    }

    fun d(
        tag: String,
        message: String
    ) {
        write(
            "DEBUG",
            tag,
            message
        )
    }

    fun i(
        tag: String,
        message: String
    ) {
        write(
            "INFO",
            tag,
            message
        )
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        write(
            "WARN",
            tag,
            message,
            throwable
        )
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {
        write(
            "ERROR",
            tag,
            message,
            throwable
        )
    }

    fun lifecycle(
        activity: String,
        event: String
    ) {
        i(
            "ActivityLifecycle",
            "$activity.$event"
        )
    }

    fun checkpoint(
        activity: String,
        message: String
    ) {
        i(
            activity,
            "[CHECKPOINT] $message"
        )
    }

    fun pageHealth(
        activity: String,
        width: Int,
        height: Int,
        visibility: Int,
        childCount: Int
    ) {
        i(
            "PageHealth",
            "$activity 页面健康检查：" +
                "width=$width, " +
                "height=$height, " +
                "visibility=$visibility, " +
                "childCount=$childCount"
        )
    }

    fun getLogDirectory(): File {
        return ensureDirectory()
    }

    fun getLogFiles(): List<File> {
        return ensureDirectory()
            .listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.equals(
                        "log",
                        ignoreCase = true
                    )
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            ?: emptyList()
    }

    fun readLog(
        file: File
    ): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            "无法读取日志：${e.message}"
        }
    }

    fun clearLogs() {
        getLogFiles().forEach {
            try {
                it.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun write(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable? = null
    ) {

        if (!::appContext.isInitialized) {
            return
        }

        try {

            val directory =
                ensureDirectory()

            val now =
                Date()

            val fileName =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss_SSS",
                    Locale.US
                ).format(now) + ".log"

            val file =
                File(
                    directory,
                    fileName
                )

            val time =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.US
                ).format(now)

            val builder =
                StringBuilder()

            builder.append(
                "==================================================\n"
            )

            builder.append(
                "时间：$time\n"
            )

            builder.append(
                "级别：$level\n"
            )

            builder.append(
                "标签：$tag\n"
            )

            builder.append(
                "Android：${Build.VERSION.RELEASE}\n"
            )

            builder.append(
                "SDK：${Build.VERSION.SDK_INT}\n"
            )

            builder.append(
                "设备：${Build.MANUFACTURER} ${Build.MODEL}\n"
            )

            builder.append(
                "日志目录：${directory.absolutePath}\n"
            )

            builder.append(
                "==================================================\n"
            )

            builder.append(message)
            builder.append("\n")

            if (throwable != null) {

                builder.append(
                    "\n异常类型："
                )

                builder.append(
                    throwable.javaClass.name
                )

                builder.append(
                    "\n异常信息："
                )

                builder.append(
                    throwable.message ?: "无"
                )

                builder.append(
                    "\n\n完整堆栈：\n"
                )

                builder.append(
                    throwable.stackTraceToString()
                )

                builder.append("\n")
            }

            file.writeText(
                builder.toString()
            )

            trimOldLogs()

        } catch (_: Exception) {
            // 日志系统自身不能影响应用
        }
    }

    private fun ensureDirectory(): File {

        val directory =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

                File(
                    appContext.getExternalFilesDir(null),
                    DIRECTORY_NAME
                )

            } else {

                File(
                    appContext.filesDir,
                    DIRECTORY_NAME
                )
            }

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return directory
    }

    private fun trimOldLogs() {

        val files =
            getLogFiles()

        if (files.size <= MAX_LOG_FILES) {
            return
        }

        files
            .drop(MAX_LOG_FILES)
            .forEach {
                try {
                    it.delete()
                } catch (_: Exception) {
                }
            }
    }
}