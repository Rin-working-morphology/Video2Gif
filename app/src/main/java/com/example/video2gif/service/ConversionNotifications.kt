package com.example.video2gif.service

import android.app.Notification
import android.app.NotificationChannel
import android.os.Build
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.video2gif.MainActivity
import com.example.video2gif.R
import com.example.video2gif.domain.JobStatus
import com.example.video2gif.domain.RunState

object ConversionNotifications {
    const val CHANNEL_ID = "conversion"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "转换进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示 GIF 转换进度与取消入口"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: RunState?): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_video2gif)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (state == null) {
            return builder
                .setContentTitle("准备转换")
                .setProgress(0, 0, true)
                .setOngoing(true)
                .build()
        }
        if (!state.isRunning) {
            val title = when {
                state.failedCount > 0 -> "转换完成，${state.failedCount} 个失败"
                state.canceledCount > 0 -> "转换已取消"
                state.interruptedCount > 0 -> "转换中断"
                else -> "转换完成"
            }
            return builder
                .setContentTitle(title)
                .setContentText("${state.succeededCount} 个 GIF 已保存")
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        }
        val current = state.currentItem
        val title = "正在转换 ${state.finishedCount + 1} / ${state.items.size}"
        val text = when (current?.status) {
            JobStatus.Encoding -> current.name
            JobStatus.Publishing -> "正在保存 ${current.name}"
            JobStatus.Preparing -> "正在准备 ${current.name}"
            else -> "等待开始"
        }
        builder
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, (state.totalProgress * 100).toInt().coerceIn(0, 100), false)
            .setOngoing(true)
            .addAction(
                0,
                "取消全部",
                PendingIntent.getService(
                    context,
                    1,
                    Intent(context, ConversionService::class.java)
                        .setAction(ConversionService.ACTION_CANCEL_ALL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        return builder.build()
    }

    fun notifyFinal(context: Context, state: RunState) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, build(context, state))
    }
}
