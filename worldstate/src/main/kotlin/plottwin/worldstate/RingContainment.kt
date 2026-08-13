package plottwin.worldstate

// The log enforces its own boundary invariants, so ring containment is a state primitive rather
// than a solver's geometry helper: a mask the writer trusts cannot live downstream of it.
fun isInsidePolygon(point: GroundPoint, ring: List<GroundPoint>): Boolean {
    var isInside = false
    var previousVertex = ring.size - 1
    for (vertex in ring.indices) {
        if (edgeCrossesRay(point, ring[vertex], ring[previousVertex])) isInside = !isInside
        previousVertex = vertex
    }
    return isInside
}

private fun edgeCrossesRay(point: GroundPoint, vertex: GroundPoint, previousVertex: GroundPoint): Boolean {
    val vertexNorth = vertex.north.value
    val previousNorth = previousVertex.north.value
    if ((vertexNorth > point.north.value) == (previousNorth > point.north.value)) return false
    val rayFraction = (point.north.value - vertexNorth) / (previousNorth - vertexNorth)
    val eastAtRay = vertex.east.value + rayFraction * (previousVertex.east.value - vertex.east.value)
    return point.east.value < eastAtRay
}
