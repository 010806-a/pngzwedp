package com.example.myno.tuzhuantong

import android.app.Application
import com.example.myno.tuzhuantong.diagnostics.AppLogger

class PngzwedpApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 第一件事：初始化日志系统
        AppLogger.init(this)

        // 初始化完成后立即写第一条日志
        AppLogger.i(
            "Application",
            "应用 Application.onCreate() 已执行"
        )

        AppLogger.i(
            "Application",
            "诊断日志目录：${AppLogger.getLogDirectory().absolutePath}"
        )

        installCrashHandler()

        AppLogger.i(
            "Application",
            "全局诊断系统初始化完成"
        )
    }

    private fun installCrashHandler() {

        val defaultHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler {
                thread,
                throwable ->

            try {
                AppLogger.e(
                    "UncaughtException",
                    "应用发生未处理异常，线程：${thread.name}",
                    throwable
                )
            } catch (_: Exception) {
            }

            try {
                defaultHandler?.uncaughtException(
                    thread,
                    throwable
                )
            } catch (_: Exception) {
            }
        }
    }
}