package plottwin.geometry

import kotlin.math.hypot
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

fun distanceBetween(from: GroundPoint, to: GroundPoint): Double =
    hypot(from.east.value - to.east.value, from.north.value - to.north.value)

fun closestPointOnSegment(segmentStart: GroundPoint, segmentEnd: GroundPoint, target: GroundPoint): GroundPoint {
    val eastSpan = segmentEnd.east.value - segmentStart.east.value
    val northSpan = segmentEnd.north.value - segmentStart.north.value
    val lengthSquared = eastSpan * eastSpan + northSpan * northSpan
    if (lengthSquared == 0.0) return segmentStart
    val alongFraction = projectionFraction(segmentStart, target, eastSpan, northSpan, lengthSquared)
    return GroundPoint(
        east = Meters(segmentStart.east.value + alongFraction * eastSpan),
        north = Meters(segmentStart.north.value + alongFraction * northSpan),
    )
}

fun distanceToSegment(point: GroundPoint, segmentStart: GroundPoint, segmentEnd: GroundPoint): Double =
    distanceBetween(point, closestPointOnSegment(segmentStart, segmentEnd, point))

private fun projectionFraction(
    segmentStart: GroundPoint,
    target: GroundPoint,
    eastSpan: Double,
    northSpan: Double,
    lengthSquared: Double,
): Double {
    val towardTarget =
        (target.east.value - segmentStart.east.value) * eastSpan +
            (target.north.value - segmentStart.north.value) * northSpan
    return (towardTarget / lengthSquared).coerceIn(0.0, 1.0)
}
