package com.example.video2gif.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.os.Build
import com.example.video2gif.domain.SourceVideo
import kotlinx.coroutines.ensureActive
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MediaCodec 按 PTS 顺序解码。仅保留目标附近两帧；后退或大跨度跳转才 seek。
 * 输出 Image 不应用容器旋转，在这里明确旋转一次，包括 180° 和方形视频。
 * Bitmap 长边最多 2560；两张缓存最坏为 50 MiB，不随片长增长。
 */
class FrameDecoder(private val context: Context, private val uri: Uri) : AutoCloseable {
    private val lock = Any()
    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var closed = false
    private var inputEnded = false
    private var outputEnded = false
    private var lastRequest = -1L
    private var requestedSide = 0
    private var before: TimedFrame? = null
    private var after: TimedFrame? = null
    private var outputFormat: MediaFormat? = null
    private data class TimedFrame(val pts: Long, val bitmap: Bitmap)

    private fun open() {
        if (codec != null) return
        val media = MediaExtractor()
        extractor = media
        media.setDataSource(context, uri, null)
        val track = (0 until media.trackCount).firstOrNull {
            media.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: throw IOException("来源没有视频轨道")
        media.selectTrack(track)
        val format = media.getTrackFormat(track)
        val transfer = if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
        if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) {
            if (Build.VERSION.SDK_INT < 31) throw IOException("此设备暂不支持 HDR 视频的 SDR 色调映射")
            format.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        format.setInteger(MediaFormat.KEY_ROTATION, 0)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec = decoder
        decoder.configure(format, null, null, 0)
        if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) {
            val accepted = decoder.inputFormat
            if (!accepted.containsKey(MediaFormat.KEY_COLOR_TRANSFER_REQUEST) ||
                accepted.getInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST) != MediaFormat.COLOR_TRANSFER_SDR_VIDEO) {
                throw IOException("此设备解码器不支持 HDR 色调映射")
            }
        }
        decoder.start()
        outputFormat = format
    }

    suspend fun decode(timeMs: Long, source: SourceVideo, requestLongSide: Int): Bitmap? {
        val task = coroutineContext
        return synchronized(lock) {
            if (closed) return@synchronized null
            task.ensureActive()
            open()
            val media = extractor!!
            val decoder = codec!!
            val target = timeMs.coerceAtLeast(0) * 1000L
            val side = requestLongSide.coerceIn(2, 2560)
            if (lastRequest < 0 || target < lastRequest || target - lastRequest > 1_000_000L || requestedSide != side) {
                clearFrames()
                media.seekTo(target, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                decoder.flush()
                inputEnded = false
                outputEnded = false
            }
            requestedSide = side
            lastRequest = target
            val info = MediaCodec.BufferInfo()
            var lastProgress = SystemClock.elapsedRealtime()
            while (!outputEnded && (after == null || after!!.pts < target)) {
                task.ensureActive()
                if (SystemClock.elapsedRealtime() - lastProgress > 5_000) {
                    throw IOException("视频解码超时，请重试或重新选择视频")
                }
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex) ?: throw IOException("解码器输入不可用")
                        val size = media.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, media.sampleTime, 0)
                            media.advance()
                        }
                        lastProgress = SystemClock.elapsedRealtime()
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outputFormat = decoder.outputFormat
                if (index >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bitmap = decoder.getOutputImage(index)?.use { image ->
                                imageToBitmap(image, side, outputFormat!!, source.rotationDegrees) { task.ensureActive() }
                            } ?: throw IOException("此设备解码器不提供可读取的像素帧")
                            before?.bitmap?.recycle()
                            before = after
                            after = TimedFrame(info.presentationTimeUs, bitmap)
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        decoder.releaseOutputBuffer(index, false)
                    }
                    lastProgress = SystemClock.elapsedRealtime()
                }
            }
            val right = after
            val left = before
            val chosen = if (left != null && (right == null || abs(target - left.pts) <= abs(right.pts - target))) left else right
            // 调用方会裁剪、旋转并回收，不能把缓存所有权交出去。
            chosen?.bitmap?.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun clearFrames() {
        before?.bitmap?.recycle()
        after?.bitmap?.recycle()
        before = null
        after = null
    }

    override fun close() = synchronized(lock) {
        if (!closed) {
            closed = true
            clearFrames()
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
            codec = null
            extractor = null
        }
    }
}

/** 读取实际 plane stride/crop；逐行转换避免分配源分辨率 RGBA 帧。 */
private fun imageToBitmap(image: Image, longSide: Int, format: MediaFormat, rotation: Int, checkActive: () -> Unit): Bitmap {
    if (image.format != ImageFormat.YUV_420_888) throw IOException("解码器像素格式暂不支持：${image.format}")
    val crop = image.cropRect
    val scale = minOf(1.0, longSide.toDouble() / max(crop.width(), crop.height()))
    val width = (crop.width() * scale).roundToInt().coerceAtLeast(2)
    val height = (crop.height() * scale).roundToInt().coerceAtLeast(2)
    val planes = image.planes
    val buffers = planes.map { it.buffer.duplicate() }
    fun sample(plane: Int, x: Int, y: Int): Int {
        val p = planes[plane]
        val b = buffers[plane]
        return b.get(b.position() + y * p.rowStride + x * p.pixelStride).toInt() and 255
    }
    fun value(key: String, default: Int) = if (format.containsKey(key)) format.getInteger(key) else default
    val fullRange = value(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED) == MediaFormat.COLOR_RANGE_FULL
    val standard = value(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
    val (kr, kb) = when (standard) {
        MediaFormat.COLOR_STANDARD_BT709 -> 0.2126 to 0.0722
        MediaFormat.COLOR_STANDARD_BT2020 -> 0.2627 to 0.0593
        else -> 0.299 to 0.114
    }
    val kg = 1.0 - kr - kb
    val row = IntArray(width)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        for (y in 0 until height) {
            checkActive()
            val sy = crop.top + (y * crop.height() / height)
            for (x in 0 until width) {
                val sx = crop.left + (x * crop.width() / width)
                val luma = sample(0, sx, sy)
                val yy = if (fullRange) luma.toDouble() else (luma - 16) * 255.0 / 219.0
                val uvScale = if (fullRange) 1.0 else 255.0 / 224.0
                val u = (sample(1, sx / 2, sy / 2) - 128) * uvScale
                val v = (sample(2, sx / 2, sy / 2) - 128) * uvScale
                val r = (yy + 2 * (1 - kr) * v).roundToInt().coerceIn(0, 255)
                val b = (yy + 2 * (1 - kb) * u).roundToInt().coerceIn(0, 255)
                val g = (yy - 2 * kb * (1 - kb) / kg * u - 2 * kr * (1 - kr) / kg * v).roundToInt().coerceIn(0, 255)
                row[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(row, 0, width, 0, y, width, 1)
        }
        return ImageOps.applyRotation(bitmap, rotation)
    } catch (t: Throwable) {
        bitmap.recycle()
        throw t
    }
}
