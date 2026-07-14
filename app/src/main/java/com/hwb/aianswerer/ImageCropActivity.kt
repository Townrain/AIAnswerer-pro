package com.hwb.aianswerer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.ui.components.AnimatedButton
import com.hwb.aianswerer.ui.components.ButtonVariant
import com.hwb.aianswerer.ui.components.CardRadius
import com.hwb.aianswerer.ui.components.GlassInfoCard
import com.hwb.aianswerer.ui.components.TouchMin
import com.hwb.aianswerer.ui.theme.AIAnswererTheme
import com.hwb.aianswerer.ui.theme.*
import com.hwb.aianswerer.utils.AppLog
import com.hwb.aianswerer.utils.ImageCropUtil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 图片裁剪 Activity — 全屏 Canvas 交互裁剪。
 *
 * 坐标系统：
 *   屏幕坐标（Canvas）→ 用于拖拽和渲染
 *   图片坐标（原始像素）→ 用于最终裁剪
 *   两者通过 imageScale 和 displayLeft/displayTop 偏移相互转换。
 *
 * 四角拖拽：
 *   draggingCorner 枚举 0=左上 1=右下 2=右上 3=左下。
 *   每个角有独立的移动范围和方向约束（左上角向右下、右下角向左上、交叉角分别控制）。
 *   热区半径为 60f 像素。
 */
class ImageCropActivity : BaseActivity() {

