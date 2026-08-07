package plottwin.solvers

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val SUN_DISC_HALF_ANGLE_DEGREES = 0.265
const val SKY_OPENNESS_DIRECTION_COUNT = 8

/**
 * Horizon slope of every cell looking toward [sunAzimuthDegrees] — Timonen & Westerholm's
 * height-field sweep, whose hull's last edge is the tangent from the cell being swept.
 */
fun horizonSlopesToward(surface: OccluderSurface, sunAzimuthDegrees: Double): FloatArray {
    val towardSun = Math.toRadians(sunAzimuthDegrees)
    val awayEast = -sin(towardSun)
    val awayNorth = -Math.cos(towardSun)
    val slopes = FloatArray(surface.cellCount)
    if (abs(awayEast) >= abs(awayNorth)) {
        sweepLines(surface, slopes, surface.columns, surface.rows, awayNorth / awayEast, awayEast > 0, ColumnMajorLines)
    } else {
        sweepLines(surface, slopes, surface.rows, surface.columns, awayEast / awayNorth, awayNorth > 0, RowMajorLines)
    }
    return slopes
}

fun sunlitFractionsAt(surface: OccluderSurface, sun: SunRay): FloatArray {
    if (!sun.isAboveHorizon) return FloatArray(surface.cellCount)
    val horizonSlopes = horizonSlopesToward(surface, sun.azimuthDegrees)
    val altitude = Math.toRadians(sun.altitudeDegrees)
    val discHalfAngle = Math.toRadians(SUN_DISC_HALF_ANGLE_DEGREES)
    return FloatArray(surface.cellCount) { cell ->
        discAboveHorizon(altitude - atan(horizonSlopes[cell].toDouble()), discHalfAngle)
    }
}

fun skyOpennessOf(surface: OccluderSurface, directionCount: Int = SKY_OPENNESS_DIRECTION_COUNT): FloatArray {
    val blockedSum = FloatArray(surface.cellCount)
    for (direction in 0 until directionCount) {
        val slopes = horizonSlopesToward(surface, direction * 360.0 / directionCount)
        for (cell in slopes.indices) blockedSum[cell] += slopes[cell] / sqrt(1f + slopes[cell] * slopes[cell])
    }
    return FloatArray(surface.cellCount) { cell -> 1f - blockedSum[cell] / directionCount }
}

private fun discAboveHorizon(altitudeAboveHorizon: Double, discHalfAngle: Double): Float {
    val crossing = ((altitudeAboveHorizon + discHalfAngle) / (2 * discHalfAngle)).coerceIn(0.0, 1.0)
    return (crossing * crossing * (3 - 2 * crossing)).toFloat()
}

private interface LineFamily {
    fun cellOf(surface: OccluderSurface, majorIndex: Int, minorIndex: Int): Int
}

private object ColumnMajorLines : LineFamily {
    override fun cellOf(surface: OccluderSurface, majorIndex: Int, minorIndex: Int): Int =
        minorIndex * surface.columns + majorIndex
}

private object RowMajorLines : LineFamily {
    override fun cellOf(surface: OccluderSurface, majorIndex: Int, minorIndex: Int): Int =
        majorIndex * surface.columns + minorIndex
}

// minorIndex - round(gradient * majorIndex) names one line per cell, so the family partitions the grid.
private fun sweepLines(
    surface: OccluderSurface,
    slopes: FloatArray,
    majorCount: Int,
    minorCount: Int,
    gradient: Double,
    ascending: Boolean,
    lines: LineFamily,
) {
    val stepDistance = surface.cellSize.value * hypot(1.0, gradient)
    val minorOffset = IntArray(majorCount) { (gradient * it).roundToInt() }
    val hullDistance = DoubleArray(majorCount)
    val hullHeight = DoubleArray(majorCount)
    for (line in -minorOffset.max()..(minorCount - 1 - minorOffset.min())) {
        var top = -1
        for (step in 0 until majorCount) {
            val majorIndex = if (ascending) step else majorCount - 1 - step
            val minorIndex = line + minorOffset[majorIndex]
            if (minorIndex < 0 || minorIndex >= minorCount) continue
            val cell = lines.cellOf(surface, majorIndex, minorIndex)
            val distance = (if (ascending) majorIndex else -majorIndex) * stepDistance
            val height = surface.heights[cell].toDouble()
            while (top >= 1 && !holdsUpperHull(hullDistance, hullHeight, top, distance, height)) top--
            slopes[cell] = if (top < 0) 0f else tangentSlope(hullDistance[top], hullHeight[top], distance, height)
            top++
            hullDistance[top] = distance
            hullHeight[top] = height
        }
    }
}

private fun holdsUpperHull(
    hullDistance: DoubleArray,
    hullHeight: DoubleArray,
    top: Int,
    distance: Double,
    height: Double,
): Boolean {
    val spanDistance = hullDistance[top] - hullDistance[top - 1]
    val spanHeight = hullHeight[top] - hullHeight[top - 1]
    return spanDistance * (height - hullHeight[top - 1]) - spanHeight * (distance - hullDistance[top - 1]) < 0.0
}

private fun tangentSlope(occluderDistance: Double, occluderHeight: Double, distance: Double, height: Double): Float {
    val rise = occluderHeight - height
    if (rise <= 0.0) return 0f
    return (rise / (distance - occluderDistance)).toFloat()
}
