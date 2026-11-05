package com.example.video2gif.media

import java.io.OutputStream

/**
 * 最小但完整的 GIF89a 写入器：全局调色板、NETSCAPE 循环扩展、全帧、LZW。
 * 无透明、无局部调色板、无差分矩形；首版以正确性为基线。
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    palette: ColorPalette,
    /** 0 = 无限循环，-1 = 只播放一次，n>0 = 额外重复 n 次。 */
    loopRepeats: Int,
) {
    private val tableSize: Int
    private val tableBits: Int
    private val minCodeSize: Int
    private val table: IntArray
    private var frameWritten = false

    init {
        require(width in 1..65535 && height in 1..65535)
        var size = 2
        var bits = 1
        while (size < palette.size) {
            size = size shl 1
            bits++
        }
        tableSize = size
        tableBits = bits
        minCodeSize = bits.coerceAtLeast(2)
        table = IntArray(tableSize) { i -> if (i < palette.size) palette.colors[i] else 0 }

        writeHeader(loopRepeats)
    }

    private fun writeHeader(loopRepeats: Int) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width)
        writeShort(height)
        // 全局色表 | 颜色解析度 8 位 | 未排序 | 表大小
        out.write(0x80 or (0x70) or (tableBits - 1))
        out.write(0)
        out.write(0)
        for (color in table) {
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }
        if (loopRepeats >= 0) {
            out.write(0x21)
            out.write(0xFF)
            out.write(0x0B)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(0x03)
            out.write(0x01)
            writeShort(loopRepeats.coerceAtMost(0xFFFF))
            out.write(0)
        }
    }

    fun writeFrame(indices: ByteArray, delayCentis: Int) {
        require(indices.size == width * height) { "frame size mismatch" }
        frameWritten = true
        // Graphic Control Extension：disposal = 保留画面
        out.write(0x21)
        out.write(0xF9)
        out.write(0x04)
        out.write(0x04)
        writeShort(delayCentis.coerceIn(0, 0xFFFF))
        out.write(0)
        out.write(0)
        // Image Descriptor
        out.write(0x2C)
        writeShort(0)
        writeShort(0)
        writeShort(width)
        writeShort(height)
        out.write(0)
        out.write(minCodeSize)
        val writer = BlockWriter(out)
        val lzw = LzwEncoder(minCodeSize, writer)
        lzw.encode(indices)
        writer.finish()
    }

    fun finish() {
        check(frameWritten) { "GIF 至少需要一帧" }
        out.write(0x3B)
        out.flush()
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }
}

private class BlockWriter(private val out: OutputStream) {
    private val buffer = ByteArray(255)
    private var count = 0

    fun write(byte: Int) {
        buffer[count++] = byte.toByte()
        if (count == 255) flushBlock()
    }

    private fun flushBlock() {
        if (count == 0) return
        out.write(count)
        out.write(buffer, 0, count)
        count = 0
    }

    fun finish() {
        flushBlock()
        out.write(0)
    }
}

private class LzwEncoder(
    private val minCodeSize: Int,
    private val out: BlockWriter,
) {
    private val clearCode = 1 shl minCodeSize
    private val endCode = clearCode + 1
    private var codeSize = minCodeSize + 1
    private var nextCode = endCode + 1
    private val dictionary = HashMap<Int, Int>(8192)
    private var prefix = -1

    private var bitBuffer = 0
    private var bitCount = 0

    init {
        writeCode(clearCode)
    }

    fun encode(pixels: ByteArray) {
        for (pixel in pixels) {
            val value = pixel.toInt() and 0xFF
            if (prefix < 0) {
                prefix = value
                continue
            }
            val key = (prefix shl 8) or value
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            writeCode(prefix)
            if (nextCode < 4096) {
                dictionary[key] = nextCode
                nextCode++
                // 解码器比编码器晚一个条目增长码宽；这里必须在超过 2^N 后再加一位。
                if (nextCode > (1 shl codeSize) && codeSize < 12) {
                    codeSize++
                }
            } else {
                writeCode(clearCode)
                dictionary.clear()
                codeSize = minCodeSize + 1
                nextCode = endCode + 1
            }
            prefix = value
        }
        if (prefix >= 0) writeCode(prefix)
        writeCode(endCode)
        flushBits()
    }

    private fun writeCode(code: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            out.write(bitBuffer and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun flushBits() {
        if (bitCount > 0) {
            out.write(bitBuffer and 0xFF)
            bitBuffer = 0
            bitCount = 0
        }
    }
}
