package plottwin.eyes

import java.awt.image.BufferedImage
import kotlin.math.abs

const val SKY_COVERAGE_BOUND = 0.98
const val SKY_BANDING_LUMINANCE_BOUND = 12.0

data class SkyRegionReading(
    val coverageAboveSkyline: Double,
    val maxAdjacentLuminanceJump: Double,
    val skyPixels: Int,
    val inspectedAboveSkyline: Int,
)

fun skyMaskOf(image: BufferedImage, classifier: SkyClassifier): BooleanArray {
    val mask = BooleanArray(image.width * image.height)
    for (row in 0 until image.height) {
        for (column in 0 until image.width) {
            mask[row * image.width + column] = classifier.isSky(image.getRGB(column, row))
        }
    }
    return mask
}

fun observedSkylineOf(image: BufferedImage, classifier: SkyClassifier): IntArray {
    val topmostRows = IntArray(image.width) { NOTHING_DRAWN }
    for (column in 0 until image.width) {
        for (row in 0 until image.height) {
            if (!classifier.isSky(image.getRGB(column, row))) {
                topmostRows[column] = row
                break
            }
        }
    }
    return topmostRows
}

fun skyRegionReadingOf(image: BufferedImage, classifier: SkyClassifier, predictedSkyline: IntArray): SkyRegionReading {
    val sky = skyMaskOf(image, classifier)
    var inspected = 0
    var covered = 0
    for (column in 0 until image.width) {
        val horizonRow = if (predictedSkyline[column] == NOTHING_DRAWN) image.height else predictedSkyline[column]
        for (row in 0 until horizonRow) {
            inspected++
            if (sky[row * image.width + column]) covered++
        }
    }
    return SkyRegionReading(
        coverageAboveSkyline = if (inspected == 0) 1.0 else covered.toDouble() / inspected,
        maxAdjacentLuminanceJump = maxAdjacentSkyLuminanceJumpOf(image, sky),
        skyPixels = sky.count { it },
        inspectedAboveSkyline = inspected,
    )
}

private fun maxAdjacentSkyLuminanceJumpOf(image: BufferedImage, sky: BooleanArray): Double {
    var worstJump = 0.0
    for (row in 0 until image.height) {
        for (column in 0 until image.width) {
            if (!sky[row * image.width + column]) continue
            val luminance = luminanceAt(image, column, row)
            if (column + 1 < image.width && sky[row * image.width + column + 1]) {
                worstJump = maxOf(worstJump, abs(luminance - luminanceAt(image, column + 1, row)))
            }
            if (row + 1 < image.height && sky[(row + 1) * image.width + column]) {
                worstJump = maxOf(worstJump, abs(luminance - luminanceAt(image, column, row + 1)))
            }
        }
    }
    return worstJump
}

fun skyRegionFindings(viewpointName: String, reading: SkyRegionReading): List<EyeFinding> = listOf(
    EyeFinding(
        check = "sky-above-skyline",
        subject = viewpointName,
        measured = reading.coverageAboveSkyline,
        bound = SKY_COVERAGE_BOUND,
        passed = reading.coverageAboveSkyline >= SKY_COVERAGE_BOUND,
        detail = "${reading.inspectedAboveSkyline} pixels above the predicted skyline, ${reading.skyPixels} sky pixels in frame",
    ),
    EyeFinding(
        check = "sky-banding",
        subject = viewpointName,
        measured = reading.maxAdjacentLuminanceJump,
        bound = SKY_BANDING_LUMINANCE_BOUND,
        passed = reading.maxAdjacentLuminanceJump <= SKY_BANDING_LUMINANCE_BOUND,
        detail = "worst luminance step between adjacent sky pixels",
    ),
)
