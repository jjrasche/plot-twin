package plottwin.render

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val LEGEND_STRIP_HEIGHT_PX = 116
// every changed cell is tinted at least this far toward its ramp so shallow work stays visible
const val MIN_DIFF_TINT = 0.35f
// red/blue pair survives the common colour-blindnesses; never red/green
val CUT_RAMP_RGB = Triple(196, 60, 57)
val FILL_RAMP_RGB = Triple(59, 111, 212)

class StageDiffRender(
    val image: BufferedImage,
    val plotWidthPx: Int,
    val plotHeightPx: Int,
    val diffMask: BooleanArray,
    val overlayMask: BooleanArray,
)

fun pngBytesOf(image: BufferedImage): ByteArray =
    ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()

// plain top-down base: one pixel per cell, north at the top, quiet grey-green shaded by elevation
fun paintTopDownBase(spec: StageDiffSpec): BufferedImage {
    val base = BufferedImage(spec.columns, spec.rows, BufferedImage.TYPE_INT_RGB)
    val lowest = spec.measuredHeights.min()
    val highest = spec.measuredHeights.max()
    val heightSpan = (highest - lowest).takeIf { it > 0.0f } ?: 1.0f
    for (pixelY in 0 until spec.rows) {
        val row = spec.rows - 1 - pixelY
        for (pixelX in 0 until spec.columns) {
            val relief = (spec.measuredHeights[row * spec.columns + pixelX] - lowest) / heightSpan
            base.setRGB(pixelX, pixelY, baseShadeOf(relief))
        }
    }
    return base
}

fun paintStageDiff(spec: StageDiffSpec): StageDiffRender {
    val plotWidth = spec.columns
    val plotHeight = spec.rows
    val sheet = BufferedImage(plotWidth, plotHeight + LEGEND_STRIP_HEIGHT_PX, BufferedImage.TYPE_INT_RGB)
    val canvas = sheet.createGraphics()
    canvas.drawImage(paintTopDownBase(spec), 0, 0, null)
    val diffMask = tintDiffCells(sheet, spec)
    val overlay = paintOverlay(spec)
    val overlayMask = alphaMaskOf(overlay)
    canvas.drawImage(overlay, 0, 0, null)
    paintLegendStrip(canvas, spec, plotWidth, plotHeight)
    canvas.dispose()
    return StageDiffRender(sheet, plotWidth, plotHeight, diffMask, overlayMask)
}

private fun tintDiffCells(sheet: BufferedImage, spec: StageDiffSpec): BooleanArray {
    val diffMask = BooleanArray(spec.columns * spec.rows)
    if (spec.maxAbsDeltaMeters == 0.0) return diffMask
    for (pixelY in 0 until spec.rows) {
        val row = spec.rows - 1 - pixelY
        for (pixelX in 0 until spec.columns) {
            val delta = spec.deltaMeters[row * spec.columns + pixelX]
            if (delta == 0.0f) continue
            diffMask[pixelY * spec.columns + pixelX] = true
            val ramp = if (delta < 0.0f) CUT_RAMP_RGB else FILL_RAMP_RGB
            val tint = MIN_DIFF_TINT + (1.0f - MIN_DIFF_TINT) * (abs(delta) / spec.maxAbsDeltaMeters.toFloat())
            sheet.setRGB(pixelX, pixelY, lerpRgb(sheet.getRGB(pixelX, pixelY), ramp, min(1.0f, tint)))
        }
    }
    return diffMask
}

private fun baseShadeOf(relief: Float): Int {
    val grey = (78 + relief * 56).toInt()
    return Color(grey, grey + 10, grey).rgb
}

private fun lerpRgb(fromRgb: Int, toward: Triple<Int, Int, Int>, t: Float): Int {
    val from = Color(fromRgb)
    return Color(
        (from.red + (toward.first - from.red) * t).toInt(),
        (from.green + (toward.second - from.green) * t).toInt(),
        (from.blue + (toward.third - from.blue) * t).toInt(),
    ).rgb
}

private fun paintOverlay(spec: StageDiffSpec): BufferedImage {
    val overlay = BufferedImage(spec.columns, spec.rows, BufferedImage.TYPE_INT_ARGB)
    val canvas = overlay.createGraphics()
    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    for (movement in spec.movements) {
        val from = regionCenterPx(spec, movement.fromCutRegion)
        val to = regionCenterPx(spec, movement.toFillRegion)
        drawArrow(canvas, from, to, strokeWidthFor(movement.looseCubicYards))
        drawHaloedText(
            canvas,
            spec.columns,
            "${ownerYards(movement.looseCubicYards)} yd3 moves here",
            (from.first + to.first) / 2,
            maxOf(16, minOf(from.second, to.second) - 14),
        )
    }
    spec.haulOff?.let { haul ->
        val from = regionCenterPx(spec, haul.fromCutRegion)
        val to = nearestEdgePx(spec, from)
        drawArrow(canvas, from, to, strokeWidthFor(haul.looseCubicYards))
        drawHaloedText(
            canvas,
            spec.columns,
            "${ownerYards(haul.looseCubicYards)} yd3 hauled away",
            (from.first + to.first) / 2,
            maxOf(16, (from.second + to.second) / 2 - 14),
        )
    }
    canvas.dispose()
    return overlay
}

