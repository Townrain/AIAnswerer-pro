package com.hwb.aianswerer.utils

import android.util.Log
import com.hwb.aianswerer.BuildConfig

/**
 * 统一日志工具类
 * 仅在 Debug 构建中输出日志，Release 构建静默
 */
object AppLog {

    private const val TAG = "AIAnswerer"

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, message, throwable)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, message, throwable)
        }
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, message)
        }
    }
}
