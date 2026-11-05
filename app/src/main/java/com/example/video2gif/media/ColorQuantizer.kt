package com.example.video2gif.media

import com.example.video2gif.domain.DitherMode
import kotlin.math.roundToInt

/**
 * 确定性 Wu 量化器。预览、估算与正式编码共用同一个实例与同一批抽样帧，
 * 以保证同一转换定义得到相同调色板。
 */
object WuQuantizer {
    private const val SIDE = 32
    private const val SIDE_PLUS = 33
    private const val BINS = SIDE_PLUS * SIDE_PLUS * SIDE_PLUS

    private fun index(r: Int, g: Int, b: Int): Int = r + g * SIDE_PLUS + b * SIDE_PLUS * SIDE_PLUS

    fun quantize(pixels: IntArray, pixelCount: Int, maxColors: Int): ColorPalette {
        require(pixelCount > 0)
        val wt = LongArray(BINS)
        val mr = LongArray(BINS)
        val mg = LongArray(BINS)
        val mb = LongArray(BINS)
        val m2 = LongArray(BINS)

        val step = (pixelCount / 220_000).coerceAtLeast(1)
        var i = 0
        while (i < pixelCount) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val bin = index((r shr 3) + 1, (g shr 3) + 1, (b shr 3) + 1)
            wt[bin] += 1
            mr[bin] += r.toLong()
            mg[bin] += g.toLong()
            mb[bin] += b.toLong()
            m2[bin] += (r * r + g * g + b * b).toLong()
            i += step
        }

        for (r in 1..SIDE) {
            for (g in 1..SIDE) {
                for (b in 1..SIDE) {
                    val idx = index(r, g, b)
                    val x = index(r - 1, g, b)
                    val y = index(r, g - 1, b)
                    val z = index(r, g, b - 1)
                    val xy = index(r - 1, g - 1, b)
                    val xz = index(r - 1, g, b - 1)
                    val yz = index(r, g - 1, b - 1)
                    val xyz = index(r - 1, g - 1, b - 1)
                    wt[idx] += wt[x] + wt[y] + wt[z] - wt[xy] - wt[xz] - wt[yz] + wt[xyz]
                    mr[idx] += mr[x] + mr[y] + mr[z] - mr[xy] - mr[xz] - mr[yz] + mr[xyz]
                    mg[idx] += mg[x] + mg[y] + mg[z] - mg[xy] - mg[xz] - mg[yz] + mg[xyz]
                    mb[idx] += mb[x] + mb[y] + mb[z] - mb[xy] - mb[xz] - mb[yz] + mb[xyz]
                    m2[idx] += m2[x] + m2[y] + m2[z] - m2[xy] - m2[xz] - m2[yz] + m2[xyz]
                }
            }
        }