private fun regionCenterPx(spec: StageDiffSpec, regionIndex: Int): Pair<Int, Int> {
    val region = spec.regions[regionIndex]
    return region.centroidColumn.toInt() to (spec.rows - 1 - region.centroidRow.toInt())
}

private fun nearestEdgePx(spec: StageDiffSpec, from: Pair<Int, Int>): Pair<Int, Int> {
    val (x, y) = from
    val toEdges = listOf(
        (0 to y) to x,
        (spec.columns - 1 to y) to (spec.columns - 1 - x),
        (x to 0) to y,
        (x to spec.rows - 1) to (spec.rows - 1 - y),
    )
    return toEdges.minByOrNull { it.second }!!.first
}

private fun strokeWidthFor(cubicYards: Double): Float =
    (3.0 + cubicYards * 0.12).coerceIn(3.0, 16.0).toFloat()

private fun drawArrow(canvas: Graphics2D, from: Pair<Int, Int>, to: Pair<Int, Int>, strokeWidth: Float) {
    canvas.color = Color(24, 24, 24)
    canvas.stroke = BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    canvas.drawLine(from.first, from.second, to.first, to.second)
    val heading = atan2((to.second - from.second).toDouble(), (to.first - from.first).toDouble())
    val headSize = 8.0 + strokeWidth
    val head = Path2D.Double()
    head.moveTo(to.first.toDouble(), to.second.toDouble())
    head.lineTo(to.first - headSize * cos(heading - 0.45), to.second - headSize * sin(heading - 0.45))
    head.lineTo(to.first - headSize * cos(heading + 0.45), to.second - headSize * sin(heading + 0.45))
    head.closePath()
    canvas.fill(head)
}

private fun drawHaloedText(canvas: Graphics2D, plotWidth: Int, text: String, centerX: Int, baselineY: Int, size: Int = 15) {
    canvas.font = Font(Font.SANS_SERIF, Font.BOLD, size)
    val textWidth = canvas.fontMetrics.stringWidth(text)
    val left = (centerX - textWidth / 2).coerceIn(4, maxOf(4, plotWidth - textWidth - 4))
    canvas.color = Color.WHITE
    for (dx in -1..1) for (dy in -1..1) {
        if (dx != 0 || dy != 0) canvas.drawString(text, left + dx, baselineY + dy)
    }
    canvas.color = Color(24, 24, 24)
    canvas.drawString(text, left, baselineY)
}

private fun alphaMaskOf(overlay: BufferedImage): BooleanArray {
    val mask = BooleanArray(overlay.width * overlay.height)
    for (y in 0 until overlay.height) {
        for (x in 0 until overlay.width) {
            mask[y * overlay.width + x] = (overlay.getRGB(x, y) ushr 24) != 0
        }
    }
    return mask
}

private fun paintLegendStrip(canvas: Graphics2D, spec: StageDiffSpec, plotWidth: Int, plotHeight: Int) {
    canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    canvas.color = Color(0x14, 0x16, 0x1B)
    canvas.fillRect(0, plotHeight, plotWidth, LEGEND_STRIP_HEIGHT_PX)
    drawColorKey(canvas, plotHeight)
    canvas.color = Color(0xE6, 0xE6, 0xEC)
    canvas.font = Font(Font.SANS_SERIF, Font.PLAIN, 15)
    val legend = spec.legend
    canvas.drawString("${legend.dugLine}   |   ${legend.placedLine}", 10, plotHeight + 58)
    canvas.drawString(legend.hauledLine, 10, plotHeight + 82)
    canvas.drawString(legend.anchorLine, 10, plotHeight + 106)
}

private fun drawColorKey(canvas: Graphics2D, plotHeight: Int) {
    canvas.color = Color(CUT_RAMP_RGB.first, CUT_RAMP_RGB.second, CUT_RAMP_RGB.third)
    canvas.fillRect(10, plotHeight + 12, 16, 16)
    canvas.color = Color(0xE6, 0xE6, 0xEC)
    canvas.font = Font(Font.SANS_SERIF, Font.BOLD, 15)
    canvas.drawString("= ground dug down", 32, plotHeight + 26)
    canvas.color = Color(FILL_RAMP_RGB.first, FILL_RAMP_RGB.second, FILL_RAMP_RGB.third)
    canvas.fillRect(220, plotHeight + 12, 16, 16)
    canvas.color = Color(0xE6, 0xE6, 0xEC)
    canvas.drawString("= ground built up", 242, plotHeight + 26)
}
