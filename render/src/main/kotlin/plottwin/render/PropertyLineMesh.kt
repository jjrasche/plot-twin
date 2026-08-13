package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.ceil
import kotlin.math.hypot
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.TerrainGrid

const val PROPERTY_LINE_ENTITY_ID = "property-line"
const val PROPERTY_LINE_COLOR = "f2a83b"
const val PROPERTY_LINE_WIDTH_METERS = 1.0
const val PROPERTY_LINE_HEIGHT_METERS = 0.6f
const val PROPERTY_LINE_SAMPLE_METERS = 2.0

// The county's line standing as geometry: a low kerb laid just inside the ring, sampled along
// each edge so it follows the ground it sits on. Flat unlit colour, like a violation marker —
// it annotates the scene rather than claiming a structure was built on the line.
fun propertyLineMeshOf(ring: List<GroundPoint>, terrain: TerrainGrid, frame: SceneFrame): Scene3dMesh {
    val vertices = ArrayList<Float>()
    val triangles = ArrayList<Int>()
    for (edge in ring.indices) {
        appendKerbAlongEdge(vertices, triangles, ring[edge], ring[(edge + 1) % ring.size], ring, terrain, frame)
    }
    return Scene3dMesh(
        vertices = vertices,
        triangles = triangles,
        triColors = List(triangles.size / 3) { PROPERTY_LINE_COLOR },
    )
}

private fun appendKerbAlongEdge(
    vertices: ArrayList<Float>,
    triangles: ArrayList<Int>,
    from: GroundPoint,
    to: GroundPoint,
    ring: List<GroundPoint>,
    terrain: TerrainGrid,
    frame: SceneFrame,
) {
    val length = hypot(to.east.value - from.east.value, to.north.value - from.north.value)
    if (length <= 0.0) return
    val alongEast = (to.east.value - from.east.value) / length
    val alongNorth = (to.north.value - from.north.value) / length
    val inward = inwardNormalOf(alongEast, alongNorth, ring)
    val steps = ceil(length / PROPERTY_LINE_SAMPLE_METERS).toInt().coerceAtLeast(1)
    for (step in 0 until steps) {
        val near = alongEdge(from, alongEast, alongNorth, length * step / steps)
        val far = alongEdge(from, alongEast, alongNorth, length * (step + 1) / steps)
        appendKerbSegment(vertices, triangles, near, far, inward, terrain, frame)
    }
}

private fun alongEdge(from: GroundPoint, alongEast: Double, alongNorth: Double, meters: Double): GroundPoint =
    GroundPoint(
        east = Meters(from.east.value + alongEast * meters),
        north = Meters(from.north.value + alongNorth * meters),
    )

private fun appendKerbSegment(
    vertices: ArrayList<Float>,
    triangles: ArrayList<Int>,
    near: GroundPoint,
    far: GroundPoint,
    inward: Pair<Double, Double>,
    terrain: TerrainGrid,
    frame: SceneFrame,
) {
    val nearInner = offsetInward(near, inward)
    val farInner = offsetInward(far, inward)
    val base = vertices.size / 3
    appendColumn(vertices, near, terrain, frame)
    appendColumn(vertices, far, terrain, frame)
    appendColumn(vertices, nearInner, terrain, frame)
    appendColumn(vertices, farInner, terrain, frame)
    val nearOuterFoot = base
    val nearOuterTop = base + 1
    val farOuterFoot = base + 2
    val farOuterTop = base + 3
    val nearInnerFoot = base + 4
    val nearInnerTop = base + 5
    val farInnerFoot = base + 6
    val farInnerTop = base + 7
    triangles.addAll(listOf(nearOuterTop, farOuterTop, farInnerTop, nearOuterTop, farInnerTop, nearInnerTop))
    triangles.addAll(listOf(nearOuterFoot, farOuterFoot, farOuterTop, nearOuterFoot, farOuterTop, nearOuterTop))
    triangles.addAll(listOf(nearInnerFoot, farInnerFoot, farInnerTop, nearInnerFoot, farInnerTop, nearInnerTop))
}

private fun appendColumn(vertices: ArrayList<Float>, point: GroundPoint, terrain: TerrainGrid, frame: SceneFrame) {
    val ground = groundHeightAt(terrain, clampedToGrid(point, terrain))
    vertices.addAll(listOf(frame.sceneX(point.east.value), ground, frame.sceneZ(point.north.value)))
    vertices.addAll(listOf(frame.sceneX(point.east.value), ground + PROPERTY_LINE_HEIGHT_METERS, frame.sceneZ(point.north.value)))
}

private fun offsetInward(point: GroundPoint, inward: Pair<Double, Double>): GroundPoint = GroundPoint(
    east = Meters(point.east.value + inward.first * PROPERTY_LINE_WIDTH_METERS),
    north = Meters(point.north.value + inward.second * PROPERTY_LINE_WIDTH_METERS),
)

private fun clampedToGrid(point: GroundPoint, terrain: TerrainGrid): GroundPoint = GroundPoint(
    east = Meters(point.east.value.coerceIn(0.0, terrain.columns * terrain.cellSize.value - 1e-6)),
    north = Meters(point.north.value.coerceIn(0.0, terrain.rows * terrain.cellSize.value - 1e-6)),
)

// The interior lies left of every directed edge of a counter-clockwise ring and right of a
// clockwise one, so the winding decides which way the kerb steps off the line.
private fun inwardNormalOf(alongEast: Double, alongNorth: Double, ring: List<GroundPoint>): Pair<Double, Double> =
    if (isCounterClockwise(ring)) -alongNorth to alongEast else alongNorth to -alongEast

fun isCounterClockwise(ring: List<GroundPoint>): Boolean =
    ring.indices.sumOf { vertex ->
        val next = ring[(vertex + 1) % ring.size]
        ring[vertex].east.value * next.north.value - next.east.value * ring[vertex].north.value
    } > 0.0
