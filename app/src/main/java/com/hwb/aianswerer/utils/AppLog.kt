package com.hwb.aianswerer.utils

import android.util.Log
import com.hwb.aianswerer.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全链路日志系统 — 同时写 logcat（仅 Debug）和文件（始终），方便排查疑难问题。
 *
 * 文件路径: cacheDir/aianswerer_<timestamp>.log
 * 格式:      [时间] [级别] [线程] [位置] 消息
 *
 * 可通过 adb pull 或应用内「关于」页面查看/导出日志文件。
 */
object AppLog {

    private const val TAG = "AIAnswerer"
    private var logFile: File? = null
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /** 初始化日志 — 在 MyApplication.onCreate 中调用 */
    fun init(dir: File) {
        logDir = dir
        // 清理 7 天前的旧日志
        val cutoff = System.currentTimeMillis() - 7 * 24 * 3600_000L
        dir.listFiles()?.forEach { f ->
            if (f.name.startsWith("aianswerer_") && f.lastModified() < cutoff) f.delete()
        }
        logFile = File(dir, "aianswerer_${System.currentTimeMillis()}.log")
        raw("══════════ AIAnswerer Log Start ══════════")
        raw("Version: ${BuildConfig.VERSION_NAME}, Debug: ${BuildConfig.DEBUG}")
        raw("Device: ${android.os.Build.MODEL}, SDK: ${android.os.Build.VERSION.SDK_INT}")
    }

    fun getLogFile(): File? = logFile
    fun getLogDir(): File? = logDir

    /** 搜索所有历史日志文件 */
    fun getLogFiles(): List<File> {
        return logDir?.listFiles { f ->
            f.name.startsWith("aianswerer_") && f.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
    }

    // ── 文件写入（始终执行） ──

    private fun raw(msg: String) {
        try {
            logFile?.let { f ->
                PrintWriter(FileWriter(f, true)).use { it.println(msg) }
            }
        } catch (_: Exception) { /* 绝不 crash */ }
    }

    private fun write(level: String, tag: String, msg: String, th: Throwable? = null) {
        val thread = Thread.currentThread().name
        val line = "[${dateFormat.format(Date())}] [$level] [$thread] [$tag] $msg"
        raw(line)
        th?.let { raw(it.stackTraceToString()) }
    }

    private fun logcat(level: Int, msg: String, th: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            when (level) {
                Log.DEBUG -> Log.d(TAG, msg)
                Log.INFO  -> Log.i(TAG, msg)
                Log.WARN  -> Log.w(TAG, msg, th)
                Log.ERROR -> Log.e(TAG, msg, th)
            }
        }
    }

    // ── 公开 API ──

    /** 调试信息 */
    fun d(tag: String, msg: String) {
        write("DEBUG", tag, msg)
        logcat(Log.DEBUG, "[$tag] $msg")
    }

    /** 关键流程节点 */
    fun i(tag: String, msg: String) {
        write("INFO", tag, msg)
        logcat(Log.INFO, "[$tag] $msg")
    }

    /** 警告 */
    fun w(tag: String, msg: String, th: Throwable? = null) {
        write("WARN", tag, msg, th)
        logcat(Log.WARN, "[$tag] $msg", th)
    }

    /** 错误 */
    fun e(tag: String, msg: String, th: Throwable? = null) {
        write("ERROR", tag, msg, th)
        logcat(Log.ERROR, "[$tag] $msg", th)
    }

    /** 网络请求/响应追踪 */
    fun net(tag: String, msg: String) {
        write("NET", tag, msg)
        logcat(Log.INFO, "[NET-$tag] $msg")
    }

    /** 进入函数 */
    fun enter(tag: String, fn: String) {
        write("TRACE", tag, "→ $fn")
        logcat(Log.DEBUG, "[$tag] → $fn")
    }

    /** 离开函数（含耗时） */
    fun leave(tag: String, fn: String, startMs: Long) {
        val elapsed = System.currentTimeMillis() - startMs
        write("TRACE", tag, "← $fn (${elapsed}ms)")
        logcat(Log.DEBUG, "[$tag] ← $fn (${elapsed}ms)")
    }

    // ── 兼容旧 API（无 tag 参数） ──

    @Deprecated("Use d(tag, msg) instead", ReplaceWith("d(\"APP\", message)"))
    fun d(message: String) = d("APP", message)

    @Deprecated("Use e(tag, msg, th) instead", ReplaceWith("e(\"APP\", message, throwable)"))
    fun e(message: String, throwable: Throwable? = null) = e("APP", message, throwable)

    @Deprecated("Use w(tag, msg, th) instead", ReplaceWith("w(\"APP\", message, throwable)"))
    fun w(message: String, throwable: Throwable? = null) = w("APP", message, throwable)

    @Deprecated("Use i(tag, msg) instead", ReplaceWith("i(\"APP\", message)"))
    fun i(message: String) = i("APP", message)
}
