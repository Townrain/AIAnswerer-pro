package com.hwb.aianswerer.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

/**
 * ClipboardUtil 单元测试
 *
 * 覆盖：copyToClipboard / getFromClipboard 的正常路径、空安全、异常处理
 *
 * 注意：由于 Android 测试环境的限制，copyToClipboard 在 ClipboardManager
 * 非 null 时可能触发 Android 框架异常，因此该路径的验证通过 mockkStatic 实现。
 */
class ClipboardUtilTest {

    // ── copyToClipboard ──

    @Test
    fun `使用自定义标签复制文本到剪贴板返回true并验证ClipData参数`() {
        mockkStatic(ClipData::class)

        val context = mockk<Context>()
        val clipboardManager = mockk<ClipboardManager>(relaxed = true)
        val clipData = mockk<ClipData>(relaxed = true)
        val clipDescription = mockk<ClipDescription>(relaxed = true)

        every { ClipData.newPlainText("我的标签", "测试内容") } returns clipData
        every { clipData.description } returns clipDescription
        every { clipDescription.label } returns "我的标签"
        every { clipData.getItemAt(0) } returns mockk {
            every { text } returns "测试内容"
        }
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        every { context.getString(any()) } returns "默认标签"

        val result = ClipboardUtil.copyToClipboard(context, "测试内容", "我的标签")

        assertTrue(result)
        verify(exactly = 1) { ClipData.newPlainText("我的标签", "测试内容") }
        verify(exactly = 1) { clipboardManager.setPrimaryClip(clipData) }
    }

    @Test
    fun `省略label时使用资源字符串作为默认标签复制文本`() {
        mockkStatic(ClipData::class)

        val context = mockk<Context>()
        val clipData = mockk<ClipData>(relaxed = true)
        every { ClipData.newPlainText("默认标签", "测试内容") } returns clipData
        every { context.getString(any()) } returns "默认标签"
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns null

        val result = ClipboardUtil.copyToClipboard(context, "测试内容")

        assertTrue(result)
        verify { context.getString(any()) }
        verify { ClipData.newPlainText("默认标签", "测试内容") }
    }

    @Test
    fun `ClipboardManager为null时复制文本不抛异常`() {
        val context = mockk<Context>()
        every { context.getString(any()) } returns "默认标签"
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns null

        val result = ClipboardUtil.copyToClipboard(context, "测试文本")

        assertTrue(result)
    }

    @Test
    fun `复制文本抛出异常时返回false`() {
        val context = mockk<Context>()
        every { context.getString(any()) } returns "默认标签"
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } throws RuntimeException("模拟异常")

        val result = ClipboardUtil.copyToClipboard(context, "测试文本", "标签")

        assertFalse(result)
    }

    // ── getFromClipboard ──

    @Test
    fun `获取剪贴板文本成功`() {
        val context = mockk<Context>()
        val clipboardManager = mockk<ClipboardManager>()
        val clipData = mockk<ClipData>()
        val clipItem = mockk<ClipData.Item>()

        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        every { clipboardManager.primaryClip } returns clipData
        every { clipData.itemCount } returns 1
        every { clipData.getItemAt(0) } returns clipItem
        every { clipItem.text } returns "剪贴板内容"

        val result = ClipboardUtil.getFromClipboard(context)

        assertEquals("剪贴板内容", result)
    }

    @Test
    fun `primaryClip为null时读取剪贴板返回null`() {
        val context = mockk<Context>()
        val clipboardManager = mockk<ClipboardManager>()

        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
        every { clipboardManager.primaryClip } returns null

        val result = ClipboardUtil.getFromClipboard(context)

        assertNull(result)
    }

    @Test
    fun `读取剪贴板抛出异常时返回null`() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } throws RuntimeException("模拟异常")

        val result = ClipboardUtil.getFromClipboard(context)

        assertNull(result)
    }

    @Test
    fun `ClipboardManager为null时读取剪贴板返回null`() {
        val context = mockk<Context>()
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns null

        val result = ClipboardUtil.getFromClipboard(context)

        assertNull(result)
    }
}
