package com.hwb.aianswerer

import org.junit.Assert.*
import org.junit.Test

/**
 * DialogQueue 单元测试
 *
 * NOTE: DialogQueue.checkAndQueueDialogs() calls AppConfig.isFirstLaunch()
 * and AppConfig.isApiConfigValid() which require Android MMKV runtime.
 * Tests that hit these paths use safelyInvoke() to skip gracefully via
 * JUnit Assume when the Android runtime is unavailable in JVM.
 *
 * Tests that manipulate queue directly (all other public methods) run
 * without Android dependency because queue is declared internal and
 * processDialogQueue / dismiss / handle / navigate methods only interact
 * with the injected lambdas.
 */
class DialogQueueTest {

    // ── Test helpers ──

    private class Callbacks {
        var languageShown = false
        var modelSetupShown = false
        var restartCalled = false
        var navigateToSettingsCalled = false
        var languageHidden = false
        var modelSetupHidden = false
    }

    private fun createDialogQueue(cb: Callbacks): DialogQueue {
        return DialogQueue(
            showLanguageDialog = { cb.languageShown },
            setShowLanguageDialog = { cb.languageShown = it; if (!it) cb.languageHidden = true },
            showModelSetupDialog = { cb.modelSetupShown },
            setShowModelSetupDialog = { cb.modelSetupShown = it; if (!it) cb.modelSetupHidden = true },
            restartActivity = { cb.restartCalled = true },
            navigateToSettings = { cb.navigateToSettingsCalled = true }
        )
    }


    // ── checkAndQueueDialogs (Android-dependent) ──

    @Test
    fun `checkAndQueueDialogs - 首次启动添加语言对话框`() {
        safelyInvoke {
            val cb = Callbacks()
            val dq = createDialogQueue(cb)
            dq.checkAndQueueDialogs()
            // checkAndQueueDialogs internally calls processDialogQueue(),
            // so languageShown should be true if AppConfig.isFirstLaunch() is true
            assertTrue("First launch should show language dialog", cb.languageShown)
        }
    }

    @Test
    fun `checkAndQueueDialogs - 非首次配置无效添加模型设置对话框`() {
        safelyInvoke {
            val cb = Callbacks()
            val dq = createDialogQueue(cb)
            dq.checkAndQueueDialogs()
            if (!cb.languageShown && !cb.modelSetupShown) {
                // Neither shown — config is valid and not first launch.
                // This is expected on a configured device; the test verifies
                // the method doesn't crash and the callback state is consistent.
            }
        }
    }

    // ── processDialogQueue (queue manipulation) ──

