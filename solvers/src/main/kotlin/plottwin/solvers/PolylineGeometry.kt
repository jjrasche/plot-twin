package plottwin.solvers

import kotlin.math.hypot
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

internal data class Pinch(val onPath: GroundPoint, val distance: Double)

internal fun distanceBetween(from: GroundPoint, to: GroundPoint): Double =
    hypot(from.east.value - to.east.value, from.north.value - to.north.value)

internal fun closestPointOnSegment(segmentStart: GroundPoint, segmentEnd: GroundPoint, target: GroundPoint): GroundPoint {
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

internal fun pinchBetweenSegments(
    pathStart: GroundPoint,
    pathEnd: GroundPoint,
    edgeStart: GroundPoint,
    edgeEnd: GroundPoint,
): Pinch {
    val crossing = segmentCrossing(pathStart, pathEnd, edgeStart, edgeEnd)
    if (crossing != null) return Pinch(crossing, 0.0)
    val endpointCandidates = listOf(
        pinchThroughPathPoint(closestPointOnSegment(pathStart, pathEnd, edgeStart), edgeStart),
        pinchThroughPathPoint(closestPointOnSegment(pathStart, pathEnd, edgeEnd), edgeEnd),
        pinchThroughPathPoint(pathStart, closestPointOnSegment(edgeStart, edgeEnd, pathStart)),
        pinchThroughPathPoint(pathEnd, closestPointOnSegment(edgeStart, edgeEnd, pathEnd)),
    )
    return tightestOf(endpointCandidates)
}

internal fun isInsidePolygon(point: GroundPoint, ring: List<GroundPoint>): Boolean {
    var isInside = false
    var previousVertex = ring.size - 1
    for (vertex in ring.indices) {
        if (edgeCrossesRay(point, ring[vertex], ring[previousVertex])) isInside = !isInside
        previousVertex = vertex
    }
    return isInside
}

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

private fun pinchThroughPathPoint(onPath: GroundPoint, onObstacle: GroundPoint): Pinch =
    Pinch(onPath, distanceBetween(onPath, onObstacle))

private fun tightestOf(candidates: List<Pinch>): Pinch {
    var tightest = candidates.first()
    for (candidate in candidates) {
        if (candidate.distance < tightest.distance) tightest = candidate
    }
    return tightest
}

// parametric segment intersection; collinear overlaps fall through to endpoint candidates, which yield 0
private fun segmentCrossing(
    pathStart: GroundPoint,
    pathEnd: GroundPoint,
    edgeStart: GroundPoint,
    edgeEnd: GroundPoint,
): GroundPoint? {
    val pathEastSpan = pathEnd.east.value - pathStart.east.value
    val pathNorthSpan = pathEnd.north.value - pathStart.north.value
    val edgeEastSpan = edgeEnd.east.value - edgeStart.east.value
    val edgeNorthSpan = edgeEnd.north.value - edgeStart.north.value
    val spanCross = pathEastSpan * edgeNorthSpan - pathNorthSpan * edgeEastSpan
    if (spanCross == 0.0) return null
    val betweenEast = edgeStart.east.value - pathStart.east.value
    val betweenNorth = edgeStart.north.value - pathStart.north.value
    val alongPath = (betweenEast * edgeNorthSpan - betweenNorth * edgeEastSpan) / spanCross
    val alongEdge = (betweenEast * pathNorthSpan - betweenNorth * pathEastSpan) / spanCross
    if (alongPath < 0.0 || alongPath > 1.0 || alongEdge < 0.0 || alongEdge > 1.0) return null
    return GroundPoint(
        east = Meters(pathStart.east.value + alongPath * pathEastSpan),
        north = Meters(pathStart.north.value + alongPath * pathNorthSpan),
    )
}

private fun edgeCrossesRay(point: GroundPoint, vertex: GroundPoint, previousVertex: GroundPoint): Boolean {
    val vertexNorth = vertex.north.value
    val previousNorth = previousVertex.north.value
    if ((vertexNorth > point.north.value) == (previousNorth > point.north.value)) return false
    val rayFraction = (point.north.value - vertexNorth) / (previousNorth - vertexNorth)
    val eastAtRay = vertex.east.value + rayFraction * (previousVertex.east.value - vertex.east.value)
    return point.east.value < eastAtRay
}
