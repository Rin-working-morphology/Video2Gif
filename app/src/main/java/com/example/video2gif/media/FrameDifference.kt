package com.example.video2gif.media

data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/** 在索引色域求变化包围盒；编码和估算共用同一规则。null 表示完整重复帧。 */
fun changedBounds(previous: ByteArray?, current: ByteArray, width: Int, height: Int): PixelRect? {
    require(current.size == width * height)
    if (previous == null) return PixelRect(0, 0, width, height)
    require(previous.size == current.size)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) for (x in 0 until width) {
        val i = y * width + x
        if (previous[i] != current[i]) {
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
    }
    return if (right < 0) null else PixelRect(left, top, right - left + 1, bottom - top + 1)
}
