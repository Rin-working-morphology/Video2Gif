package com.example.video2gif.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 时间码：mm:ss.cc，超过一小时显示 h:mm:ss.cc。 */
fun formatTimecode(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val totalSeconds = safe / 1000
    val centis = (safe % 1000) / 10
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    } else {
        String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}

/** 播放器与摘要使用的短时长：4.0 秒 / 1 分 12 秒。 */
fun formatDuration(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    if (safe < 60_000) {
        val seconds = safe / 1000.0
        return String.format(Locale.ROOT, "%.1f 秒", seconds)
    }
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1000
    return "$minutes 分 $seconds 秒"
}

/** 体积按 1024 进制显示，保留一位小数，避免展示无依据精度。 */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) {
        return if (kb < 10) String.format(Locale.ROOT, "%.1f KB", kb) else "${kb.roundToLong()} KB"
    }
    val mb = kb / 1024.0
    if (mb < 10) return String.format(Locale.ROOT, "%.1f MB", mb)
    if (mb < 100) return String.format(Locale.ROOT, "%.0f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.ROOT, "%.1f GB", gb)
}

fun formatSpeed(speed: Float): String {
    val value = if (abs(speed - speed.roundToLong()) < 0.01f) {
        speed.roundToLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", speed)
    }
    return "$value×"
}

fun formatEstimateRange(center: Long, low: Long, high: Long): String =
    "${formatBytes(low)} – ${formatBytes(high)}"

fun formatResolution(width: Int, height: Int): String = "$width × $height"

fun formatLoop(loop: LoopMode, count: Int): String = when (loop) {
    LoopMode.Infinite -> "无限循环"
    LoopMode.Once -> "播放一次"
    LoopMode.Custom -> "播放 $count 次"
}

fun formatFrameCount(frames: Int): String = "$frames 帧"
