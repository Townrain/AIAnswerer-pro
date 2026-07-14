package com.hwb.aianswerer.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * JSON工具类
 * 提供全局共享的Gson实例，避免重复创建
 */
object JsonUtil {
    
    /**
     * 全局共享的Gson实例（线程安全）
     * 
     * 特点：
     * 1. 禁用HTML转义，保留原始字符
     * 2. 不序列化null值
     */
    val gson: Gson by lazy {
        GsonBuilder()
            .disableHtmlEscaping()
            .create()
    }
}
