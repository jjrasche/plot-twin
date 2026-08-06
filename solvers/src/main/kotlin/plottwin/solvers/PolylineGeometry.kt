package plottwin.solvers

import plottwin.geometry.closestPointOnSegment
import plottwin.geometry.distanceBetween
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

internal data class Pinch(val onPath: GroundPoint, val distance: Double)

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
