package plottwin.eyes

import java.awt.image.BufferedImage
import kotlin.math.abs

const val SKYLINE_TOLERANCE_ROWS = 6
const val SKYLINE_AGREEMENT_BOUND = 0.85
const val SKYLINE_COVERAGE_BOUND = 0.5

data class SkylineComparison(
    val comparedColumns: Int,
    val coverage: Double,
    val medianRowError: Double,
    val agreement: Double,
)

fun observedSkylineOf(image: BufferedImage, backgroundArgb: Int = BACKGROUND_ARGB): IntArray {
    val topmostRows = IntArray(image.width) { NOTHING_DRAWN }
    for (column in 0 until image.width) {
        for (row in 0 until image.height) {
            if (image.getRGB(column, row) != backgroundArgb) {
                topmostRows[column] = row
                break
            }
        }
    }
    return topmostRows
}

fun compareSkylines(observed: IntArray, predicted: IntArray): SkylineComparison {
    require(observed.size == predicted.size) { "skyline widths differ: ${observed.size} vs ${predicted.size}" }
    val rowErrors = ArrayList<Int>(observed.size)
    for (column in observed.indices) {
        if (observed[column] == NOTHING_DRAWN || predicted[column] == NOTHING_DRAWN) continue
        rowErrors.add(abs(observed[column] - predicted[column]))
    }
    if (rowErrors.isEmpty()) return SkylineComparison(0, 0.0, Double.NaN, 0.0)
    val sorted = rowErrors.sorted()
    return SkylineComparison(
        comparedColumns = rowErrors.size,
        coverage = rowErrors.size.toDouble() / observed.size,
        medianRowError = sorted[sorted.size / 2].toDouble(),
        agreement = rowErrors.count { it <= SKYLINE_TOLERANCE_ROWS }.toDouble() / rowErrors.size,
    )
}

fun skylineFindings(viewpointName: String, comparison: SkylineComparison): List<EyeFinding> = listOf(
    EyeFinding(
        check = "skyline-coverage",
        subject = viewpointName,
        measured = comparison.coverage,
        bound = SKYLINE_COVERAGE_BOUND,
        passed = comparison.coverage >= SKYLINE_COVERAGE_BOUND,
        detail = "${comparison.comparedColumns} columns carry both a drawn pixel and predicted geometry",
    ),
    EyeFinding(
        check = "skyline-agreement",
        subject = viewpointName,
        measured = comparison.agreement,
        bound = SKYLINE_AGREEMENT_BOUND,
        passed = comparison.agreement >= SKYLINE_AGREEMENT_BOUND,
        detail = "median row error ${comparison.medianRowError} px, tolerance $SKYLINE_TOLERANCE_ROWS px",
    ),
)
