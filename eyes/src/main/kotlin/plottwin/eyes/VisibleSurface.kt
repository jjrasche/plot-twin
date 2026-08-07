package plottwin.eyes

import ai.factoredui.compose.math.ProjectedPoint
import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

const val NO_SURFACE = -1

class VisibleSurface(val width: Int, val height: Int) {
    val nearestDepth = FloatArray(width * height) { Float.MAX_VALUE }
    val owner = IntArray(width * height) { NO_SURFACE }

    fun maskOf(ownerIndex: Int): BooleanArray = BooleanArray(width * height) { owner[it] == ownerIndex }
}

fun trianglesOf(mesh: Scene3dMesh): List<Int> =
    if (mesh.gridCellsX > 0 && mesh.gridCellsZ > 0) latticeTrianglesOf(mesh.gridCellsX, mesh.gridCellsZ)
    else mesh.triangles

private fun latticeTrianglesOf(cellsX: Int, cellsZ: Int): List<Int> {
    val perRow = cellsX + 1
    val triangles = ArrayList<Int>(cellsX * cellsZ * 6)
    for (row in 0 until cellsZ) {
        for (column in 0 until cellsX) {
            val corner = row * perRow + column
            triangles.addAll(listOf(corner, corner + 1, corner + perRow))
            triangles.addAll(listOf(corner + 1, corner + perRow + 1, corner + perRow))
        }
    }
    return triangles
}

fun rasterizeVisibleSurfaces(meshesByEntity: Map<String, Scene3dMesh>, projector: ScreenProjector): VisibleSurface {
    val surface = VisibleSurface(projector.width, projector.height)
    meshesByEntity.values.forEachIndexed { ownerIndex, mesh ->
        val projected = verticesOf(mesh).map { projector.project(it) }
        val triangles = trianglesOf(mesh)
        var corner = 0
        while (corner + 2 < triangles.size) {
            val a = projected.getOrNull(triangles[corner])
            val b = projected.getOrNull(triangles[corner + 1])
            val c = projected.getOrNull(triangles[corner + 2])
            corner += 3
            if (a == null || b == null || c == null) continue
            if (!a.visible || !b.visible || !c.visible) continue
            claimTriangle(surface, a, b, c, ownerIndex)
        }
    }
    return surface
}

fun ownerIndexOf(meshesByEntity: Map<String, Scene3dMesh>, entityName: String): Int =
    meshesByEntity.keys.indexOf(entityName)

private fun claimTriangle(
    surface: VisibleSurface,
    a: ProjectedPoint,
    b: ProjectedPoint,
    c: ProjectedPoint,
    ownerIndex: Int,
) {
    val firstColumn = max(0, floor(minOf(a.x, b.x, c.x)).toInt())
    val lastColumn = min(surface.width - 1, ceil(maxOf(a.x, b.x, c.x)).toInt())
    val firstRow = max(0, floor(minOf(a.y, b.y, c.y)).toInt())
    val lastRow = min(surface.height - 1, ceil(maxOf(a.y, b.y, c.y)).toInt())
    val area = signedArea(a, b, c)
    if (abs(area) < 1e-6f) return
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val x = column + 0.5f
            val y = row + 0.5f
            val alpha = edgeWeight(b.x, b.y, c.x, c.y, x, y) / area
            val beta = edgeWeight(c.x, c.y, a.x, a.y, x, y) / area
            val gamma = 1f - alpha - beta
            if (alpha < 0f || beta < 0f || gamma < 0f) continue
            val depth = alpha * a.depth + beta * b.depth + gamma * c.depth
            val pixel = row * surface.width + column
            if (depth >= surface.nearestDepth[pixel]) continue
            surface.nearestDepth[pixel] = depth
            surface.owner[pixel] = ownerIndex
        }
    }
}

private fun signedArea(a: ProjectedPoint, b: ProjectedPoint, c: ProjectedPoint): Float =
    edgeWeight(a.x, a.y, b.x, b.y, c.x, c.y)

private fun edgeWeight(ax: Float, ay: Float, bx: Float, by: Float, px: Float, py: Float): Float =
    (bx - ax) * (py - ay) - (by - ay) * (px - ax)