        val boxes = ArrayList<Box>(maxColors)
        boxes += Box(0, SIDE, 0, SIDE, 0, SIDE)
        while (boxes.size < maxColors) {
            var bestIndex = -1
            var bestScore = 0.0
            for (idx in boxes.indices) {
                val box = boxes[idx]
                val score = priority(box, wt, mr, mg, mb, m2)
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = idx
                }
            }
            if (bestIndex < 0) break
            val split = splitBox(boxes[bestIndex], wt, mr, mg, mb, m2) ?: break
            boxes[bestIndex] = split.first
            boxes += split.second
        }

        val colors = IntArray(boxes.size)
        var count = 0
        for (box in boxes) {
            val weight = volume(box, wt)
            if (weight <= 0L) continue
            val r = (volume(box, mr).toDouble() / weight).roundToInt().coerceIn(0, 255)
            val g = (volume(box, mg).toDouble() / weight).roundToInt().coerceIn(0, 255)
            val b = (volume(box, mb).toDouble() / weight).roundToInt().coerceIn(0, 255)
            colors[count++] = (r shl 16) or (g shl 8) or b
        }
        if (count == 0) colors[0] = 0
        return ColorPalette(if (count == colors.size) colors else colors.copyOf(count))
    }

    private class Box(val r0: Int, val r1: Int, val g0: Int, val g1: Int, val b0: Int, val b1: Int)

    private fun volume(box: Box, moment: LongArray): Long {
        val i111 = index(box.r1, box.g1, box.b1)
        val i011 = index(box.r0, box.g1, box.b1)
        val i101 = index(box.r1, box.g0, box.b1)
        val i001 = index(box.r0, box.g0, box.b1)
        val i110 = index(box.r1, box.g1, box.b0)
        val i010 = index(box.r0, box.g1, box.b0)
        val i100 = index(box.r1, box.g0, box.b0)
        val i000 = index(box.r0, box.g0, box.b0)
        return moment[i111] - moment[i011] - moment[i101] + moment[i001] +
            moment[i110] - moment[i010] - moment[i100] + moment[i000]
    }

    private fun priority(
        box: Box,
        wt: LongArray,
        mr: LongArray,
        mg: LongArray,
        mb: LongArray,
        m2: LongArray,
    ): Double {
        val weight = volume(box, wt)
        if (weight <= 0L) return 0.0
        if (box.r1 - box.r0 <= 1 && box.g1 - box.g0 <= 1 && box.b1 - box.b0 <= 1) return 0.0
        val r = volume(box, mr)
        val g = volume(box, mg)
        val b = volume(box, mb)
        val sum2 = volume(box, m2)
        val variance = sum2.toDouble() - (r * r + g * g + b * b).toDouble() / weight
        return variance * weight
    }

    private fun splitBox(
        box: Box,
        wt: LongArray,
        mr: LongArray,
        mg: LongArray,
        mb: LongArray,
        m2: LongArray,
    ): Pair<Box, Box>? {
        val dr = box.r1 - box.r0
        val dg = box.g1 - box.g0
        val db = box.b1 - box.b0
        val axis = when {
            dr >= dg && dr >= db -> 0
            dg >= db -> 1
            else -> 2
        }
        if (when (axis) {
                0 -> dr
                1 -> dg
                else -> db
            } <= 1
        ) {
            return null
        }
        var bestCut = -1
        var bestScore = -1.0
        val start: Int
        val end: Int
        when (axis) {
            0 -> {
                start = box.r0 + 1
                end = box.r1
            }
            1 -> {
                start = box.g0 + 1
                end = box.g1
            }
            else -> {
                start = box.b0 + 1
                end = box.b1
            }
        }
        for (cut in start until end) {
            val first: Box
            val second: Box
            when (axis) {
                0 -> {
                    first = Box(box.r0, cut, box.g0, box.g1, box.b0, box.b1)
                    second = Box(cut, box.r1, box.g0, box.g1, box.b0, box.b1)
                }
                1 -> {
                    first = Box(box.r0, box.r1, box.g0, cut, box.b0, box.b1)
                    second = Box(box.r0, box.r1, cut, box.g1, box.b0, box.b1)
                }
                else -> {
                    first = Box(box.r0, box.r1, box.g0, box.g1, box.b0, cut)
                    second = Box(box.r0, box.r1, box.g0, box.g1, cut, box.b1)
                }
            }
            if (volume(first, wt) <= 0L || volume(second, wt) <= 0L) continue
            val score = squaredMeans(first, wt, mr, mg, mb) + squaredMeans(second, wt, mr, mg, mb)
            if (score > bestScore) {
                bestScore = score
                bestCut = cut
            }
        }
        if (bestCut < 0) return null
        val first: Box
        val second: Box
        when (axis) {
            0 -> {
                first = Box(box.r0, bestCut, box.g0, box.g1, box.b0, box.b1)
                second = Box(bestCut, box.r1, box.g0, box.g1, box.b0, box.b1)
            }
            1 -> {
                first = Box(box.r0, box.r1, box.g0, bestCut, box.b0, box.b1)
                second = Box(box.r0, box.r1, bestCut, box.g1, box.b0, box.b1)
            }
            else -> {
                first = Box(box.r0, box.r1, box.g0, box.g1, box.b0, bestCut)
                second = Box(box.r0, box.r1, box.g0, box.g1, bestCut, box.b1)
            }
        }
        return first to second
    }

    private fun squaredMeans(
        box: Box,
        wt: LongArray,
        mr: LongArray,
        mg: LongArray,
        mb: LongArray,
    ): Double {
        val weight = volume(box, wt)
        if (weight <= 0L) return 0.0
        val r = volume(box, mr)
        val g = volume(box, mg)
        val b = volume(box, mb)
        return (r * r + g * g + b * b).toDouble() / weight
    }
}

