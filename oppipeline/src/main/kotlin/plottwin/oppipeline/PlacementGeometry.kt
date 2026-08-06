package plottwin.oppipeline

import kotlin.math.hypot
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

internal fun centroidOf(footprint: List<GroundPoint>): GroundPoint = GroundPoint(
    east = Meters(footprint.sumOf { it.east.value } / footprint.size),
    north = Meters(footprint.sumOf { it.north.value } / footprint.size),
)

internal fun isInsideRegion(point: GroundPoint, regionRing: List<GroundPoint>): Boolean {
    var isInside = false
    var previousVertex = regionRing.size - 1
    for (vertex in regionRing.indices) {
        if (edgeCrossesRay(point, regionRing[vertex], regionRing[previousVertex])) isInside = !isInside
        previousVertex = vertex
    }
    return isInside
}

internal fun distanceToSegment(point: GroundPoint, segmentStart: GroundPoint, segmentEnd: GroundPoint): Double {
    val eastSpan = segmentEnd.east.value - segmentStart.east.value
    val northSpan = segmentEnd.north.value - segmentStart.north.value
    val lengthSquared = eastSpan * eastSpan + northSpan * northSpan
    if (lengthSquared == 0.0) return distanceBetween(point, segmentStart)
    val towardPoint =
        (point.east.value - segmentStart.east.value) * eastSpan +
            (point.north.value - segmentStart.north.value) * northSpan
    val alongFraction = (towardPoint / lengthSquared).coerceIn(0.0, 1.0)
    val closest = GroundPoint(
        east = Meters(segmentStart.east.value + alongFraction * eastSpan),
        north = Meters(segmentStart.north.value + alongFraction * northSpan),
    )
    return distanceBetween(point, closest)
}

private fun distanceBetween(from: GroundPoint, to: GroundPoint): Double =
    hypot(from.east.value - to.east.value, from.north.value - to.north.value)

private fun edgeCrossesRay(point: GroundPoint, vertex: GroundPoint, previousVertex: GroundPoint): Boolean {
    val vertexNorth = vertex.north.value
    val previousNorth = previousVertex.north.value
    if ((vertexNorth > point.north.value) == (previousNorth > point.north.value)) return false
    val rayFraction = (point.north.value - vertexNorth) / (previousNorth - vertexNorth)
    val eastAtRay = vertex.east.value + rayFraction * (previousVertex.east.value - vertex.east.value)
    return point.east.value < eastAtRay
}
