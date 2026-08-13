package plottwin.worldstate

class ParcelMask(val columns: Int, val rows: Int, val cellSize: Meters, private val inside: BooleanArray) {

    init {
        require(inside.size == columns * rows) {
            "expected ${columns * rows} mask cells for ${columns}x$rows, got ${inside.size}"
        }
    }

    val insideCellCount: Int get() = inside.count { it }

    val insideAreaSquareMeters: Double get() = insideCellCount * cellSize.value * cellSize.value

    fun isInsideBoundary(cell: Int): Boolean = inside[cell]

    fun isInsideBoundary(column: Int, row: Int): Boolean = inside[row * columns + column]

    fun centerOf(column: Int, row: Int): GroundPoint = GroundPoint(
        east = Meters((column + 0.5) * cellSize.value),
        north = Meters((row + 0.5) * cellSize.value),
    )
}

// Derived, never stored: a second copy of the property line could disagree with the ring the
// county gave us, and then no reader could tell which one the plot is.
fun parcelMaskOf(boundary: ParcelBoundaryRow, columns: Int, rows: Int, cellSize: Meters): ParcelMask {
    val inside = BooleanArray(columns * rows)
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val center = GroundPoint(
                east = Meters((column + 0.5) * cellSize.value),
                north = Meters((row + 0.5) * cellSize.value),
            )
            inside[row * columns + column] = isInsidePolygon(center, boundary.ring)
        }
    }
    return ParcelMask(columns, rows, cellSize, inside)
}

fun parcelMaskOf(boundary: ParcelBoundaryRow, grid: TerrainGrid): ParcelMask =
    parcelMaskOf(boundary, grid.columns, grid.rows, grid.cellSize)