/**
 * 调色板与 5-bit RGB 查找表。查找表把逐像素最近色搜索压到 O(1)，
 * 预览与正式编码共用同一映射，避免两条链出现颜色差异。
 */
class ColorPalette(val colors: IntArray) {
    val size: Int get() = colors.size

    private val lut: IntArray by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { buildLut() }

    private fun buildLut(): IntArray {
        val table = IntArray(32 * 32 * 32)
        var r5 = 0
        while (r5 < 32) {
            val r = (r5 shl 3) or 4
            var g5 = 0
            while (g5 < 32) {
                val g = (g5 shl 3) or 4
                var b5 = 0
                while (b5 < 32) {
                    val b = (b5 shl 3) or 4
                    table[(r5 shl 10) or (g5 shl 5) or b5] = nearestInPalette(r, g, b)
                    b5++
                }
                g5++
            }
            r5++
        }
        return table
    }

    private fun nearestInPalette(r: Int, g: Int, b: Int): Int {
        var best = 0
        var bestDistance = Int.MAX_VALUE
        for (i in colors.indices) {
            val c = colors[i]
            val dr = r - ((c shr 16) and 0xFF)
            val dg = g - ((c shr 8) and 0xFF)
            val db = b - (c and 0xFF)
            // 近似感知权重，避免绿色误差被低估。
            val distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    fun map(r: Int, g: Int, b: Int): Int {
        val rr = (r.coerceIn(0, 255) shr 3)
        val gg = (g.coerceIn(0, 255) shr 3)
        val bb = (b.coerceIn(0, 255) shr 3)
        return lut[(rr shl 10) or (gg shl 5) or bb]
    }
}

/** 固定 8×8 Bayer 矩阵，抖动落在输出像素坐标系中，结果可复现。 */
object OrderedDither {
    private val bayer = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )

    fun strength(mode: DitherMode): Float = when (mode) {
        DitherMode.Off -> 0f
        DitherMode.Light -> 0.45f
        DitherMode.Standard -> 0.9f
    }

    fun offset(x: Int, y: Int, strength: Float): Int {
        if (strength <= 0f) return 0
        val threshold = bayer[((y and 7) shl 3) or (x and 7)]
        return ((threshold / 64f - 0.5f) * 72f * strength).roundToInt()
    }
}

fun mapToIndices(
    pixels: IntArray,
    width: Int,
    height: Int,
    palette: ColorPalette,
    dither: DitherMode,
): ByteArray {
    val out = ByteArray(width * height)
    val strength = OrderedDither.strength(dither)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val offset = OrderedDither.offset(x, y, strength)
            out[i] = palette.map(
                (r + offset).coerceIn(0, 255),
                (g + offset).coerceIn(0, 255),
                (b + offset).coerceIn(0, 255),
            ).toByte()
            i++
        }
    }
    return out
}

fun indicesToArgb(indices: ByteArray, palette: ColorPalette): IntArray {
    val colors = palette.colors
    val out = IntArray(indices.size)
    for (i in indices.indices) {
        // 调色板只存 RGB；预览 Bitmap 需要不透明 alpha，否则会绘制成透明/黑色。
        out[i] = colors[indices[i].toInt() and 0xFF] or 0xFF000000.toInt()
    }
    return out
}
