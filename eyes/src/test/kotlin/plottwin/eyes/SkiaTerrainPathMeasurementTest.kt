package plottwin.eyes

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.VertexMode

private const val VIEW_PIXELS_WIDE = 640
private const val VIEW_PIXELS_HIGH = 400
private const val RENDER_GRID_CELLS = 225
private const val CHUNK_CELLS = 64
private const val TEXTURE_PIXELS = 64
private const val TIMED_FRAMES = 20
private const val WARMUP_FRAMES = 5
private const val LIT_GREY = 0xFFFFFFFF.toInt()
private const val SHADED_GREY = 0xFF404040.toInt()

private val measurementDirectory = File(System.getProperty("user.dir"), "build")

private val bandedSkslProgram = """
    half4 main(float2 coord) {
        float band = 0.5 + 0.5 * sin(coord.x * 0.05 + coord.y * 0.03);
        float ridge = 0.5 + 0.5 * cos(coord.y * 0.11);
        return half4(half(band * 0.9), half(ridge * 0.8), half(0.25), 1.0);
    }
""".trimIndent()

private class TerrainChunkBatch(val positions: FloatArray, val colors: IntArray, val texCoords: FloatArray)

private fun chunkBatches(): List<TerrainChunkBatch> {
    val cellPixelsWide = VIEW_PIXELS_WIDE.toFloat() / RENDER_GRID_CELLS
    val cellPixelsHigh = VIEW_PIXELS_HIGH.toFloat() / RENDER_GRID_CELLS
    val chunksPerSide = (RENDER_GRID_CELLS + CHUNK_CELLS - 1) / CHUNK_CELLS
    val batches = ArrayList<TerrainChunkBatch>(chunksPerSide * chunksPerSide)
    for (chunkRow in 0 until chunksPerSide) {
        for (chunkColumn in 0 until chunksPerSide) {
            val firstColumn = chunkColumn * CHUNK_CELLS
            val firstRow = chunkRow * CHUNK_CELLS
            val columns = minOf(CHUNK_CELLS, RENDER_GRID_CELLS - firstColumn)
            val rows = minOf(CHUNK_CELLS, RENDER_GRID_CELLS - firstRow)
            val vertexCount = columns * rows * 6
            val positions = FloatArray(vertexCount * 2)
            val texCoords = FloatArray(vertexCount * 2)
            val colors = IntArray(vertexCount)
            var write = 0
            for (row in firstRow until firstRow + rows) {
                for (column in firstColumn until firstColumn + columns) {
                    val left = column * cellPixelsWide
                    val top = row * cellPixelsHigh
                    val right = left + cellPixelsWide
                    val bottom = top + cellPixelsHigh
                    val shade = if (column < RENDER_GRID_CELLS / 2) LIT_GREY else SHADED_GREY
                    for ((cornerX, cornerY) in listOf(
                        left to top, right to top, right to bottom,
                        left to top, right to bottom, left to bottom,
                    )) {
                        positions[write * 2] = cornerX
                        positions[write * 2 + 1] = cornerY
                        texCoords[write * 2] = cornerX * 0.5f
                        texCoords[write * 2 + 1] = cornerY * 0.5f
                        colors[write] = shade
                        write++
                    }
                }
            }
            batches.add(TerrainChunkBatch(positions, colors, texCoords))
        }
    }
    return batches
}

private fun checkerTexture(): Image {
    val info = ImageInfo.makeN32Premul(TEXTURE_PIXELS, TEXTURE_PIXELS)
    val pixels = ByteArray(TEXTURE_PIXELS * TEXTURE_PIXELS * 4)
    for (row in 0 until TEXTURE_PIXELS) {
        for (column in 0 until TEXTURE_PIXELS) {
            val darkPatch = ((row / 8) + (column / 8)) % 2 == 0
            val offset = (row * TEXTURE_PIXELS + column) * 4
            pixels[offset] = if (darkPatch) 40.toByte() else 90.toByte()
            pixels[offset + 1] = if (darkPatch) 90.toByte() else 170.toByte()
            pixels[offset + 2] = if (darkPatch) 30.toByte() else 60.toByte()
            pixels[offset + 3] = 255.toByte()
        }
    }
    return Image.makeRaster(info, pixels, TEXTURE_PIXELS * 4)
}

