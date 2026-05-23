package com.hwb.aianswerer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.hwb.aianswerer.utils.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 截图管理器。
 *
 * MediaProjection 生命周期：
 *   调用方通过 createScreenCaptureIntent() 获取权限 Intent，拿到 result 后
 *   调用 initMediaProjection() 初始化。initMediaProjection 会先 release 旧实例再重建，
 *   确保 MediaProjection 与最新的权限数据绑定。
 *
 * VirtualDisplay 复用策略：
 *   VirtualDisplay 和 ImageReader 在首次截图时创建并保持存活，不再每次截图都重建，
 *   避免了频繁创建/销毁 VirtualDisplay 的开销和可能的闪烁。
 *   仅在 MediaProjection stop 回调或主动 release() 时清理。
 */
class ScreenCaptureManager(private val context: Context) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val projectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    /**
     * 创建截图Intent，用于请求权限
     */
    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    // 保存权限数据，用于在MediaProjection被释放后重新创建
    private var savedResultCode: Int? = null
    private var savedData: Intent? = null
    private val captureMutex = Mutex()

    /**
     * 初始化MediaProjection（保存权限数据）
     */
    fun initMediaProjection(resultCode: Int, data: Intent?) {
        // 保存权限数据
        savedResultCode = resultCode
        savedData = data

        // 清理旧的MediaProjection
        release()

        // 创建新的MediaProjection
        createMediaProjection()
    }

    /**
     * 创建MediaProjection实例（只创建一次）
     */
    private fun createMediaProjection() {
        val resultCode = savedResultCode
        val data = savedData
        if (resultCode == null || data == null) {
            return
        }

        try {
            val projection = projectionManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                AppLog.e("getMediaProjection 返回 null，权限可能已失效")
                mediaProjection = null
                return
            }
            mediaProjection = projection.also {
                // 注册回调以管理资源（Android 14+强制要求，但所有版本都支持）
                it.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        // MediaProjection停止时清理所有资源
                        cleanUpVirtualDisplay()
                    }
                }, Handler(Looper.getMainLooper()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("创建MediaProjection失败", e)
            mediaProjection = null
        }
    }

    /**
     * 执行截图
     */
    suspend fun captureScreen(): Bitmap = captureMutex.withLock {
        withTimeout(10_000L) {
        suspendCancellableCoroutine { continuation ->
        try {
            // 检查MediaProjection是否已初始化
            if (mediaProjection == null) {
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(Exception("MediaProjection未初始化，请先授权截图权限"))
                }
                return@suspendCancellableCoroutine
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // 复用已有的VirtualDisplay和ImageReader，避免重复创建造成资源浪费
            if (virtualDisplay == null || imageReader == null) {
                // maxImages=2: 允许双缓冲，避免截图时阻塞
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                // VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR: 自动镜像显示内容
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    null
                )

                // virtualDisplay创建失败时立即抛出异常，避免continuation永远等待
                if (virtualDisplay == null) {
                    imageReader?.close()
                    imageReader = null
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(Exception("VirtualDisplay创建失败，MediaProjection可能已失效"))
                    }
                    return@suspendCancellableCoroutine
                }
            }

            // 清除 ImageReader 缓冲区中的旧帧，避免 acquireLatestImage 返回过时画面
            while (true) {
                val old = imageReader?.acquireLatestImage() ?: break
                old.close()
            }

            // 移除旧的listener，避免泄漏
            imageReader?.setOnImageAvailableListener(null, null)

            // 设置图像可用监听器（每次截图都设置，确保回调正确）
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image: Image? = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image, width, height)
                        image.close()

                        // 不清理VirtualDisplay，保留它供下次使用

                        if (!continuation.isCompleted) {
                            continuation.resume(bitmap)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!continuation.isCompleted) {
                        continuation.resumeWithException(e)
                    }
                }
            }, null)

            // 设置取消回调
            continuation.invokeOnCancellation {
                // 取消时也不清理，保留VirtualDisplay
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!continuation.isCompleted) {
                continuation.resumeWithException(e)
            }
        }
        }
        }
    }

    /**
     * Image → Bitmap 转换。
     * 某些设备上 rowStride > pixelStride * width（即行末尾有 padding），
     * 需要先以原始 stride 创建 Bitmap 再裁剪掉 padding 区域。
     */
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        // rowPadding: 每行末尾的填充字节数
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            bitmap
        } else {
            // 裁剪掉行填充部分，得到精确的屏幕截图
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            // 回收原始宽bitmap，避免内存泄漏
            bitmap.recycle()
            croppedBitmap
        }
    }

    /**
     * 清理VirtualDisplay和ImageReader（保留MediaProjection）
     * 使用captureMutex确保不会与captureScreen()并发执行
     */
    private suspend fun cleanUpVirtualDisplaySafely() {
        captureMutex.withLock {
            cleanUpVirtualDisplay()
        }
    }

    private fun cleanUpVirtualDisplay() {
        virtualDisplay?.release()
        imageReader?.close()
        virtualDisplay = null
        imageReader = null
    }

    /**
     * 释放所有资源（包括MediaProjection和保存的权限数据）
     * 使用captureMutex确保不会与captureScreen()并发执行
     */
    suspend fun releaseSafely() {
        captureMutex.withLock {
            release()
        }
    }

    /**
     * 释放所有资源（包括MediaProjection和保存的权限数据）
     * 注意：此方法无锁保护，仅在确保没有并发截图时调用
     */
    fun release() {
        cleanUpVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        // 不清理savedResultCode和savedData，以便重新创建
    }

    /**
     * 完全清理（包括权限数据）
     */
    fun releaseAll() {
        release()
        savedResultCode = null
        savedData = null
    }
}

