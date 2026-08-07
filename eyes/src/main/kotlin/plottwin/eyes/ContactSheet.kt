package plottwin.eyes

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

const val CONTACT_SHEET_COLUMNS = 3
const val TILE_LABEL_HEIGHT = 34
const val TILE_PADDING = 8

fun writeContactSheet(inspections: List<ViewpointInspection>, destination: File): File {
    require(inspections.isNotEmpty()) { "a contact sheet needs at least one viewpoint" }
    val tileWidth = inspections.first().image.width
    val tileHeight = inspections.first().image.height
    val rows = (inspections.size + CONTACT_SHEET_COLUMNS - 1) / CONTACT_SHEET_COLUMNS
    val sheet = BufferedImage(
        CONTACT_SHEET_COLUMNS * (tileWidth + TILE_PADDING) + TILE_PADDING,
        rows * (tileHeight + TILE_LABEL_HEIGHT + TILE_PADDING) + TILE_PADDING,
        BufferedImage.TYPE_INT_RGB,
    )
    val canvas = sheet.createGraphics()
    canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    canvas.color = Color(0x11, 0x13, 0x17)
    canvas.fillRect(0, 0, sheet.width, sheet.height)
    inspections.forEachIndexed { index, inspection ->
        val column = index % CONTACT_SHEET_COLUMNS
        val row = index / CONTACT_SHEET_COLUMNS
        val left = TILE_PADDING + column * (tileWidth + TILE_PADDING)
        val top = TILE_PADDING + row * (tileHeight + TILE_LABEL_HEIGHT + TILE_PADDING)
        canvas.drawImage(inspection.image, left, top, null)
        drawTileLabel(canvas, inspection, left, top + tileHeight, tileWidth)
    }
    canvas.dispose()
    destination.parentFile?.mkdirs()
    ImageIO.write(sheet, "PNG", destination)
    return destination
}

private fun drawTileLabel(
    canvas: java.awt.Graphics2D,
    inspection: ViewpointInspection,
    left: Int,
    top: Int,
    tileWidth: Int,
) {
    val failures = failedFindings(inspection.findings)
    canvas.color = if (failures.isEmpty()) Color(0x1C, 0x2A, 0x1C) else Color(0x3A, 0x18, 0x18)
    canvas.fillRect(left, top, tileWidth, TILE_LABEL_HEIGHT)
    canvas.color = Color(0xE6, 0xE6, 0xEC)
    canvas.font = Font(Font.SANS_SERIF, Font.BOLD, 13)
    canvas.drawString(inspection.viewpoint.name, left + 6, top + 15)
    canvas.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
    canvas.color = if (failures.isEmpty()) Color(0x8F, 0xD4, 0x9A) else Color(0xFF, 0x9A, 0x8A)
    canvas.drawString(labelSummaryOf(inspection), left + 6, top + 29)
}

private fun labelSummaryOf(inspection: ViewpointInspection): String {
    val failures = failedFindings(inspection.findings)
    val advisories = advisoryFindings(inspection.findings)
    val gated = inspection.findings.size - advisories.size
    if (failures.isEmpty()) return "$gated checks ok, ${advisories.size} advisory"
    return failures.joinToString(", ") { "${it.check} %.2f".format(it.measured) }
}

fun findingsReportOf(inspections: List<ViewpointInspection>): String =
    inspections.joinToString("\n") { inspection ->
        (listOf("[${inspection.viewpoint.name}]") + inspection.findings.map { "  " + it.line() }).joinToString("\n")
    }
