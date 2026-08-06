package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.hypot
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity

const val WALKLINE_HALF_WIDTH_METERS = 0.3
const val WALKLINE_LIFT_METERS = 0.05f

private val entityColors = mapOf(
    "greenhouse" to "8fc7d4",
    "pergola" to "8a6a4b",
    "garden path" to "cfc09a",
)
private const val DEFAULT_ENTITY_COLOR = "9aa0a8"

fun entityMeshOf(entityName: String, placed: PlacedEntity, terrain: TerrainGrid, frame: SceneFrame): Scene3dMesh {
    val color = entityColors[entityName] ?: DEFAULT_ENTITY_COLOR
    return if (placed.footprint.size < 3) walklineRibbonMeshOf(placed.footprint, terrain, frame, color)
    else extrudedPrismMeshOf(placed.footprint, placed.height, terrain, frame, color)
}

private fun extrudedPrismMeshOf(
    ring: List<GroundPoint>,
    height: Meters,
    terrain: TerrainGrid,
    frame: SceneFrame,
    color: String,
): Scene3dMesh {
    val cornerCount = ring.size
    val floorHeight = groundHeightAt(terrain, footprintCentroid(ring))
    val roofHeight = floorHeight + height.value.toFloat()
    val vertices = ArrayList<Float>(cornerCount * 6)
    for (corner in ring) {
        vertices.add(frame.sceneX(corner.east.value))
        vertices.add(floorHeight)
        vertices.add(frame.sceneZ(corner.north.value))
    }
    for (corner in ring) {
        vertices.add(frame.sceneX(corner.east.value))
        vertices.add(roofHeight)
        vertices.add(frame.sceneZ(corner.north.value))
    }
    val triangles = ArrayList<Int>()
    for (corner in 0 until cornerCount) {
        val next = (corner + 1) % cornerCount
        triangles.addAll(listOf(corner, next, cornerCount + next))
        triangles.addAll(listOf(corner, cornerCount + next, cornerCount + corner))
    }
    for (fan in 1 until cornerCount - 1) {
        triangles.addAll(listOf(cornerCount, cornerCount + fan, cornerCount + fan + 1))
        triangles.addAll(listOf(0, fan + 1, fan))
    }
    return Scene3dMesh(
        vertices = vertices,
        triangles = triangles,
        triColors = List(triangles.size / 3) { color },
    )
}

private fun walklineRibbonMeshOf(
    polyline: List<GroundPoint>,
    terrain: TerrainGrid,
    frame: SceneFrame,
    color: String,
): Scene3dMesh {
    val vertices = ArrayList<Float>()
    val triangles = ArrayList<Int>()
    for (segment in 0 until polyline.size - 1) {
        val start = polyline[segment]
        val end = polyline[segment + 1]
        val firstVertex = vertices.size / 3
        for (edgePoint in ribbonCorners(start, end)) {
            vertices.add(frame.sceneX(edgePoint.east.value))
            vertices.add(groundHeightAt(terrain, edgePoint) + WALKLINE_LIFT_METERS)
            vertices.add(frame.sceneZ(edgePoint.north.value))
        }
        triangles.addAll(listOf(firstVertex, firstVertex + 1, firstVertex + 3))
        triangles.addAll(listOf(firstVertex, firstVertex + 3, firstVertex + 2))
    }
    return Scene3dMesh(
        vertices = vertices,
        triangles = triangles,
        triColors = List(triangles.size / 3) { color },
    )
}

private fun ribbonCorners(start: GroundPoint, end: GroundPoint): List<GroundPoint> {
    val eastSpan = end.east.value - start.east.value
    val northSpan = end.north.value - start.north.value
    val length = hypot(eastSpan, northSpan).coerceAtLeast(1e-9)
    val sideEast = -northSpan / length * WALKLINE_HALF_WIDTH_METERS
    val sideNorth = eastSpan / length * WALKLINE_HALF_WIDTH_METERS
    return listOf(
        GroundPoint(Meters(start.east.value - sideEast), Meters(start.north.value - sideNorth)),
        GroundPoint(Meters(start.east.value + sideEast), Meters(start.north.value + sideNorth)),
        GroundPoint(Meters(end.east.value - sideEast), Meters(end.north.value - sideNorth)),
        GroundPoint(Meters(end.east.value + sideEast), Meters(end.north.value + sideNorth)),
    )
}

private fun footprintCentroid(ring: List<GroundPoint>): GroundPoint = GroundPoint(
    east = Meters(ring.sumOf { it.east.value } / ring.size),
    north = Meters(ring.sumOf { it.north.value } / ring.size),
)
