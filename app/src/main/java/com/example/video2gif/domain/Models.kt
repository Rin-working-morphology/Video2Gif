package com.example.video2gif.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
)

/** 导入完成后的来源信息。宽高是已经应用源方向元数据之后的显示尺寸。 */
data class SourceVideo(
    val displayWidth: Int,
    val displayHeight: Int,
    val rotationDegrees: Int,
    val durationMs: Long,
    val mimeType: String?,
    val nominalFrameRate: Float?,
) {
    val aspectRatio: Float
        get() = if (displayHeight > 0) displayWidth.toFloat() / displayHeight else 1f
}

/** 画质只组合颜色数量与抖动；尺寸和帧率始终保持独立。 */
enum class QualityPreset {
    Compact,
    Balanced,
    Fine,
    ;

    val colorCount: Int
        get() = when (this) {
            Compact -> 64
            Balanced -> 128
            Fine -> 256
        }

    val dither: DitherMode
        get() = when (this) {
            Compact -> DitherMode.Off
            Balanced -> DitherMode.Light
            Fine -> DitherMode.Standard
        }

    companion object {
        fun from(colorCount: Int, dither: DitherMode): QualityPreset? =
            entries.firstOrNull { it.colorCount == colorCount && it.dither == dither }
    }
}

enum class DitherMode { Off, Light, Standard }

val FPS_OPTIONS = listOf(5, 10, 12, 15, 20, 24, 30)
val LONG_SIDE_OPTIONS = listOf(240, 360, 480, 720)

const val MAX_LONG_SIDE = 1080
const val MAX_BATCH_ITEMS = 10

enum class LoopMode { Infinite, Once, Custom }

enum class CropAspect { Original, Square, FourThree, NineSixteen, Free }

data class CropSpec(
    /** 相对旋转后画面的归一化坐标，范围 0..1。 */
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val aspect: CropAspect = CropAspect.Original,
) {
    val width: Float get() = (right - left).coerceIn(0.02f, 1f)
    val height: Float get() = (bottom - top).coerceIn(0.02f, 1f)

    val isFullFrame: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    companion object {
        val Full = CropSpec()

        /** 以画面中心为基准生成指定比例的取景框。 */
        fun centered(aspect: CropAspect, sourceWidth: Int, sourceHeight: Int): CropSpec {
            if (aspect == CropAspect.Original || aspect == CropAspect.Free) return Full
            val sourceAspect = sourceWidth.toFloat() / sourceHeight.coerceAtLeast(1)
            val target = when (aspect) {
                CropAspect.Square -> 1f
                CropAspect.FourThree -> 4f / 3f
                CropAspect.NineSixteen -> 9f / 16f
                else -> sourceAspect
            }
            return if (sourceAspect > target) {
                // 画面更宽：裁掉左右
                val w = target / sourceAspect
                val left = (1f - w) / 2f
                CropSpec(left = left, right = left + w, aspect = aspect)
            } else {
                val h = sourceAspect / target
                val top = (1f - h) / 2f
                CropSpec(top = top, bottom = top + h, aspect = aspect)
            }
        }

        /** 拖动取景框后保持比例，四边不越界。 */
        fun fromEdgeDrag(
            aspect: CropAspect,
            sourceWidth: Int,
            sourceHeight: Int,
            anchorLeft: Float,
            anchorTop: Float,
            right: Float,
            bottom: Float,
        ): CropSpec {
            var l = min(anchorLeft, right).coerceIn(0f, 0.98f)
            var t = min(anchorTop, bottom).coerceIn(0f, 0.98f)
            var r = max(anchorLeft, right).coerceIn(0.02f, 1f)
            var b = max(anchorTop, bottom).coerceIn(0.02f, 1f)
            if (r - l < 0.05f) r = (l + 0.05f).coerceAtMost(1f)
            if (b - t < 0.05f) b = (t + 0.05f).coerceAtMost(1f)
            if (aspect == CropAspect.Original || aspect == CropAspect.Free) {
                return CropSpec(l, t, r, b, aspect)
            }
            val sourceAspect = sourceWidth.toFloat() / sourceHeight.coerceAtLeast(1)
            val target = when (aspect) {
                CropAspect.Square -> 1f
                CropAspect.FourThree -> 4f / 3f
                CropAspect.NineSixteen -> 9f / 16f
                else -> sourceAspect
            }
            // 以拖动结果为对角线，修正为固定比例
            var w = r - l
            var h = b - t
            val targetNormalized = target / sourceAspect
            if (w / h > targetNormalized) {
                w = h * targetNormalized
            } else {
                h = w / targetNormalized
            }
            if (l + w > 1f) {
                l = 1f - w
            }
            if (t + h > 1f) {
                t = 1f - h
            }
            w = w.coerceAtMost(1f - l)
            h = h.coerceAtMost(1f - t)
            return CropSpec(l, t, l + w, t + h, aspect)
        }
    }
}

