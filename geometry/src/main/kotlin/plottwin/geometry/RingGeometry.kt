package plottwin.geometry

import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

// Rings are open here: the last vertex joins the first, and a repeated closing vertex is a
// degenerate edge that makes the ring non-simple, not a wrap.
fun ringAreaSquareMeters(ring: List<GroundPoint>): Double {
    if (ring.size < 3) return 0.0
    val twiceArea = ring.indices.sumOf { vertex ->
        val next = ring[(vertex + 1) % ring.size]
        ring[vertex].east.value * next.north.value - next.east.value * ring[vertex].north.value
    }
    return kotlin.math.abs(twiceArea) / 2.0
}

fun isSimpleRing(ring: List<GroundPoint>): Boolean {
    if (ring.size < 3) return false
    if (ring.distinct().size != ring.size) return false
    for (edge in ring.indices) {
        for (other in (edge + 2) until ring.size) {
            if (edge == 0 && other == ring.size - 1) continue
            if (segmentsCross(ring[edge], ring[(edge + 1) % ring.size], ring[other], ring[(other + 1) % ring.size])) {
                return false
            }
        }
    }
    return true
}

fun clipRingToBox(ring: List<GroundPoint>, southwest: GroundPoint, northeast: GroundPoint): List<GroundPoint> =
    boxBoundsOf(southwest, northeast).fold(ring) { clipped, bound -> clipRingToHalfPlane(clipped, bound) }

private enum class Axis { EAST, NORTH }

private class AxisBound(val axis: Axis, val bound: Double, val keepsAbove: Boolean) {

    fun keeps(point: GroundPoint): Boolean =
        if (keepsAbove) coordinateOf(point) >= bound else coordinateOf(point) <= bound

    fun crossingBetween(from: GroundPoint, to: GroundPoint): GroundPoint {
        val span = coordinateOf(to) - coordinateOf(from)
        val fraction = if (span == 0.0) 0.0 else (bound - coordinateOf(from)) / span
        return GroundPoint(
            east = Meters(from.east.value + fraction * (to.east.value - from.east.value)),
            north = Meters(from.north.value + fraction * (to.north.value - from.north.value)),
        )
    }

    private fun coordinateOf(point: GroundPoint): Double =
        if (axis == Axis.EAST) point.east.value else point.north.value
}

private fun boxBoundsOf(southwest: GroundPoint, northeast: GroundPoint): List<AxisBound> = listOf(
    AxisBound(Axis.EAST, southwest.east.value, keepsAbove = true),
    AxisBound(Axis.EAST, northeast.east.value, keepsAbove = false),
    AxisBound(Axis.NORTH, southwest.north.value, keepsAbove = true),
    AxisBound(Axis.NORTH, northeast.north.value, keepsAbove = false),
)

private fun clipRingToHalfPlane(ring: List<GroundPoint>, bound: AxisBound): List<GroundPoint> {
    if (ring.isEmpty()) return ring
    val clipped = ArrayList<GroundPoint>(ring.size + 2)
    for (vertex in ring.indices) {
        val from = ring[(vertex + ring.size - 1) % ring.size]
        val to = ring[vertex]
        if (bound.keeps(to)) {
            if (!bound.keeps(from)) clipped += bound.crossingBetween(from, to)
            clipped += to
        } else if (bound.keeps(from)) {
            clipped += bound.crossingBetween(from, to)
        }
    }
    return clipped
}

private fun segmentsCross(
    firstStart: GroundPoint,
    firstEnd: GroundPoint,
    secondStart: GroundPoint,
    secondEnd: GroundPoint,
): Boolean {
    val firstAgainstSecondStart = turnOf(firstStart, firstEnd, secondStart)
    val firstAgainstSecondEnd = turnOf(firstStart, firstEnd, secondEnd)
    val secondAgainstFirstStart = turnOf(secondStart, secondEnd, firstStart)
    val secondAgainstFirstEnd = turnOf(secondStart, secondEnd, firstEnd)
    return firstAgainstSecondStart * firstAgainstSecondEnd < 0 &&
        secondAgainstFirstStart * secondAgainstFirstEnd < 0
}

private fun turnOf(from: GroundPoint, through: GroundPoint, to: GroundPoint): Int {
    val cross = (through.east.value - from.east.value) * (to.north.value - from.north.value) -
        (through.north.value - from.north.value) * (to.east.value - from.east.value)
    return when {
        cross > 0.0 -> 1
        cross < 0.0 -> -1
        else -> 0
    }
}
