package com.example.video2gif.media

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.w3c.dom.Node

class GifOptimizationTest {
    private val palette = ColorPalette(intArrayOf(0, 0xFFFFFF, 0xFF0000, 0x00FF00))

    @Test fun `moving rectangle erases old location and merges identical frames`() {
        val width = 20
        val height = 12
        val frames = listOf(
            ByteArray(width * height),
            ByteArray(width * height) { if (it % width in 2..4 && it / width in 3..5) 1 else 0 },
            ByteArray(width * height) { if (it % width in 9..11 && it / width in 6..8) 2 else 0 },
            ByteArray(width * height),
        )
        val out = ByteArrayOutputStream()
        val encoder = GifEncoder(out, width, height, palette, 0)
        frames.forEach { encoder.writeFrame(it, 8); encoder.writeFrame(it.copyOf(), 9) }
        encoder.finish()
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(out.toByteArray())).use { input ->
            reader.input = input
            assertEquals(4, reader.getNumImages(true))
            val canvas = IntArray(width * height)
            frames.forEachIndexed { i, expected ->
                val root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0")
                val descriptor = root.child("ImageDescriptor")
                val left = descriptor.int("imageLeftPosition")
                val top = descriptor.int("imageTopPosition")
                val control = root.child("GraphicControlExtension")
                assertEquals(17, control.int("delayTime"))
                assertEquals("doNotDispose", control.attributes.getNamedItem("disposalMethod").nodeValue)
                val image = reader.read(i)
                if (i > 0) assertTrue(image.width * image.height < width * height)
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    canvas[(top + y) * width + left + x] = image.getRGB(x, y) and 0xFFFFFF
                }
                expected.forEachIndexed { index, byte -> assertEquals("frame $i pixel $index", palette.colors[byte.toInt()], canvas[index]) }
            }
        }
        reader.dispose()
    }

    @Test fun `long duplicate run preserves delay beyond unsigned short`() {
        val out = ByteArrayOutputStream()
        val encoder = GifEncoder(out, 2, 2, palette, -1)
        val pixels = byteArrayOf(1, 2, 3, 0)
        encoder.writeFrame(pixels, 65535)
        encoder.writeFrame(pixels, 50)
        pixels.fill(0) // 验证调用方复用缓冲不会改变待写帧。
        encoder.finish()
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        ImageIO.createImageInputStream(ByteArrayInputStream(out.toByteArray())).use { input ->
            reader.input = input
            assertEquals(2, reader.getNumImages(true))
            val delay = (0..1).sumOf { reader.getImageMetadata(it).getAsTree("javax_imageio_gif_image_1.0").child("GraphicControlExtension").int("delayTime") }
            assertEquals(65585, delay)
            assertEquals(0xFFFFFF, reader.read(0).getRGB(0, 0) and 0xFFFFFF)
        }
        reader.dispose()
    }

    private fun Node.child(name: String): Node = (0 until childNodes.length).map { childNodes.item(it) }.first { it.nodeName == name }
    private fun Node.int(name: String) = attributes.getNamedItem(name).nodeValue.toInt()
}
