package plottwin.eyes

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

const val QUESTION_HEADING_HEIGHT = 64
const val PANEL_LABEL_HEIGHT = 46
const val SHEET_PADDING = 10

// One option under one question: the frame, what it IS, and what choosing it COSTS. The cost is
// not decoration - a sheet that shows only the upside asks the reader to rule on half a trade.
data class TastePanel(val option: String, val cost: String, val image: BufferedImage)

// The options for one question, plus the statement of what was held still across them. Everything
// but the one variable must be constant inside a question or the comparison measures two things.
data class TasteQuestion(
    val question: String,
    val heldConstant: String,
    val note: String,
    val panels: List<TastePanel>,
)

// One sheet, one sitting: each question is a row of its own options, so three decisions read as
// three rows rather than as nine unrelated pictures.
fun writeTasteSheet(questions: List<TasteQuestion>, destination: File): File {
    require(questions.isNotEmpty()) { "a taste sheet needs at least one question" }
    val tileWidth = questions.first().panels.first().image.width
    val tileHeight = questions.first().panels.first().image.height
    val columns = questions.maxOf { it.panels.size }
    val sheet = BufferedImage(
        columns * (tileWidth + SHEET_PADDING) + SHEET_PADDING,
        questions.size * (QUESTION_HEADING_HEIGHT + tileHeight + PANEL_LABEL_HEIGHT + SHEET_PADDING) + SHEET_PADDING,
        BufferedImage.TYPE_INT_RGB,
    )
    val canvas = sheet.createGraphics()
    canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    canvas.color = Color(0x11, 0x13, 0x17)
    canvas.fillRect(0, 0, sheet.width, sheet.height)
    var top = SHEET_PADDING
    for (question in questions) {
        drawQuestionHeading(canvas, question, top, sheet.width)
        drawPanelRow(canvas, question, top + QUESTION_HEADING_HEIGHT, tileWidth, tileHeight)
        top += QUESTION_HEADING_HEIGHT + tileHeight + PANEL_LABEL_HEIGHT + SHEET_PADDING
    }
    canvas.dispose()
    destination.parentFile?.mkdirs()
    ImageIO.write(sheet, "PNG", destination)
    return destination
}

private fun drawQuestionHeading(canvas: Graphics2D, question: TasteQuestion, top: Int, width: Int) {
    canvas.color = Color(0x1B, 0x20, 0x2B)
    canvas.fillRect(SHEET_PADDING, top, width - 2 * SHEET_PADDING, QUESTION_HEADING_HEIGHT)
    canvas.color = Color(0xF0, 0xF0, 0xF6)
    canvas.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
    canvas.drawString(question.question, SHEET_PADDING + 10, top + 20)
    canvas.color = Color(0x9A, 0xA6, 0xBC)
    canvas.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
    canvas.drawString("held constant: ${question.heldConstant}", SHEET_PADDING + 10, top + 38)
    canvas.color = Color(0xE8, 0xB0, 0x74)
    canvas.drawString(question.note, SHEET_PADDING + 10, top + 56)
}

private fun drawPanelRow(canvas: Graphics2D, question: TasteQuestion, top: Int, tileWidth: Int, tileHeight: Int) {
    question.panels.forEachIndexed { column, panel ->
        val left = SHEET_PADDING + column * (tileWidth + SHEET_PADDING)
        canvas.drawImage(panel.image, left, top, null)
        canvas.color = Color(0x21, 0x24, 0x2C)
        canvas.fillRect(left, top + tileHeight, tileWidth, PANEL_LABEL_HEIGHT)
        canvas.color = Color(0xE6, 0xE6, 0xEC)
        canvas.font = Font(Font.SANS_SERIF, Font.BOLD, 13)
        canvas.drawString(panel.option, left + 6, top + tileHeight + 18)
        canvas.color = Color(0xE8, 0xB0, 0x74)
        canvas.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        canvas.drawString("costs: ${panel.cost}", left + 6, top + tileHeight + 36)
    }
}
