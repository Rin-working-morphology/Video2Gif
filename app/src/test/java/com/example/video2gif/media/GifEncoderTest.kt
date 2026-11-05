package com.example.video2gif.media

import com.example.video2gif.domain.DitherMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

class GifEncoderTest {

    @Test
    fun `lzw round trip keeps binary pixels`() {
        val width = 257
        val height = 193
        val random = Random(42)
        val indices = ByteArray(width * height) { (random.nextInt(2)).toByte() }
        val palette = ColorPalette(intArrayOf(0x000000, 0xFFFFFF))
        val bytes = encode(width, height, palette, indices)
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = indices[y * width + x].toInt() and 0xFF
                val expected = if (index == 0) 0x000000 else 0xFFFFFF
                assertEquals(
                    "pixel $x,$y",
                    expected,
                    decoded.getRGB(x, y) and 0xFFFFFF,
                )
            }
        }
    }

    @Test
    fun `quantizer returns usable palette for gradient`() {
        val pixels = IntArray(64 * 64) { i ->
            val x = i % 64
            val y = i / 64
            ((x * 4) shl 16) or ((y * 4) shl 8) or (((x + y) * 2) and 0xFF)
        }
        val palette = WuQuantizer.quantize(pixels, pixels.size, 32)
        assertTrue(palette.size in 2..32)
        val dither = DitherMode.Standard
        val indices = mapToIndices(pixels, 64, 64, palette, dither)
        assertTrue(indices.size == 64 * 64)
        val bytes = encode(64, 64, palette, indices)
        val decoded: BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(64, decoded.width)
        assertEquals(64, decoded.height)
    }

    @Test
    fun `gif header and loop extension are present`() {
        val palette = ColorPalette(intArrayOf(0xFF0000, 0x00FF00))
        val bytes = encode(4, 4, palette, ByteArray(16) { 1 }, loopRepeats = 0)
        val header = String(bytes.copyOfRange(0, 6), Charsets.US_ASCII)
        assertEquals("GIF89a", header)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("NETSCAPE2.0"))
    }

    private fun encode(
        width: Int,
        height: Int,
        palette: ColorPalette,
        indices: ByteArray,
        loopRepeats: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = GifEncoder(out, width, height, palette, loopRepeats)
        encoder.writeFrame(indices, 8)
        encoder.finish()
        return out.toByteArray()
    }
}
