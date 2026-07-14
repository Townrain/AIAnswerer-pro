package com.hwb.aianswerer

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for pure (non-Android-dependent) constants in Constants.kt.
 * These are compile-time string/int constants that don't need safelyInvoke.
 */
class ConstantsTest {

    @Test
    fun `通知渠道常量不应为空`() {
        assertTrue("NOTIFICATION_CHANNEL_ID should not be blank", Constants.NOTIFICATION_CHANNEL_ID.isNotBlank())
    }

    @Test
    fun `通知ID应为正数`() {
        assertTrue("NOTIFICATION_ID should be positive", Constants.NOTIFICATION_ID > 0)
    }

    @Test
    fun `Intent动作常量格式正确`() {
        assertTrue(
            "ACTION_SHOW_ANSWER should start with package prefix",
            Constants.ACTION_SHOW_ANSWER.startsWith("com.hwb.aianswerer.")
        )
        assertTrue(
            "ACTION_REQUEST_ANSWER should start with package prefix",
            Constants.ACTION_REQUEST_ANSWER.startsWith("com.hwb.aianswerer.")
        )
        assertTrue(
            "ACTION_REFRESH_SETTINGS should start with package prefix",
            Constants.ACTION_REFRESH_SETTINGS.startsWith("com.hwb.aianswerer.")
        )
    }

    @Test
    fun `Extra常量不为空`() {
        assertTrue("EXTRA_ANSWER_TEXT should not be blank", Constants.EXTRA_ANSWER_TEXT.isNotBlank())
        assertTrue("EXTRA_RECOGNIZED_TEXT should not be blank", Constants.EXTRA_RECOGNIZED_TEXT.isNotBlank())
        assertTrue("EXTRA_QUESTION_TEXT should not be blank", Constants.EXTRA_QUESTION_TEXT.isNotBlank())
    }
}