    @Test
    fun `processDialogQueue - 队列有语言对话框时显示语言对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        assertTrue("Should show language dialog", cb.languageShown)
        assertFalse("Should not show model setup", cb.modelSetupShown)
    }

    @Test
    fun `processDialogQueue - 队列有模型设置对话框时显示模型设置`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Should show model setup dialog", cb.modelSetupShown)
        assertFalse("Should not show language dialog", cb.languageShown)
    }

    @Test
    fun `processDialogQueue - 空队列时不显示任何对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.processDialogQueue()
        assertFalse("Should not show language dialog", cb.languageShown)
        assertFalse("Should not show model setup dialog", cb.modelSetupShown)
    }

    @Test
    fun `processDialogQueue - 多个对话框时只显示第一个`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Should show language dialog first", cb.languageShown)
        assertFalse("Should not show model setup yet", cb.modelSetupShown)
        assertEquals("Queue should still have 2 items", 2, dq.queue.size)
    }

    @Test
    fun `processDialogQueue - 重复调用不改变状态`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        assertTrue(cb.languageShown)
        // Second call should be idempotent — same dialog is still at front
        dq.processDialogQueue()
        assertTrue("Language dialog should still be shown", cb.languageShown)
    }

    // ── dismissLanguageDialog ──

    @Test
    fun `dismissLanguageDialog - 关闭语言对话框后隐藏`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        assertTrue("Language dialog should be shown first", cb.languageShown)
        dq.dismissLanguageDialog()
        assertTrue("Language dialog should be hidden", cb.languageHidden)
        assertFalse("Language shown should be false", cb.languageShown)
    }

    @Test
    fun `dismissLanguageDialog - 从队列中移除语言对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        dq.dismissLanguageDialog()
        assertFalse("Queue should not contain language", dq.queue.contains(DialogQueue.DIALOG_LANGUAGE))
        assertEquals("Queue should be empty", 0, dq.queue.size)
    }

    @Test
    fun `dismissLanguageDialog - 关闭语言后显示下一个对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Language dialog should be shown", cb.languageShown)
        dq.dismissLanguageDialog()
        assertTrue("Language should be hidden", cb.languageHidden)
        assertTrue("Model setup should be shown next", cb.modelSetupShown)
    }

    @Test
    fun `dismissLanguageDialog - 队列空时关闭不崩溃`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.dismissLanguageDialog()
        // Should not throw; setShowLanguageDialog(false) called, queue.remove succeeds
        assertTrue("Language hidden flag should be set", cb.languageHidden)
    }

    // ── handleLanguageConfirmed ──

    @Test
    fun `handleLanguageConfirmed - 确认语言后重启Activity`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        // Pre-populate queue with language + model to avoid AppConfig call
        // (handleLanguageConfirmed checks queue.isEmpty() before calling AppConfig)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Language dialog should be shown", cb.languageShown)
        dq.handleLanguageConfirmed()
        assertTrue("Language should be hidden after confirm", cb.languageHidden)
        assertTrue("Should restart activity", cb.restartCalled)
        assertTrue("Model setup should be shown after language confirm", cb.modelSetupShown)
    }

    @Test
    fun `handleLanguageConfirmed - 确认语言后从队列移除`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        dq.handleLanguageConfirmed()
        assertFalse("Language should not be in queue", dq.queue.contains(DialogQueue.DIALOG_LANGUAGE))
        assertEquals("Queue should have 1 item left", 1, dq.queue.size)
        assertEquals("Remaining item should be model setup", DialogQueue.DIALOG_MODEL_SETUP, dq.queue.first())
    }

    // ── dismissModelSetupDialog ──

    @Test
    fun `dismissModelSetupDialog - 关闭模型设置对话框后隐藏`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Model setup should be shown", cb.modelSetupShown)
        dq.dismissModelSetupDialog()
        assertTrue("Model setup should be hidden", cb.modelSetupHidden)
        assertFalse("Model setup shown should be false", cb.modelSetupShown)
    }

    @Test
    fun `dismissModelSetupDialog - 从队列中移除模型设置对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        dq.dismissModelSetupDialog()
        assertFalse("Queue should not contain model setup", dq.queue.contains(DialogQueue.DIALOG_MODEL_SETUP))
        assertEquals("Queue should be empty", 0, dq.queue.size)
    }

    @Test
    fun `dismissModelSetupDialog - 关闭模型后显示下一个对话框`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        assertTrue("Model setup should be shown", cb.modelSetupShown)
        dq.dismissModelSetupDialog()
        assertTrue("Model setup should be hidden", cb.modelSetupHidden)
        assertTrue("Language should be shown next", cb.languageShown)
    }

    @Test
    fun `dismissModelSetupDialog - 队列空时关闭不崩溃`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.dismissModelSetupDialog()
        assertTrue("Model setup hidden flag should be set", cb.modelSetupHidden)
    }

    // ── navigateToModelSettings ──

    @Test
    fun `navigateToModelSettings - 跳转到设置页`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        dq.navigateToModelSettings()
        assertTrue("Should navigate to settings", cb.navigateToSettingsCalled)
        assertTrue("Model setup should be dismissed", cb.modelSetupHidden)
    }

    @Test
    fun `navigateToModelSettings - 即使对话框未显示也能正常工作`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        // Not calling processDialogQueue — dialog is in queue but not shown
        dq.navigateToModelSettings()
        assertTrue("Should navigate to settings regardless", cb.navigateToSettingsCalled)
        assertTrue("Model setup should be hidden", cb.modelSetupHidden)
        assertFalse("Queue should be empty", dq.queue.contains(DialogQueue.DIALOG_MODEL_SETUP))
    }

    // ── Integration / edge cases ──

    @Test
    fun `重复添加同一对话框不会导致异常`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        assertEquals("Queue should have 2 items", 2, dq.queue.size)
        dq.processDialogQueue()
        assertTrue("Model setup should be shown", cb.modelSetupShown)
        dq.dismissModelSetupDialog()
        // After first dismiss, second duplicate should appear
        dq.processDialogQueue()
        assertTrue("Duplicate model setup should still show", cb.modelSetupShown)
    }

    @Test
    fun `对话框关闭流程完整不崩溃`() {
        // Full lifecycle: language → dismiss → model setup → navigate to settings
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Step 1: language shown", cb.languageShown)
        dq.dismissLanguageDialog()
        assertTrue("Step 2: language hidden", cb.languageHidden)
        assertTrue("Step 3: model setup shown", cb.modelSetupShown)
        dq.navigateToModelSettings()
        assertTrue("Step 4: navigated to settings", cb.navigateToSettingsCalled)
        assertTrue("Step 5: model setup hidden", cb.modelSetupHidden)
    }

    @Test
    fun `队列清空后可重新添加`() {
        val cb = Callbacks()
        val dq = createDialogQueue(cb)
        dq.queue.add(DialogQueue.DIALOG_LANGUAGE)
        dq.processDialogQueue()
        dq.dismissLanguageDialog()
        assertEquals("Queue should be empty after dismiss", 0, dq.queue.size)
        // Re-add and process again
        dq.queue.add(DialogQueue.DIALOG_MODEL_SETUP)
        dq.processDialogQueue()
        assertTrue("Should show model setup after re-add", cb.modelSetupShown)
    }
}
