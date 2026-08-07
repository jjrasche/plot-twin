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

fun entityMeshOf(
    entityName: String,
    placed: PlacedEntity,
    terrain: TerrainGrid,
    frame: SceneFrame,
    daylight: Daylight,
): Scene3dMesh {
    val albedo = rgbOfHex(entityColors[entityName] ?: DEFAULT_ENTITY_COLOR)
    return if (placed.footprint.size < 3) walklineRibbonMeshOf(placed.footprint, terrain, frame, albedo, daylight)
    else extrudedPrismMeshOf(placed.footprint, placed.height, terrain, frame, albedo, daylight)
}

private fun litFace(albedo: Rgb, normal: SceneDirection, daylight: Daylight): String =
    hexOf(litColor(albedo, normal, sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight))

private fun extrudedPrismMeshOf(
    ring: List<GroundPoint>,
    height: Meters,
    terrain: TerrainGrid,
    frame: SceneFrame,
    albedo: Rgb,
    daylight: Daylight,
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
    val centroid = footprintCentroid(ring)
    val triangles = ArrayList<Int>()
    val triColors = ArrayList<String>()
    for (corner in 0 until cornerCount) {
        val next = (corner + 1) % cornerCount
        triangles.addAll(listOf(corner, next, cornerCount + next))
        triangles.addAll(listOf(corner, cornerCount + next, cornerCount + corner))
        val wallFace = litFace(albedo, outwardWallNormal(ring[corner], ring[next], centroid), daylight)
        triColors.add(wallFace)
        triColors.add(wallFace)
    }
    val roofFace = litFace(albedo, SceneDirection(0f, 1f, 0f), daylight)
    val floorFace = litFace(albedo, SceneDirection(0f, -1f, 0f), daylight)
    for (fan in 1 until cornerCount - 1) {
        triangles.addAll(listOf(cornerCount, cornerCount + fan, cornerCount + fan + 1))
        triangles.addAll(listOf(0, fan + 1, fan))
        triColors.add(roofFace)
        triColors.add(floorFace)
    }
    return Scene3dMesh(vertices = vertices, triangles = triangles, triColors = triColors)
}

private fun outwardWallNormal(start: GroundPoint, end: GroundPoint, centroid: GroundPoint): SceneDirection {
    val eastSpan = end.east.value - start.east.value
    val northSpan = end.north.value - start.north.value
    val length = hypot(eastSpan, northSpan).coerceAtLeast(1e-9)
    val sideEast = (northSpan / length).toFloat()
    val sideNorth = (-eastSpan / length).toFloat()
    val towardEdgeEast = (start.east.value + end.east.value) / 2 - centroid.east.value
    val towardEdgeNorth = (start.north.value + end.north.value) / 2 - centroid.north.value
    val outward = if (sideEast * towardEdgeEast + sideNorth * towardEdgeNorth < 0) -1f else 1f
    return SceneDirection(sideEast * outward, 0f, sideNorth * outward)
}

private fun walklineRibbonMeshOf(
    polyline: List<GroundPoint>,
    terrain: TerrainGrid,
    frame: SceneFrame,
    albedo: Rgb,
    daylight: Daylight,
): Scene3dMesh {
    val vertices = ArrayList<Float>()
    val triangles = ArrayList<Int>()
    val color = litFace(albedo, SceneDirection(0f, 1f, 0f), daylight)
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
