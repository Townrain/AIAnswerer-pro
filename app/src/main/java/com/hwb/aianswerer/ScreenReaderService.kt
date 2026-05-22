package com.hwb.aianswerer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import com.hwb.aianswerer.utils.AppLog

/**
 * 无障碍屏幕读取服务 — 通过 AccessibilityService 读取屏幕文本内容。
 *
 * 替代截图 + OCR 的方式，直接获取屏幕上的文字节点，速度更快、无需截图权限。
 *
 * 使用方式：
 *   1. 用户在系统设置中启用此服务
 *   2. FloatingWindowService 在"屏幕读取"模式下调用 ScreenReaderService.readScreenText()
 *   3. 返回拼接后的文本，交给 AI 分析
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        AppLog.d("ScreenReaderService 已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理事件，只在主动调用时读取
    }

    override fun onInterrupt() {
        AppLog.d("ScreenReaderService 被中断")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        AppLog.d("ScreenReaderService 已销毁")
    }

    companion object {
        var instance: ScreenReaderService? = null
            private set

        /** 服务是否已连接并可用 */
        val isActive: Boolean get() = instance != null

        /**
         * 读取当前屏幕上的所有文本内容。
         * @return 拼接后的屏幕文本，如果服务不可用返回 null
         */
        fun readScreenText(): String? {
            val service = instance ?: return null
            val rootNode = service.rootInActiveWindow ?: return null

            val textBuilder = StringBuilder()
            collectText(rootNode, textBuilder)
            rootNode.recycle()

            val text = textBuilder.toString().trim()
            return text.ifEmpty { null }
        }

        /**
         * 递归遍历节点树，收集所有可见文本。
         * 跳过不可见、无文本的节点，避免重复内容。
         */
        private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder) {
            // 跳过不可见的节点
            if (!node.isVisibleToUser) return

            // 收集节点文本
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrBlank()) {
                // 简单去重：避免连续重复行
                val lastLine = builder.lines().lastOrNull { it.isNotBlank() }
                if (lastLine != nodeText.trim()) {
                    builder.appendLine(nodeText.trim())
                }
            }

            // 收集 contentDescription（图标按钮等无 text 但有描述的元素）
            val desc = node.contentDescription?.toString()
            if (!desc.isNullOrBlank() && desc != nodeText) {
                // 不添加 contentDescription，避免噪音（按钮描述等）
            }

            // 递归子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectText(child, builder)
                child.recycle()
            }
        }
    }
}
