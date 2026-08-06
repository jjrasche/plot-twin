package plottwin.worldstate

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

    override fun equals(other: Any?): Boolean =
        other is TerrainGrid &&
            columns == other.columns &&
            rows == other.rows &&
            cellSize == other.cellSize &&
            surfaceHeights.contentEquals(other.surfaceHeights)

    override fun hashCode(): Int = ((columns * 31 + rows) * 31 + cellSize.hashCode()) * 31 + surfaceHeights.contentHashCode()
}
