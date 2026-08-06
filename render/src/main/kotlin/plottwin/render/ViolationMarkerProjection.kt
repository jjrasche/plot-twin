package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import plottwin.worldstate.TerrainGrid
import plottwin.solvers.Violation

const val VIOLATION_MARKER_HEIGHT_METERS = 3.5f
const val VIOLATION_MARKER_COLOR = "e03b28"

fun violationMarkerMeshOf(violation: Violation, terrain: TerrainGrid, frame: SceneFrame): Scene3dMesh {
    val x = frame.sceneX(violation.location.east.value)
    val z = frame.sceneZ(violation.location.north.value)
    val base = groundHeightAt(terrain, violation.location)
    val waist = base + VIOLATION_MARKER_HEIGHT_METERS / 2f
    val radius = markerRadiusOf(violation.magnitude)
    val vertices = listOf(
        x, base, z,
        x, base + VIOLATION_MARKER_HEIGHT_METERS, z,
        x - radius, waist, z,
        x + radius, waist, z,
        x, waist, z - radius,
        x, waist, z + radius,
    )
    val triangles = listOf(
        0, 2, 4, 0, 4, 3, 0, 3, 5, 0, 5, 2,
        1, 4, 2, 1, 3, 4, 1, 5, 3, 1, 2, 5,
    )
    return Scene3dMesh(
        vertices = vertices,
        triangles = triangles,
        triColors = List(triangles.size / 3) { VIOLATION_MARKER_COLOR },
    )
}

fun markerRadiusOf(magnitude: Double): Float =
    (0.4 + 0.15 * magnitude).coerceIn(0.4, 2.0).toFloat()
