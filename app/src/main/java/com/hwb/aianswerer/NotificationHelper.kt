package com.hwb.aianswerer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hwb.aianswerer.utils.AppLog

/**
 * 通知栏工具 — 纯粹的通知创建，不涉及答题业务逻辑。
 *
 * 三个方法：
 *   createChannel   — 创建通知渠道（Android 8.0+）
 *   ensurePermission — 检查通知权限（Android 13+）
 *   buildNotification — 构建前台服务通知
 */
object NotificationHelper {

    /** 创建通知渠道 */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = name
                setSound(null, null)   // 禁用启动音效，前台服务只需可见不需要发声
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Android 13+ 检查通知权限（缺少可能导致前台服务被杀） */
    fun ensurePermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                AppLog.w("FWS", "Notifications disabled on API 33+; foreground service may be killed")
            }
        }
    }

    /** 构建前台服务通知（含停止按钮） */
    fun buildNotification(context: Context, isStealth: Boolean = false): Notification {
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, FloatingWindowService::class.java)
                .setAction(FloatingWindowService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                if (isStealth) context.getString(R.string.app_name)
                else context.getString(R.string.notification_content)
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.stop_service),
                stopIntent
            )
            .setOngoing(true)
            .build()
    }
}
