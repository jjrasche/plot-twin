package plottwin.render

import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.GroundPoint

// scene3d frame: x = east, y = up, z = north, centered on the plot midpoint. z ascends
// with grid row so the batched painter's regular-grid far-to-near ordering holds.
data class SceneFrame(val centerEast: Double, val centerNorth: Double) {
    fun sceneX(east: Double): Float = (east - centerEast).toFloat()

    fun sceneZ(north: Double): Float = (north - centerNorth).toFloat()
}

fun sceneFrameOf(terrain: TerrainGrid): SceneFrame = SceneFrame(
    centerEast = terrain.columns * terrain.cellSize.value / 2.0,
    centerNorth = terrain.rows * terrain.cellSize.value / 2.0,
)

fun groundHeightAt(terrain: TerrainGrid, point: GroundPoint): Float {
    val column = (point.east.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.columns - 1)
    val row = (point.north.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.rows - 1)
    return terrain.surfaceHeights[terrain.indexOf(column, row)]
}