    private var imagePath: String? = null
    private var bitmap: Bitmap? = null
    private var fileSentToService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 获取图片路径
        imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath == null) {
            finish()
            return
        }

        // 加载图片
        try {
            bitmap = imagePath?.let { ImageCropUtil.loadBitmapFromFile(it) }
        } catch (e: Exception) {
            AppLog.e("加载图片失败", e)
            finish()
            return
        }

        // 获取上一次的裁剪坐标（如果有）
        val previousCropRect = if (intent.hasExtra(EXTRA_PREVIOUS_TOP_LEFT_X)) {
            CropRect(
                topLeft = PointF(
                    intent.getFloatExtra(EXTRA_PREVIOUS_TOP_LEFT_X, 0f),
                    intent.getFloatExtra(EXTRA_PREVIOUS_TOP_LEFT_Y, 0f)
                ),
                bottomRight = PointF(
                    intent.getFloatExtra(EXTRA_PREVIOUS_BOTTOM_RIGHT_X, 0f),
                    intent.getFloatExtra(EXTRA_PREVIOUS_BOTTOM_RIGHT_Y, 0f)
                )
            )
        } else {
            null
        }

        val currentBitmap = bitmap
        if (currentBitmap == null) {
            finish()
            return
        }

        setContent {
            AIAnswererTheme {
                ImageCropScreen(
                    bitmap = currentBitmap,
                    previousCropRect = previousCropRect,
                    onConfirm = { cropRect ->
                        // 通过广播返回裁剪坐标
                        fileSentToService = true
                        val broadcastIntent =
                            Intent(FloatingWindowService.ACTION_CROP_RESULT).apply {
                                setPackage(packageName)
                                putExtra(FloatingWindowService.EXTRA_IMAGE_PATH, imagePath)
                                putExtra(EXTRA_TOP_LEFT_X, cropRect.topLeft.x)
                                putExtra(EXTRA_TOP_LEFT_Y, cropRect.topLeft.y)
                                putExtra(EXTRA_BOTTOM_RIGHT_X, cropRect.bottomRight.x)
                                putExtra(EXTRA_BOTTOM_RIGHT_Y, cropRect.bottomRight.y)
                            }
                        sendBroadcast(broadcastIntent)
                        finish()
                    },
                    onCancel = {
                        // 取消时删除临时文件
                        imagePath?.let { ImageCropUtil.deleteTempFile(it) }
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap = null

        // 仅在文件未发送给 Service 时清理（取消/异常退出）
        // 若已发送，由 Service 负责清理
        if (!fileSentToService) {
            imagePath?.let { ImageCropUtil.deleteTempFile(it) }
        }
    }

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_TOP_LEFT_X = "top_left_x"
        const val EXTRA_TOP_LEFT_Y = "top_left_y"
        const val EXTRA_BOTTOM_RIGHT_X = "bottom_right_x"
        const val EXTRA_BOTTOM_RIGHT_Y = "bottom_right_y"
        const val EXTRA_PREVIOUS_TOP_LEFT_X = "previous_top_left_x"
        const val EXTRA_PREVIOUS_TOP_LEFT_Y = "previous_top_left_y"
        const val EXTRA_PREVIOUS_BOTTOM_RIGHT_X = "previous_bottom_right_x"
        const val EXTRA_PREVIOUS_BOTTOM_RIGHT_Y = "previous_bottom_right_y"
    }
}

// Crop layout constants
private const val CROP_BOTTOM_BUTTON_HEIGHT_DP = 100f
private const val CROP_TOP_MARGIN_DP = 80f
private const val CROP_MIN_CORNER_DISTANCE = 50f
private const val CROP_INITIAL_MARGIN_FACTOR = 0.1f
private const val CROP_IMAGE_SCALE_FACTOR = 0.9f

@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    previousCropRect: CropRect? = null,
    onConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit
) {
    val isDark = LocalIsDarkMode.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val imageWidth = bitmap.width
    val imageHeight = bitmap.height

    val density = LocalDensity.current
    val bottomButtonHeight = with(density) { CROP_BOTTOM_BUTTON_HEIGHT_DP.dp.toPx() }
    val displayTop = with(density) { CROP_TOP_MARGIN_DP.dp.toPx() }
    val touchRadius = with(density) { TouchMin.toPx() }
    val spacingXxxlPx = with(density) { Spacing.xxxl.toPx() }

    val imageScale = remember(canvasSize.width, canvasSize.height) {
        if (canvasSize.width > 0 && canvasSize.height > 0) {
            val availableHeight = canvasSize.height - bottomButtonHeight - spacingXxxlPx
            val scaleX = canvasSize.width.toFloat() / imageWidth
            val scaleY = availableHeight / imageHeight
            min(scaleX, scaleY) * CROP_IMAGE_SCALE_FACTOR
        } else {
            1f
        }
    }

    val displayWidth = remember(imageScale) { imageWidth * imageScale }
    val displayHeight = remember(imageScale) { imageHeight * imageScale }
    val displayLeft = remember(canvasSize.width, displayWidth) {
        (canvasSize.width - displayWidth) / 2
    }

    var topLeft by remember { mutableStateOf(PointF(0f, 0f)) }
    var bottomRight by remember { mutableStateOf(PointF(0f, 0f)) }
    var draggingCorner by remember { mutableStateOf(-1) }

    val isInitialized = remember { mutableStateOf(false) }
    if (canvasSize.width > 0 && displayWidth > 0 && !isInitialized.value) {
        if (previousCropRect != null && previousCropRect.isValid(imageWidth, imageHeight)) {
            topLeft = PointF(
                displayLeft + previousCropRect.topLeft.x * imageScale,
                displayTop + previousCropRect.topLeft.y * imageScale
            )
            bottomRight = PointF(
                displayLeft + previousCropRect.bottomRight.x * imageScale,
                displayTop + previousCropRect.bottomRight.y * imageScale
            )
        } else {
            val margin = min(displayWidth, displayHeight) * CROP_INITIAL_MARGIN_FACTOR
            topLeft = PointF(displayLeft + margin, displayTop + margin)
            bottomRight = PointF(
                displayLeft + displayWidth - margin,
                displayTop + displayHeight - margin
            )
        }
        isInitialized.value = true
    }

    fun screenToImageCoords(point: PointF): PointF {
        val imageX = ((point.x - displayLeft) / imageScale).coerceIn(0f, imageWidth.toFloat())
        val imageY = ((point.y - displayTop) / imageScale).coerceIn(0f, imageHeight.toFloat())
        return PointF(imageX, imageY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumBgDark)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(displayLeft, displayWidth, displayTop, displayHeight) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val touchX = offset.x
                            val touchY = offset.y
                            val topLeftPos = topLeft
                            val bottomRightPos = bottomRight
                            val topRightPos = PointF(bottomRight.x, topLeft.y)
                            val bottomLeftPos = PointF(topLeft.x, bottomRight.y)

                            val topLeftDist = sqrt(
                                (touchX - topLeftPos.x) * (touchX - topLeftPos.x) +
                                        (touchY - topLeftPos.y) * (touchY - topLeftPos.y)
                            )
                            val bottomRightDist = sqrt(
                                (touchX - bottomRightPos.x) * (touchX - bottomRightPos.x) +
                                        (touchY - bottomRightPos.y) * (touchY - bottomRightPos.y)
                            )
                            val topRightDist = sqrt(
                                (touchX - topRightPos.x) * (touchX - topRightPos.x) +
                                        (touchY - topRightPos.y) * (touchY - topRightPos.y)
                            )
                            val bottomLeftDist = sqrt(
                                (touchX - bottomLeftPos.x) * (touchX - bottomLeftPos.x) +
                                        (touchY - bottomLeftPos.y) * (touchY - bottomLeftPos.y)
                            )

                            draggingCorner = when {
                                topLeftDist < touchRadius -> 0
                                bottomRightDist < touchRadius -> 1
                                topRightDist < touchRadius -> 2
                                bottomLeftDist < touchRadius -> 3
                                else -> -1
                            }
                        },
                        onDrag = { change, dragAmount ->
                            when (draggingCorner) {
                                0 -> {
                                    topLeft = PointF(
                                        (topLeft.x + dragAmount.x).coerceIn(displayLeft, bottomRight.x - CROP_MIN_CORNER_DISTANCE),
                                        (topLeft.y + dragAmount.y).coerceIn(displayTop, bottomRight.y - CROP_MIN_CORNER_DISTANCE)
                                    )
                                    change.consume()
                                }
                                1 -> {
                                    bottomRight = PointF(
                                        (bottomRight.x + dragAmount.x).coerceIn(topLeft.x + CROP_MIN_CORNER_DISTANCE, displayLeft + displayWidth),
                                        (bottomRight.y + dragAmount.y).coerceIn(topLeft.y + CROP_MIN_CORNER_DISTANCE, displayTop + displayHeight)
                                    )
                                    change.consume()
                                }
                                2 -> {
                                    val newX = (bottomRight.x + dragAmount.x).coerceIn(topLeft.x + CROP_MIN_CORNER_DISTANCE, displayLeft + displayWidth)
                                    val newY = (topLeft.y + dragAmount.y).coerceIn(displayTop, bottomRight.y - CROP_MIN_CORNER_DISTANCE)
                                    bottomRight = PointF(newX, bottomRight.y)
                                    topLeft = PointF(topLeft.x, newY)
                                    change.consume()
                                }
                                3 -> {
                                    val newX = (topLeft.x + dragAmount.x).coerceIn(displayLeft, bottomRight.x - CROP_MIN_CORNER_DISTANCE)
                                    val newY = (bottomRight.y + dragAmount.y).coerceIn(topLeft.y + CROP_MIN_CORNER_DISTANCE, displayTop + displayHeight)
                                    topLeft = PointF(newX, topLeft.y)
                                    bottomRight = PointF(bottomRight.x, newY)
                                    change.consume()
                                }
                            }
                        },
                        onDragEnd = { draggingCorner = -1 }
                    )
                }
        ) {
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(displayLeft.toInt(), displayTop.toInt()),
                dstSize = IntSize(displayWidth.toInt(), displayHeight.toInt())
            )

            val cropLeft = topLeft.x.coerceIn(displayLeft, displayLeft + displayWidth)
            val cropTop = topLeft.y.coerceIn(displayTop, displayTop + displayHeight)
            val cropRight = bottomRight.x.coerceIn(displayLeft, displayLeft + displayWidth)
            val cropBottom = bottomRight.y.coerceIn(displayTop, displayTop + displayHeight)

            val maskColor = PremiumBgDark.copy(alpha = 0.60f)

            if (cropTop > displayTop) {
                drawRect(color = maskColor, topLeft = Offset(displayLeft, displayTop), size = Size(displayWidth, cropTop - displayTop))
            }
            if (cropBottom < displayTop + displayHeight) {
                drawRect(color = maskColor, topLeft = Offset(displayLeft, cropBottom), size = Size(displayWidth, displayTop + displayHeight - cropBottom))
            }
            if (cropLeft > displayLeft) {
                drawRect(color = maskColor, topLeft = Offset(displayLeft, cropTop), size = Size(cropLeft - displayLeft, cropBottom - cropTop))
            }
            if (cropRight < displayLeft + displayWidth) {
                drawRect(color = maskColor, topLeft = Offset(cropRight, cropTop), size = Size(displayLeft + displayWidth - cropRight, cropBottom - cropTop))
            }

            val strokeWidth = Spacing.xs.toPx()
            val dashLength = (Spacing.md + Spacing.xs).toPx()
            drawRect(
                color = PremiumPrimaryVariant,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength), 0f))
            )

            val cornerRadius = Spacing.md.toPx()
            val cornerColor = PremiumPrimaryVariant
            val cornerStrokeColor = PremiumCardLight

            listOf(
                Offset(cropLeft, cropTop),
                Offset(cropRight, cropBottom),
                Offset(cropRight, cropTop),
                Offset(cropLeft, cropBottom)
            ).forEach { center ->
                drawCircle(color = cornerStrokeColor, radius = cornerRadius + Spacing.xs.toPx(), center = center)
                drawCircle(color = cornerColor, radius = cornerRadius, center = center)
            }
        }

        // Top instruction card
        GlassInfoCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(Spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.crop_instruction),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextDarkPrimary
            )
        }

        // Bottom button bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadowElevated(CardRadius)
                    .then(
                        if (isDark) Modifier.glassSurfaceDark(alpha = 0.07f, shadowElevation = 0f)
                        else Modifier.glassOverlay()
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    AnimatedButton(
                        text = stringResource(R.string.crop_cancel),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Glass
                    )
                    AnimatedButton(
                        text = stringResource(R.string.crop_reset),
                        onClick = {
                            val margin = min(displayWidth, displayHeight) * CROP_INITIAL_MARGIN_FACTOR
                            topLeft = PointF(displayLeft + margin, displayTop + margin)
                            bottomRight = PointF(displayLeft + displayWidth - margin, displayTop + displayHeight - margin)
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Tonal
                    )
                    AnimatedButton(
                        text = stringResource(R.string.crop_confirm),
                        onClick = {
                            val cropRect = CropRect(
                                topLeft = screenToImageCoords(topLeft),
                                bottomRight = screenToImageCoords(bottomRight)
                            )
                            onConfirm(cropRect)
                        },
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Primary
                    )
                }
            }
        }
    }
}
