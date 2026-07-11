package com.hwb.aianswerer.utils

import android.graphics.Bitmap
import com.hwb.aianswerer.models.CropRect
import kotlin.math.min

/**
 * 图片裁剪工具类
 * 提供基于矩形坐标的图片裁剪功能
 */
object ImageCropUtil {

    /**
     * 根据矩形坐标裁剪图片
     * @param bitmap 原始图片
     * @param cropRect 裁剪矩形（坐标相对于原始图片尺寸）
     * @return 裁剪后的图片
     */
    fun cropBitmap(bitmap: Bitmap, cropRect: CropRect): Bitmap {
        // 验证坐标有效性
        if (!cropRect.isValid(bitmap.width, bitmap.height)) {
            throw IllegalArgumentException("Invalid crop coordinates")
        }

        val left = cropRect.topLeft.x.toInt().coerceIn(0, bitmap.width)
        val top = cropRect.topLeft.y.toInt().coerceIn(0, bitmap.height)
        val right = cropRect.bottomRight.x.toInt().coerceIn(0, bitmap.width)
        val bottom = cropRect.bottomRight.y.toInt().coerceIn(0, bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid crop dimensions")
        }

        // 使用简单的矩形裁剪（性能最优）
        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    /**
     * 保存 Bitmap 到临时文件
     * @param bitmap 要保存的图片
     * @param cacheDir 缓存目录
     * @return 临时文件路径
     */
    fun saveBitmapToTempFile(bitmap: Bitmap, cacheDir: java.io.File): String {
        val tempFile = java.io.File(cacheDir, "temp_crop_${System.currentTimeMillis()}.jpg")
        tempFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return tempFile.absolutePath
    }

    /**
     * 从文件加载 Bitmap
     * @param filePath 文件路径
     * @return Bitmap 对象
     */
    fun loadBitmapFromFile(filePath: String): Bitmap {
        return android.graphics.BitmapFactory.decodeFile(filePath)
            ?: throw IllegalArgumentException("Failed to load bitmap from file: $filePath")
    }

    /**
     * 删除临时文件
     * @param filePath 文件路径
     */
    fun deleteTempFile(filePath: String) {
        try {
            java.io.File(filePath).delete()
        } catch (e: Exception) {
            AppLog.e("删除临时文件失败: $filePath", e)
        }
    }

    /**
     * 计算适合屏幕显示的缩放比例
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 缩放比例
     */
    fun calculateFitScale(
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Float {
        val scaleX = screenWidth.toFloat() / imageWidth
        val scaleY = screenHeight.toFloat() / imageHeight
        return min(scaleX, scaleY)
    }
}

