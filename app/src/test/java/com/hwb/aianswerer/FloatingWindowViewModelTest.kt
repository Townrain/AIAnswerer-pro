package com.hwb.aianswerer

import androidx.compose.runtime.mutableStateOf
import com.hwb.aianswerer.models.AIAnswer
import com.hwb.aianswerer.ui.components.FloatingStatus
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingWindowViewModelTest {

    private lateinit var viewModel: FloatingWindowViewModel
    private lateinit var mockCtx: FloatingWindowViewModel.ServiceContext
    private lateinit var mockRecorder: RecordingCoordinator

    @Before
    fun setUp() {
        mockCtx = mockk(relaxed = true)
        mockRecorder = mockk(relaxed = true)
        viewModel = FloatingWindowViewModel()
        viewModel.initialize(mockCtx)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── Initial state ──

    @Test
    fun `initial state has answer hidden`() {
        assertNull(viewModel.answerText.value)
        assertFalse(viewModel.showAnswer.value)
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(FloatingStatus.Idle, viewModel.floatingStatus.value)
    }

    @Test
    fun `initial state not recording`() {
        assertFalse(viewModel.isRecording.value)
        assertEquals(0, viewModel.recordingCaptureCount.value)
    }

    @Test
    fun `initial recording answers are empty`() {
        assertTrue(viewModel.recordingAnswers.value.isEmpty())
        assertTrue(viewModel.recordingCopyTexts.value.isEmpty())
        assertTrue(viewModel.paginatedAnswers.value.isEmpty())
    }

    // ── startRecording ──

    @Test
    fun `startRecording resets all recording state`() {
        // Set dirty state first
        viewModel.isRecording.value = false
        viewModel.recordingCaptureCount.value = 5
        viewModel.recordingSkippedCount.value = 3
        viewModel.recordingFailedCount.value = 2
        viewModel.recordingAnswers.value = listOf(1 to "old answer")
        viewModel.paginatedAnswers.value = listOf(1 to "old paginated")
        viewModel.showAnswer.value = true
        viewModel.answerText.value = "old text"
        viewModel.floatingStatus.value = FloatingStatus.Error

        viewModel.startRecording(mockRecorder)

        assertTrue(viewModel.isRecording.value)
        assertEquals(0, viewModel.recordingCaptureCount.value)
        assertEquals(0, viewModel.recordingSkippedCount.value)
        assertEquals(0, viewModel.recordingFailedCount.value)
        assertTrue(viewModel.recordingAnswers.value.isEmpty())
        assertTrue(viewModel.paginatedAnswers.value.isEmpty())
        assertFalse(viewModel.showAnswer.value)
        assertNull(viewModel.answerText.value)
        assertEquals(FloatingStatus.Idle, viewModel.floatingStatus.value)
    }

    @Test
    fun `startRecording calls recorder start`() {
        viewModel.startRecording(mockRecorder)
        verify { mockRecorder.start() }
    }

    @Test
    fun `startRecording cancels current fetch job`() {
        val mockJob = mockk<kotlinx.coroutines.Job>(relaxed = true)
        viewModel.currentFetchJob = mockJob

        viewModel.startRecording(mockRecorder)

        verify { mockJob.cancel() }
        assertNull(viewModel.currentFetchJob)
    }

    // ── stopRecording ──

    @Test
    fun `stopRecording sets isRecording to false`() {
        viewModel.isRecording.value = true
        every { mockRecorder.stop() } returns RecordingCoordinator.StopResult.NothingToShow

        viewModel.stopRecording(mockRecorder)

        assertFalse(viewModel.isRecording.value)
    }

    @Test
    fun `stopRecording NothingToShow shows toast`() {
        every { mockRecorder.stop() } returns RecordingCoordinator.StopResult.NothingToShow
        every { mockCtx.getString(any()) } returns "无截图"

        viewModel.stopRecording(mockRecorder)

        verify { mockCtx.showToast(any()) }
    }

    @Test
    fun `stopRecording Processing sets status`() {
        every { mockRecorder.stop() } returns RecordingCoordinator.StopResult.Processing(5)
        every { mockCtx.getString(any(), any<Int>()) } returns "处理中"
        every { mockCtx.getString(any()) } returns "已停止"

        viewModel.stopRecording(mockRecorder)

        assertTrue(viewModel.isProcessingRecording.value)
        assertEquals(FloatingStatus.GettingAnswer, viewModel.floatingStatus.value)
    }

    // ── showErrorMessage ──

    @Test
    fun `showErrorMessage sets error status`() {
        viewModel.showErrorMessage("测试错误")

        assertEquals(FloatingStatus.Error, viewModel.floatingStatus.value)
        assertEquals("测试错误", viewModel.statusMessage.value)
    }

    // ── refreshSettingsFromApp ──

    @Test
    fun `refreshSettingsFromApp does not crash`() {
        viewModel.refreshSettingsFromApp()
        // Should not throw
    }

    // ── answerCallbacks ──

    @Test
    fun `answerCallbacks onStatus updates floatingStatus and message`() {
        viewModel.answerCallbacks.onStatus(FloatingStatus.GettingAnswer, "获取答案中...")

        assertEquals(FloatingStatus.GettingAnswer, viewModel.floatingStatus.value)
        assertEquals("获取答案中...", viewModel.statusMessage.value)
    }

    @Test
    fun `answerCallbacks onError shows error to user`() {
        viewModel.answerCallbacks.onError("网络错误")

        verify { mockCtx.showErrorToUser("网络错误") }
    }

    @Test
    fun `answerCallbacks onToast shows toast`() {
        viewModel.answerCallbacks.onToast("复制成功")

        verify { mockCtx.showToast("复制成功") }
    }

    // ── recordingCallbacks ──

    @Test
    fun `recordingCallbacks onProgressUpdate updates status`() {
        every { mockCtx.getString(any(), any<Int>(), any<Int>()) } returns "处理中 (3/8)"

        viewModel.recordingCallbacks.onProgressUpdate(3, 8)

        assertEquals("处理中 (3/8)", viewModel.statusMessage.value)
        assertEquals(3, viewModel.recordingProcessedCount.value)
    }

    @Test
    fun `recordingCallbacks onError shows error`() {
        viewModel.recordingCallbacks.onError("录制失败")

        verify { mockCtx.showErrorToUser("录制失败") }
    }

    // ── captureCallbacks ──

    @Test
    fun `captureCallbacks isRecording reflects viewModel state`() {
        viewModel.isRecording.value = true
        assertTrue(viewModel.captureCallbacks.isRecording())

        viewModel.isRecording.value = false
        assertFalse(viewModel.captureCallbacks.isRecording())
    }

    @Test
    fun `captureCallbacks getCropMode reads from viewModel`() {
        viewModel.cropMode = "each"
        assertEquals("each", viewModel.captureCallbacks.getCropMode())
    }

    @Test
    fun `captureCallbacks setHasContent updates both viewModel and context`() {
        viewModel.captureCallbacks.setHasContent(true)

        assertTrue(viewModel.hasContent)
        verify { mockCtx.setHasContent(true) }
    }

    @Test
    fun `captureCallbacks setShowAnswer updates showAnswer`() {
        viewModel.captureCallbacks.setShowAnswer(true)

        assertTrue(viewModel.showAnswer.value)
    }

    @Test
    fun `captureCallbacks setCaptureInProgress toggles flag`() {
        viewModel.captureCallbacks.setCaptureInProgress(true)
        assertTrue(viewModel.captureInProgress)

        viewModel.captureCallbacks.setCaptureInProgress(false)
        assertFalse(viewModel.captureInProgress)
    }

    @Test
    fun `captureCallbacks incRecordingCaptureCount increments counter`() {
        assertEquals(0, viewModel.recordingCaptureCount.value)

        val count = viewModel.captureCallbacks.incRecordingCaptureCount()
        assertEquals(1, count)
        assertEquals(1, viewModel.recordingCaptureCount.value)
    }
}
