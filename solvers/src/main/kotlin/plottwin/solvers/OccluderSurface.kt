package plottwin.solvers

import kotlin.math.ceil
import kotlin.math.floor
import plottwin.worldstate.isInsidePolygon
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainGrid

// What the sun actually has to get past: ground height plus whatever stands on it.
// The sweep reads only this, so a greenhouse shades a bed for the same reason a hill does.
class OccluderSurface(
    val columns: Int,
    val rows: Int,
    val cellSize: Meters,
    val heights: FloatArray,
) {
    val cellCount: Int get() = columns * rows

    fun centerOf(index: Int): GroundPoint = GroundPoint(
        east = Meters((index % columns + 0.5) * cellSize.value),
        north = Meters((index / columns + 0.5) * cellSize.value),
    )
}

fun bareGroundSurfaceOf(grid: TerrainGrid): OccluderSurface =
    OccluderSurface(grid.columns, grid.rows, grid.cellSize, grid.surfaceHeights.copyOf())

fun occluderSurfaceOf(state: CurrentState): OccluderSurface? = occluderSurfaceOf(state, Surface.Measured)

fun occluderSurfaceOf(state: CurrentState, surface: Surface): OccluderSurface? {
    val grid = state.terrainOn(surface)?.grid ?: return null
    val surface = bareGroundSurfaceOf(grid)
    state.entities.values.forEach { placed -> raiseFootprint(surface, placed) }
    return surface
}

private fun raiseFootprint(surface: OccluderSurface, placed: PlacedEntity) {
    if (placed.footprint.size < 3 || placed.height.value <= 0.0) return
    val eastBounds = placed.footprint.map { it.east.value }
    val northBounds = placed.footprint.map { it.north.value }
    val firstColumn = floor(eastBounds.min() / surface.cellSize.value).toInt().coerceAtLeast(0)
    val lastColumn = ceil(eastBounds.max() / surface.cellSize.value).toInt().coerceAtMost(surface.columns - 1)
    val firstRow = floor(northBounds.min() / surface.cellSize.value).toInt().coerceAtLeast(0)
    val lastRow = ceil(northBounds.max() / surface.cellSize.value).toInt().coerceAtMost(surface.rows - 1)
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val cell = row * surface.columns + column
            if (!isInsidePolygon(surface.centerOf(cell), placed.footprint)) continue
            surface.heights[cell] += placed.height.value.toFloat()
        }
    }
}
