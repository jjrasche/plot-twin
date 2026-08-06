package plottwin.eyes

import ai.factoredui.compose.math.Camera
import ai.factoredui.compose.math.ProjectedPoint
import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.math.project
import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt



const val NOTHING_DRAWN = -1

class ScreenProjector(camera: Camera, val width: Int, val height: Int) {
    private val view = camera.viewMatrix()
    private val projection = camera.projectionMatrix(width.toFloat() / height.toFloat())

    fun project(point: Vec3): ProjectedPoint =
        point.project(view, projection, width.toFloat(), height.toFloat())
}

fun verticesOf(mesh: Scene3dMesh): List<Vec3> {
    val points = ArrayList<Vec3>(mesh.vertices.size / 3)
    var index = 0
    while (index + 2 < mesh.vertices.size) {
        points.add(Vec3(mesh.vertices[index], mesh.vertices[index + 1], mesh.vertices[index + 2]))
        index += 3
    }
    return points
}

fun edgesOf(mesh: Scene3dMesh): List<Pair<Int, Int>> =
    if (mesh.gridCellsX > 0 && mesh.gridCellsZ > 0) latticeEdgesOf(mesh.gridCellsX, mesh.gridCellsZ)
    else triangleEdgesOf(mesh.triangles)

private fun latticeEdgesOf(cellsX: Int, cellsZ: Int): List<Pair<Int, Int>> {
    val perRow = cellsX + 1
    val edges = ArrayList<Pair<Int, Int>>(2 * perRow * (cellsZ + 1))
    for (row in 0..cellsZ) {
        for (column in 0..cellsX) {
            val vertex = row * perRow + column
            if (column < cellsX) edges.add(vertex to vertex + 1)
            if (row < cellsZ) edges.add(vertex to vertex + perRow)
        }
    }
    return edges
}

private fun triangleEdgesOf(triangles: List<Int>): List<Pair<Int, Int>> {
    val edges = ArrayList<Pair<Int, Int>>(triangles.size)
    var corner = 0
    while (corner + 2 < triangles.size) {
        val a = triangles[corner]
        val b = triangles[corner + 1]
        val c = triangles[corner + 2]
        edges.add(a to b)
        edges.add(b to c)
        edges.add(c to a)
        corner += 3
    }
    return edges
}

fun predictedSkylineOf(meshes: Collection<Scene3dMesh>, projector: ScreenProjector): IntArray {
    val topmostRows = IntArray(projector.width) { NOTHING_DRAWN }
    for (mesh in meshes) {
        val projected = verticesOf(mesh).map { projector.project(it) }
        for ((from, to) in edgesOf(mesh)) {
            val start = projected.getOrNull(from) ?: continue
            val end = projected.getOrNull(to) ?: continue
            if (!start.visible || !end.visible) continue
            markTopmostAlongSegment(topmostRows, start, end, projector.height)
        }
    }
    return topmostRows
}

private fun markTopmostAlongSegment(topmostRows: IntArray, start: ProjectedPoint, end: ProjectedPoint, height: Int) {
    val leftmost = min(start.x, end.x)
    val rightmost = max(start.x, end.x)
    if (rightmost < 0f || leftmost >= topmostRows.size) return
    val firstColumn = max(0, ceil(leftmost).toInt())
    val lastColumn = min(topmostRows.size - 1, floor(rightmost).toInt())
    val span = end.x - start.x
    for (column in firstColumn..lastColumn) {
        val alongEdge = if (kotlin.math.abs(span) < 1e-4f) 0f else (column - start.x) / span
        val row = (start.y + (end.y - start.y) * alongEdge).roundToInt()
        if (row < 0 || row >= height) continue
        val current = topmostRows[column]
        if (current == NOTHING_DRAWN || row < current) topmostRows[column] = row
    }
}

