package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

const val SHADOW_BIN_COUNT = 36
const val SHADOW_CONTRAST_FLOOR = 0.08
const val SHADOW_AZIMUTH_TOLERANCE_DEGREES = 20.0
const val SHADOW_PROBE_METERS = 6.0f

data class ShadowEstimate(val screenRadians: Double, val contrast: Double) {
    val hasSignal: Boolean get() = contrast >= SHADOW_CONTRAST_FLOOR
}

fun luminanceAt(image: BufferedImage, column: Int, row: Int): Double {
    val argb = image.getRGB(column, row)
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

fun estimateShadowDirection(
    image: BufferedImage,
    centerX: Double,
    centerY: Double,
    innerRadius: Double,
    outerRadius: Double,
): ShadowEstimate {
    val binTotals = DoubleArray(SHADOW_BIN_COUNT)
    val binCounts = IntArray(SHADOW_BIN_COUNT)
    val firstColumn = maxOf(0, (centerX - outerRadius).toInt())
    val lastColumn = minOf(image.width - 1, (centerX + outerRadius).toInt())
    val firstRow = maxOf(0, (centerY - outerRadius).toInt())
    val lastRow = minOf(image.height - 1, (centerY + outerRadius).toInt())
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val offsetX = column - centerX
            val offsetY = row - centerY
            val reach = hypot(offsetX, offsetY)
            if (reach < innerRadius || reach > outerRadius) continue
            if (image.getRGB(column, row) == BACKGROUND_ARGB) continue
            val bin = binOf(atan2(offsetY, offsetX))
            binTotals[bin] += luminanceAt(image, column, row)
            binCounts[bin]++
        }
    }
    val binMeans = DoubleArray(SHADOW_BIN_COUNT) { if (binCounts[it] == 0) Double.NaN else binTotals[it] / binCounts[it] }
    val occupied = binMeans.filter { !it.isNaN() }
    if (occupied.size < SHADOW_BIN_COUNT / 2) return ShadowEstimate(Double.NaN, 0.0)
    val overallMean = occupied.average()
    if (overallMean <= 0.0) return ShadowEstimate(Double.NaN, 0.0)
    var darknessX = 0.0
    var darknessY = 0.0
    for (bin in 0 until SHADOW_BIN_COUNT) {
        if (binMeans[bin].isNaN()) continue
        val darkness = (overallMean - binMeans[bin]).coerceAtLeast(0.0)
        val angle = binAngleOf(bin)
        darknessX += darkness * cos(angle)
        darknessY += darkness * sin(angle)
    }
    return ShadowEstimate(
        screenRadians = atan2(darknessY, darknessX),
        contrast = (overallMean - occupied.min()) / overallMean,
    )
}

private fun binOf(radians: Double): Int {
    val turned = (radians + 2 * PI) % (2 * PI)
    return ((turned / (2 * PI)) * SHADOW_BIN_COUNT).toInt().coerceIn(0, SHADOW_BIN_COUNT - 1)
}

private fun binAngleOf(bin: Int): Double = (bin + 0.5) * (2 * PI) / SHADOW_BIN_COUNT

fun shadowDirectionOf(sunAzimuthDegrees: Double): Vec3 {
    val shadowAzimuth = Math.toRadians(sunAzimuthDegrees + 180.0)
    return Vec3(sin(shadowAzimuth).toFloat(), 0f, cos(shadowAzimuth).toFloat())
}

fun expectedShadowScreenRadians(
    projector: ScreenProjector,
    groundPoint: Vec3,
    sunAzimuthDegrees: Double,
): Double? {
    val origin = projector.project(groundPoint)
    val tip = projector.project(groundPoint + shadowDirectionOf(sunAzimuthDegrees) * SHADOW_PROBE_METERS)
    if (!origin.visible || !tip.visible) return null
    return atan2((tip.y - origin.y).toDouble(), (tip.x - origin.x).toDouble())
}

fun angleErrorDegrees(left: Double, right: Double): Double {
    val difference = abs(left - right) % (2 * PI)
    return Math.toDegrees(if (difference > PI) 2 * PI - difference else difference)
}

// Advisory until the renderer grows a sun pass: scene3d tints terrain by elevation, so the
// darkest direction around a point tracks the downhill slope, not the light. See TASKS.md.
fun shadowFinding(
    subject: String,
    estimate: ShadowEstimate,
    expectedScreenRadians: Double?,
    advisory: Boolean,
): EyeFinding {
    if (expectedScreenRadians == null) {
        return EyeFinding(
            check = "shadow-direction",
            subject = subject,
            measured = estimate.contrast,
            bound = SHADOW_CONTRAST_FLOOR,
            passed = false,
            detail = "the sampled ground point does not project into this view",
            advisory = advisory,
        )
    }
    if (!estimate.hasSignal) {
        return EyeFinding(
            check = "shadow-direction",
            subject = subject,
            measured = estimate.contrast,
            bound = SHADOW_CONTRAST_FLOOR,
            passed = false,
            detail = "no directional darkening above the floor — no shadow to compare with the sun",
            advisory = advisory,
        )
    }
    val error = angleErrorDegrees(estimate.screenRadians, expectedScreenRadians)
    return EyeFinding(
        check = "shadow-direction",
        subject = subject,
        measured = error,
        bound = SHADOW_AZIMUTH_TOLERANCE_DEGREES,
        passed = error <= SHADOW_AZIMUTH_TOLERANCE_DEGREES,
        detail = "contrast %.3f, image says %.1f deg, sun says %.1f deg".format(
            estimate.contrast,
            Math.toDegrees(estimate.screenRadians),
            Math.toDegrees(expectedScreenRadians),
        ),
        advisory = advisory,
    )
}