private fun timeFrames(name: String, drawFrame: (Canvas) -> Unit): Pair<Double, BufferedImage> {
    val surface = Surface.makeRasterN32Premul(VIEW_PIXELS_WIDE, VIEW_PIXELS_HIGH)
    repeat(WARMUP_FRAMES) { drawFrame(surface.canvas) }
    val startedAt = System.nanoTime()
    repeat(TIMED_FRAMES) { drawFrame(surface.canvas) }
    val millisPerFrame = (System.nanoTime() - startedAt) / 1e6 / TIMED_FRAMES
    val png = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    val frame = ImageIO.read(ByteArrayInputStream(png))
    measurementDirectory.mkdirs()
    ImageIO.write(frame, "PNG", File(measurementDirectory, "skia_$name.png"))
    println("[skia] $name %.2f ms/frame at ${RENDER_GRID_CELLS * RENDER_GRID_CELLS * 2} triangles".format(millisPerFrame))
    return millisPerFrame to frame
}

private fun distinctColorCount(frame: BufferedImage): Int {
    val seen = HashSet<Int>()
    for (row in 0 until frame.height step 3) {
        for (column in 0 until frame.width step 3) seen.add(frame.getRGB(column, row))
    }
    return seen.size
}

private fun meanLuminance(frame: BufferedImage, firstColumn: Int, lastColumn: Int): Double {
    var total = 0.0
    var counted = 0
    for (row in 0 until frame.height step 2) {
        for (column in firstColumn until lastColumn step 2) {
            total += luminanceAt(frame, column, row)
            counted++
        }
    }
    return total / counted
}

class SkiaTerrainPathMeasurementTest {

    private val batches = chunkBatches()

    @Test
    fun a_per_pixel_sksl_program_runs_on_the_headless_raster_backend_over_the_whole_terrain() {
        val effect = RuntimeEffect.makeForShader(bandedSkslProgram)
        assertNotNull(effect, "SkSL failed to compile on the headless backend")
        val shaderPaint = Paint().apply { shader = effect.makeShader(null, null, null) }
        val flatPaint = Paint().apply { color = Color.makeRGB(90, 140, 60) }

        val (flatMillis, _) = timeFrames("terrain-flat-vertex-colors") { canvas ->
            batches.forEach { batch ->
                canvas.drawVertices(VertexMode.TRIANGLES, batch.positions, batch.colors, null, null, BlendMode.DST, flatPaint)
            }
        }
        val (skslMillis, skslFrame) = timeFrames("terrain-sksl-runtime-effect") { canvas ->
            batches.forEach { batch ->
                canvas.drawVertices(VertexMode.TRIANGLES, batch.positions, null, batch.texCoords, null, BlendMode.SRC, shaderPaint)
            }
        }

        println("[skia] sksl costs %.2fx the flat vertex-colour path".format(skslMillis / flatMillis))
        assertTrue(distinctColorCount(skslFrame) > 50, "the SkSL program drew a flat frame, so it did not run per pixel")
        assertTrue(skslMillis < 500.0, "SkSL over the terrain took %.1f ms/frame".format(skslMillis))
    }

    @Test
    fun a_ground_texture_and_a_baked_lightmap_drape_through_one_drawvertices_call() {
        val texture = checkerTexture()
        val drapePaint = Paint().apply {
            shader = texture.makeShader(FilterTileMode.REPEAT, FilterTileMode.REPEAT, null)
        }

        val (drapeMillis, drapeFrame) = timeFrames("terrain-texture-drape") { canvas ->
            batches.forEach { batch ->
                canvas.drawVertices(VertexMode.TRIANGLES, batch.positions, null, batch.texCoords, null, BlendMode.SRC, drapePaint)
            }
        }
        val (lightmapMillis, lightmapFrame) = timeFrames("terrain-texture-drape-with-lightmap") { canvas ->
            batches.forEach { batch ->
                canvas.drawVertices(
                    VertexMode.TRIANGLES,
                    batch.positions,
                    batch.colors,
                    batch.texCoords,
                    null,
                    BlendMode.MODULATE,
                    drapePaint,
                )
            }
        }

        println("[skia] drape %.2f ms, drape+lightmap %.2f ms".format(drapeMillis, lightmapMillis))
        assertTrue(distinctColorCount(drapeFrame) > 1, "the texture did not reach the triangles")
        val litHalf = meanLuminance(lightmapFrame, 0, lightmapFrame.width / 2)
        val shadedHalf = meanLuminance(lightmapFrame, lightmapFrame.width / 2, lightmapFrame.width)
        println("[skia] lightmap lit half %.1f vs shaded half %.1f".format(litHalf, shadedHalf))
        assertTrue(litHalf > shadedHalf * 1.5, "the baked lightmap did not darken its half: $litHalf vs $shadedHalf")
        assertTrue(distinctColorCount(lightmapFrame) > 2, "texture and lightmap did not combine")
    }
}