data class ConversionRecipe(
    val quality: QualityPreset = QualityPreset.Balanced,
    val colorCount: Int = 128,
    val dither: DitherMode = DitherMode.Light,
    val fps: Int = 12,
    val longSide: Int = 480,
    val speed: Float = 1f,
    val loop: LoopMode = LoopMode.Infinite,
    val loopCount: Int = 3,
    val userRotation: Int = 0,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val crop: CropSpec = CropSpec.Full,
) {
    val qualityIsCustom: Boolean
        get() = QualityPreset.from(colorCount, dither) == null

    /** 用户可选参数改变后，画质分段控件回落到“自定义”。 */
    fun withColors(colors: Int): ConversionRecipe = copy(colorCount = colors.coerceIn(4, 256))

    fun withDither(mode: DitherMode): ConversionRecipe = copy(dither = mode)
}

enum class SharedParam { Quality, ColorCount, Dither, Fps, LongSide, Speed, Loop, Rotation, Crop }

data class VideoItemDraft(
    val id: String,
    val uri: String,
    val name: String,
    val source: SourceVideo?,
    val recipe: ConversionRecipe,
    val overrides: Set<SharedParam> = emptySet(),
    val error: String? = null,
) {
    val hasIndividualSettings: Boolean get() = overrides.isNotEmpty()
}

data class BatchDraft(
    val id: String,
    val defaults: ConversionRecipe,
    val items: List<VideoItemDraft>,
    val updatedAt: Long,
) {
    val size: Int get() = items.size
}

data class OutputSpec(
    val width: Int,
    val height: Int,
    val frameCount: Int,
    val durationMs: Long,
) {
    val pixelCount: Int get() = width * height
}

/** 按截取、速度、旋转、裁剪与目标长边计算实际输出规格。 */
fun ConversionRecipe.outputSpec(source: SourceVideo?): OutputSpec? {
    source ?: return null
    if (source.displayWidth <= 0 || source.displayHeight <= 0) return null
    val rotatedW: Int
    val rotatedH: Int
    if (normalizedRotation(userRotation) % 180 == 0) {
        rotatedW = source.displayWidth
        rotatedH = source.displayHeight
    } else {
        rotatedW = source.displayHeight
        rotatedH = source.displayWidth
    }
    val cropW = (rotatedW * crop.width).coerceAtLeast(2f)
    val cropH = (rotatedH * crop.height).coerceAtLeast(2f)
    val cropLong = max(cropW, cropH)
    // 默认不放大：目标长边超过裁剪后长边时保持原尺寸。
    val scale = min(1f, longSide.toFloat() / cropLong)
    var width = (cropW * scale).roundToInt().coerceAtLeast(2)
    var height = (cropH * scale).roundToInt().coerceAtLeast(2)
    if (width % 2 != 0 && cropW > 4) width -= 1
    if (height % 2 != 0 && cropH > 4) height -= 1
    val safeEnd = trimEndMs.coerceIn(1L, source.durationMs.coerceAtLeast(1L))
    val safeStart = trimStartMs.coerceIn(0L, (safeEnd - 1).coerceAtLeast(0L))
    val sourceDuration = (safeEnd - safeStart).coerceAtLeast(1L)
    val outputDuration = (sourceDuration / speed.coerceAtLeast(0.1f)).roundToLong()
    val frames = max(1, (outputDuration * fps / 1000.0).roundToInt())
    return OutputSpec(width, height, frames, outputDuration)
}

fun normalizedRotation(degrees: Int): Int {
    val d = degrees % 360
    return if (d < 0) d + 360 else d
}

/** 第 k 个输出帧对应的源时间，保证不越过截取终点。 */
fun ConversionRecipe.sourceTimeMsForFrame(source: SourceVideo, frameIndex: Int): Long {
    val rotationlessEnd = trimEndMs.coerceIn(1L, source.durationMs.coerceAtLeast(1L))
    val start = trimStartMs.coerceIn(0L, (rotationlessEnd - 1).coerceAtLeast(0L))
    val offset = (frameIndex.toDouble() / fps.coerceAtLeast(1) * 1000.0 * speed).roundToLong()
    return (start + offset).coerceIn(start, rotationlessEnd - 1)
}

/** GIF 以 10 ms 为延迟单位；使用累计误差补偿避免 12/24/30 fps 漂移。 */
fun gifDelayCentis(frameIndex: Int, fps: Int): Int {
    val prev = (frameIndex * 100.0 / fps).roundToLong()
    val next = ((frameIndex + 1) * 100.0 / fps).roundToLong()
    return (next - prev).toInt().coerceAtLeast(2)
}
