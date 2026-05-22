package com.hwb.aianswerer.models

import android.graphics.PointF

/**
 * 裁剪矩形数据类
 * 简化版：只存储左上角和右下角坐标（相对于原始图片尺寸）
 * 注意：坐标仅在运行时内存中保持，不进行持久化
 */
data class CropRect(
    val topLeft: PointF,
    val bottomRight: PointF
) {
    /**
     * 获取矩形的宽度
     */
    val width: Float
        get() = bottomRight.x - topLeft.x

    /**
     * 获取矩形的高度
     */
    val height: Float
        get() = bottomRight.y - topLeft.y

    /**
     * 验证坐标是否有效
     */
    fun isValid(imageWidth: Int, imageHeight: Int): Boolean {
        return topLeft.x >= 0 && topLeft.x < bottomRight.x &&
                topLeft.y >= 0 && topLeft.y < bottomRight.y &&
                bottomRight.x <= imageWidth && bottomRight.y <= imageHeight &&
                width > 0 && height > 0
    }

    companion object {
        /**
         * 创建默认的裁剪矩形（覆盖整个图片的80%中心区域）
         */
        fun createDefault(imageWidth: Int, imageHeight: Int): CropRect {
            val margin = 0.1f
            val left = imageWidth * margin
            val top = imageHeight * margin
            val right = imageWidth * (1 - margin)
            val bottom = imageHeight * (1 - margin)

            return CropRect(
                topLeft = PointF(left, top),
                bottomRight = PointF(right, bottom)
            )
        }
    }
}

