package plottwin.render

import kotlin.math.min
import plottwin.solvers.TerrainGrid
import plottwin.worldstate.Meters

// Decimates the RENDER grid only (solver grid stays full-res) into the batched painter's proven ~100K-triangle band.
fun downsampleForRender(terrain: TerrainGrid, factor: Int): TerrainGrid {
    require(factor >= 1) { "downsample factor must be >= 1, got $factor" }
    if (factor == 1) return terrain
    val renderColumns = (terrain.columns + factor - 1) / factor
    val renderRows = (terrain.rows + factor - 1) / factor
    val renderHeights = FloatArray(renderColumns * renderRows)
    for (row in 0 until renderRows) {
        for (column in 0 until renderColumns) {
            val sourceColumn = min(column * factor, terrain.columns - 1)
            val sourceRow = min(row * factor, terrain.rows - 1)
            renderHeights[row * renderColumns + column] =
                terrain.surfaceHeights[terrain.indexOf(sourceColumn, sourceRow)]
        }
    }
    return TerrainGrid(renderColumns, renderRows, Meters(terrain.cellSize.value * factor), renderHeights)
}
