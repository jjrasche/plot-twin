package plottwin.solvers

import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

class TerrainGrid(
    val columns: Int,
    val rows: Int,
    val cellSize: Meters,
    val surfaceHeights: FloatArray,
) {
    init {
        require(surfaceHeights.size == columns * rows) {
            "expected ${columns * rows} heights for ${columns}x$rows grid, got ${surfaceHeights.size}"
        }
    }

    val cellCount: Int get() = columns * rows

    fun indexOf(column: Int, row: Int): Int = row * columns + column

    fun columnOf(index: Int): Int = index % columns

    fun rowOf(index: Int): Int = index / columns

    fun centerOf(index: Int): GroundPoint = GroundPoint(
        east = Meters((columnOf(index) + 0.5) * cellSize.value),
        north = Meters((rowOf(index) + 0.5) * cellSize.value),
    )
}
