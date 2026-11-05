package com.example.video2gif.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.video2gif.domain.ConversionRecipe
import com.example.video2gif.domain.OutputSpec
import com.example.video2gif.domain.SourceVideo
import com.example.video2gif.domain.formatBytes
import com.example.video2gif.domain.sourceTimeMsForFrame
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object VideoProbe {
    /** 实际读取媒体轨道，不用扩展名推断支持。失败时返回 null。 */
    fun probe(context: Context, uri: Uri): SourceVideo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            if (duration <= 0L || width <= 0 || height <= 0) return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val normalized = ((rotation % 360) + 360) % 360
            val displayWidth = if (normalized % 180 == 90) height else width
            val displayHeight = if (normalized % 180 == 90) width else height
            val fps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                        ?.toFloatOrNull()
                        ?.takeIf { it > 1 && duration > 0 }
                        ?.let { it * 1000f / duration }
                } else {
                    null
                }
            SourceVideo(
                displayWidth = displayWidth,
                displayHeight = displayHeight,
                rotationDegrees = normalized,
                durationMs = duration,
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                nominalFrameRate = fps?.takeIf { it > 0.5f && it < 240f },
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun thumbnail(context: Context, uri: Uri, maxSize: Int = 640): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: frame.width
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: frame.height
            val oriented = ImageOps.applySourceRotation(frame, rotation, width, height)
            val long = max(oriented.width, oriented.height)
            if (long <= maxSize) oriented else {
                val scale = maxSize.toFloat() / long
                ImageOps.scale(
                    oriented,
                    (oriented.width * scale).roundToInt().coerceAtLeast(1),
                    (oriented.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/**
 * 顺序解码器。每次只向前取一个目标时间戳，不预存整段帧序列；
 * 屏幕预览、调色板抽样与正式编码共用这个解码入口。
 */
class FrameDecoder(private val context: Context, private val uri: Uri) : AutoCloseable {
    private val retriever = MediaMetadataRetriever()
    private val lock = Any()
    private var opened = false
    private var closed = false

    private fun ensureOpen() {
        if (!opened) {
            retriever.setDataSource(context, uri)
            opened = true
        }
    }

    /**
     * 解码指定源时间的一帧，并按源方向元数据转正。
     * [requestLongSide] 是显示坐标下的期望长边，用于限制解码像素预算。
     */
    fun decode(timeMs: Long, source: SourceVideo, requestLongSide: Int): Bitmap? = synchronized(lock) {
        if (closed) return null
        try {
            ensureOpen()
            // 编码帧尺寸与显示尺寸在 90°/270° 时互换，请求缩放必须按编码尺寸计算。
            val encodedWidth = if (source.rotationDegrees % 180 == 90) source.displayHeight else source.displayWidth
            val encodedHeight = if (source.rotationDegrees % 180 == 90) source.displayWidth else source.displayHeight
            val encodedLong = max(encodedWidth, encodedHeight)
            val scale = min(1f, requestLongSide.toFloat() / encodedLong.coerceAtLeast(1))
            val requestW = (encodedWidth * scale).roundToInt().coerceAtLeast(2)
            val requestH = (encodedHeight * scale).roundToInt().coerceAtLeast(2)
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    requestW,
                    requestH,
                )
            } else {
                retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
            } ?: return null
            var oriented = ImageOps.applySourceRotation(
                raw,
                source.rotationDegrees,
                encodedWidth,
                encodedHeight,
            )
            val actualLong = max(oriented.width, oriented.height)
            if (actualLong > requestLongSide) {
                val scale = requestLongSide.toFloat() / actualLong
                oriented = ImageOps.scale(
                    oriented,
                    (oriented.width * scale).roundToInt().coerceAtLeast(2),
                    (oriented.height * scale).roundToInt().coerceAtLeast(2),
                )
            }
            oriented
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            if (opened) runCatching { retriever.release() }
        }
    }
}

object ImageOps {
    /** 部分 Android 解码器会先应用旋转元数据，按帧宽高避免重复旋转。 */
    fun applySourceRotation(bitmap: Bitmap, degrees: Int, encodedWidth: Int, encodedHeight: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0 || normalized % 180 != 90) return bitmap

        val encodedRatio = encodedWidth.toFloat() / encodedHeight.coerceAtLeast(1)
        val displayRatio = 1f / encodedRatio
        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val encodedError = abs(ln(bitmapRatio / encodedRatio))
        val displayError = abs(ln(bitmapRatio / displayRatio))
        return if (displayError + 0.01f < encodedError) bitmap else applyRotation(bitmap, normalized)
    }

    fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val l = (left.coerceIn(0f, 1f) * bitmap.width).roundToInt()
        val t = (top.coerceIn(0f, 1f) * bitmap.height).roundToInt()
        val r = (right.coerceIn(0f, 1f) * bitmap.width).roundToInt()
        val b = (bottom.coerceIn(0f, 1f) * bitmap.height).roundToInt()
        val x = l.coerceIn(0, bitmap.width - 2)
        val y = t.coerceIn(0, bitmap.height - 2)
        val w = (r - x).coerceIn(2, bitmap.width - x)
        val h = (b - y).coerceIn(2, bitmap.height - y)
        if (x == 0 && y == 0 && w == bitmap.width && h == bitmap.height) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    fun scale(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** 旋转 → 裁剪 → 缩放。返回的 bitmap 由调用方负责回收。 */
    fun prepare(
        decoded: Bitmap,
        recipe: ConversionRecipe,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap {
        var bitmap = applyRotation(decoded, recipe.userRotation)
        bitmap = crop(
            bitmap,
            recipe.crop.left,
            recipe.crop.top,
            recipe.crop.right,
            recipe.crop.bottom,
        )
        bitmap = scale(bitmap, targetWidth.coerceAtLeast(1), targetHeight.coerceAtLeast(1))
        return bitmap
    }

    fun previewDims(spec: OutputSpec, maxLongSide: Int): Pair<Int, Int> {
        val long = max(spec.width, spec.height)
        if (long <= maxLongSide) return spec.width to spec.height
        val scale = maxLongSide.toFloat() / long
        return (spec.width * scale).roundToInt().coerceAtLeast(2) to
            (spec.height * scale).roundToInt().coerceAtLeast(2)
    }

    /**
     * 裁剪会丢弃画面，因此解码整帧时不能只按输出尺寸请求，否则取景框内的像素不足。
     * 这里反推需要的整帧长边；上限 1920 控制单帧内存。
     */
    fun decodeLongSideForTarget(
        source: SourceVideo,
        recipe: ConversionRecipe,
        targetWidth: Int,
        targetHeight: Int,
        cap: Int = 1920,
    ): Int {
        val rotatedW = if (recipe.userRotation % 180 == 90) source.displayHeight else source.displayWidth
        val rotatedH = if (recipe.userRotation % 180 == 90) source.displayWidth else source.displayHeight
        val cropW = (rotatedW * recipe.crop.width).coerceAtLeast(1f)
        val cropH = (rotatedH * recipe.crop.height).coerceAtLeast(1f)
        val scaleX = targetWidth / cropW
        val scaleY = targetHeight / cropH
        val scale = max(scaleX, scaleY).coerceAtMost(1f) * 1.05f
        val displayLong = max(rotatedW, rotatedH)
        return (displayLong * scale).roundToInt().coerceIn(2, min(cap, displayLong))
    }
}

data class SizeEstimate(
    val centerBytes: Long,
    val lowBytes: Long,
    val highBytes: Long,
    val analyzed: Boolean,
    val coverage: Float = 0f,
) {
    val label: String get() = "${formatBytes(lowBytes)} – ${formatBytes(highBytes)}"
}

object SizeEstimator {
    /** 分析开始前的宽区间，仅用于立即反馈，不冒充分析结果。 */
    fun quick(spec: OutputSpec, colorCount: Int): SizeEstimate {
        val bitsPerPixel = 0.62 * ln(colorCount.toDouble()) / ln(2.0)
        val center = (spec.pixelCount.toDouble() * spec.frameCount * bitsPerPixel / 8.0).toLong()
        return SizeEstimate(
            centerBytes = center.coerceAtLeast(2048),
            lowBytes = (center * 0.62).toLong().coerceAtLeast(1024),
            highBytes = (center * 1.65).toLong().coerceAtLeast(2048),
            analyzed = false,
        )
    }

    /**
     * 分析阶段估算：用与正式编码相同的调色板和抖动，抽样连续输出帧，
     * 计算索引色熵与变化量，再按像素与帧数外推。全程不执行 LZW。
     */
    fun fromSamples(
        spec: OutputSpec,
        colorCount: Int,
        averageEntropyBits: Double,
        averageAdjacentEqual: Double,
        averageChangedRatio: Double,
        sampledWindows: Int,
        totalWindows: Int,
    ): SizeEstimate {
        // LZW 的实际码率同时取决于符号分布（熵）与空间重复。
        // 系数由有序抖动输出与实际 LZW 结果对照得到：相邻相等比例越高，码率越低。
        val compression = (0.98 - 0.94 * averageAdjacentEqual.coerceIn(0.0, 1.0))
            .coerceIn(0.06, 0.98)
        val bitsPerPixel = (averageEntropyBits * compression).coerceIn(0.12, 7.8)
        val frameBytes = spec.pixelCount * bitsPerPixel / 8.0 + 20.0
        val tableBytes = colorCount * 3.0 + 96.0
        val center = (frameBytes * spec.frameCount + tableBytes).toLong().coerceAtLeast(2048)
        val coverage = (sampledWindows.toFloat() / totalWindows.coerceAtLeast(1)).coerceIn(0f, 1f)
        // 覆盖越少、变化越剧烈，区间越宽。
        val spread = 0.16 + 0.08 * (1.0 - coverage) + 0.10 * averageChangedRatio.coerceIn(0.0, 1.0)
        return SizeEstimate(
            centerBytes = center,
            lowBytes = (center * (1.0 - spread)).toLong().coerceAtLeast(1024),
            highBytes = (center * (1.0 + spread * 1.5)).toLong().coerceAtLeast(2048),
            analyzed = true,
            coverage = coverage,
        )
    }
}

data class AnalysisResult(
    val palette: ColorPalette,
    val estimate: SizeEstimate,
)

/** 抽样帧生成调色板；预览、估算和编码都调用同一函数。 */
object PaletteBuilder {
    private const val SAMPLE_FRAMES = 8

    fun build(
        decoder: FrameDecoder,
        source: SourceVideo,
        recipe: ConversionRecipe,
        spec: OutputSpec,
        colorCount: Int,
        sampleLongSide: Int = 160,
    ): ColorPalette {
        val (sampleW, sampleH) = ImageOps.previewDims(spec, sampleLongSide)
        val requestLong = ImageOps.decodeLongSideForTarget(source, recipe, sampleW, sampleH)
        val start = recipe.trimStartMs.coerceIn(0L, (source.durationMs - 1).coerceAtLeast(0L))
        val end = recipe.trimEndMs.coerceIn(start + 1, source.durationMs.coerceAtLeast(start + 1))
        var total = 0
        val samples = ArrayList<IntArray>(SAMPLE_FRAMES)
        for (i in 0 until SAMPLE_FRAMES) {
            val fraction = (i + 0.5) / SAMPLE_FRAMES
            val timeMs = (start + ((end - start) * fraction).toLong()).coerceAtMost(end - 1)
            val decoded = decoder.decode(timeMs, source, requestLong) ?: continue
            val prepared = ImageOps.prepare(decoded, recipe, sampleW, sampleH)
            val pixels = IntArray(prepared.width * prepared.height)
            prepared.getPixels(pixels, 0, prepared.width, 0, 0, prepared.width, prepared.height)
            prepared.recycle()
            samples += pixels
            total += pixels.size
        }
        if (total == 0) {
            return ColorPalette(intArrayOf(0x000000, 0xFFFFFF))
        }
        val merged = IntArray(total)
        var offset = 0
        for (sample in samples) {
            sample.copyInto(merged, offset)
            offset += sample.size
        }
        return WuQuantizer.quantize(merged, total, colorCount.coerceIn(4, 256))
    }

    /** 分析：统计抽样帧的索引色熵与帧间变化，生成体积区间。不写入任何 GIF 数据。 */
    fun analyze(
        decoder: FrameDecoder,
        source: SourceVideo,
        recipe: ConversionRecipe,
        spec: OutputSpec,
        palette: ColorPalette,
    ): AnalysisResult {
        val windows = 4
        val framesPerWindow = 2
        // 估算在接近实际输出的分辨率上进行：降采样会提高相邻像素相等比例，
        // 直接套用低分辨率统计会把 LZW 压缩率估得过高。
        val sampleLong = max(spec.width, spec.height).coerceAtMost(720)
        val (sampleW, sampleH) = ImageOps.previewDims(spec, sampleLong)
        val requestLong = ImageOps.decodeLongSideForTarget(source, recipe, sampleW, sampleH)
        val totalFrames = spec.frameCount
        var entropySum = 0.0
        var changedSum = 0.0
        var adjacentSum = 0.0
        var sampledFrames = 0
        var sampledWindows = 0
        var previousIndices: ByteArray? = null
        for (w in 0 until windows) {
            val windowFraction = (w + 0.5) / windows
            val centerFrame = (totalFrames * windowFraction).roundToInt().coerceIn(0, totalFrames - 1)
            var windowSampled = false
            for (k in 0 until framesPerWindow) {
                val frameIndex = (centerFrame + k).coerceAtMost(totalFrames - 1)
                val timeMs = recipe.sourceTimeMsForFrame(source, frameIndex)
                val decoded = decoder.decode(timeMs, source, requestLong) ?: continue
                val prepared = ImageOps.prepare(decoded, recipe, sampleW, sampleH)
                val pixels = IntArray(prepared.width * prepared.height)
                prepared.getPixels(pixels, 0, prepared.width, 0, 0, prepared.width, prepared.height)
                prepared.recycle()
                val indices = mapToIndices(pixels, sampleW, sampleH, palette, recipe.dither)
                entropySum += entropyOf(indices, palette.size)
                adjacentSum += adjacentEqualRatio(indices, sampleW, sampleH)
                previousIndices?.let { prev ->
                    changedSum += changedRatio(prev, indices)
                }
                previousIndices = indices
                sampledFrames++
                windowSampled = true
            }
            if (windowSampled) sampledWindows++
        }
        if (sampledFrames == 0) {
            return AnalysisResult(palette, SizeEstimator.quick(spec, recipe.colorCount))
        }
        val averageEntropy = entropySum / sampledFrames
        val averageAdjacent = adjacentSum / sampledFrames
        val averageChanged = (changedSum / (sampledFrames - 1).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        return AnalysisResult(
            palette = palette,
            estimate = SizeEstimator.fromSamples(
                spec = spec,
                colorCount = palette.size,
                averageEntropyBits = averageEntropy,
                averageAdjacentEqual = averageAdjacent,
                averageChangedRatio = averageChanged,
                sampledWindows = sampledWindows,
                totalWindows = windows,
            ),
        )
    }

    private fun entropyOf(indices: ByteArray, paletteSize: Int): Double {
        val histogram = IntArray(paletteSize.coerceAtLeast(1))
        for (index in indices) {
            histogram[index.toInt() and 0xFF]++
        }
        val total = indices.size.toDouble()
        var entropy = 0.0
        for (count in histogram) {
            if (count == 0) continue
            val p = count / total
            entropy -= p * (ln(p) / ln(2.0))
        }
        return entropy
    }

    private fun adjacentEqualRatio(indices: ByteArray, width: Int, height: Int): Double {
        if (width < 2 || height < 1) return 0.0
        var same = 0
        var total = 0
        for (y in 0 until height) {
            val row = y * width
            for (x in 1 until width) {
                if (indices[row + x] == indices[row + x - 1]) same++
                total++
            }
        }
        return if (total == 0) 0.0 else same.toDouble() / total
    }

    private fun changedRatio(a: ByteArray, b: ByteArray): Double {
        val size = min(a.size, b.size)
        if (size == 0) return 0.0
        var changed = 0
        for (i in 0 until size) {
            if (a[i] != b[i]) changed++
        }
        return changed.toDouble() / size
    }
}

/** 单帧预览结果：原视频帧与减色抖动后的效果帧，同一时间点。 */
data class PreviewFrame(
    val raw: Bitmap,
    val processed: Bitmap,
    val timeMs: Long,
    val previewDownscaled: Boolean,
)

object PreviewRenderer {
    const val PREVIEW_MAX_LONG_SIDE = 480

    fun render(
        decoder: FrameDecoder,
        source: SourceVideo,
        recipe: ConversionRecipe,
        spec: OutputSpec,
        timeMs: Long,
        palette: ColorPalette?,
    ): PreviewFrame? {
        val (previewW, previewH) = ImageOps.previewDims(spec, PREVIEW_MAX_LONG_SIDE)
        val downscaled = previewW != spec.width || previewH != spec.height
        val requestLong = ImageOps.decodeLongSideForTarget(source, recipe, previewW, previewH)
        val decoded = decoder.decode(timeMs, source, requestLong) ?: return null
        val raw = ImageOps.prepare(decoded, recipe, previewW, previewH)
        val processed = if (palette == null) {
            raw.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val pixels = IntArray(previewW * previewH)
            raw.getPixels(pixels, 0, previewW, 0, 0, previewW, previewH)
            val indices = mapToIndices(pixels, previewW, previewH, palette, recipe.dither)
            val argb = indicesToArgb(indices, palette)
            Bitmap.createBitmap(previewW, previewH, Bitmap.Config.ARGB_8888).apply {
                setPixels(argb, 0, previewW, 0, 0, previewW, previewH)
            }
        }
        return PreviewFrame(raw, processed, timeMs, downscaled)
    }
}

/** 供测试与调试使用的帧计数估算。 */
fun estimatedFrameCount(durationMs: Long, fps: Int): Int =
    max(1, ceil(durationMs * fps / 1000.0).toInt())
