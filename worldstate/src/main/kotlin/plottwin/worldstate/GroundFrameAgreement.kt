package plottwin.worldstate

import kotlin.math.ceil

const val CELL_SNAP_EPSILON = 1e-9

class LogFrameDisagreement(reason: String) : IllegalStateException(
    "the log's rows disagree on the ground frame, so its plot-local coordinates mean two things: $reason"
)

data class GridExtent(val columns: Int, val rows: Int)

fun cellsSpanning(spanMeters: Double, cellSize: Double): Int =
    ceil(spanMeters / cellSize - CELL_SNAP_EPSILON).toInt()

fun gridExtentOf(boundary: ParcelBoundaryRow, cellSize: Meters): GridExtent = GridExtent(
    columns = cellsSpanning(boundary.ring.maxOf { it.east.value }, cellSize.value),
    rows = cellsSpanning(boundary.ring.maxOf { it.north.value }, cellSize.value),
)

// A recut grid moves the origin and silently relocates every entity already logged.
fun requireOneGroundFrame(log: List<LoggedRow>) {
    val declared = log.mapNotNull(::declaredFrameOf).distinct()
    if (declared.size > 1) throw LogFrameDisagreement("frames declared: $declared")
    val boundary = log.mapNotNull { it.row as? ParcelBoundaryRow }.lastOrNull() ?: return
    log.mapNotNull { it.row as? BaseTerrainRow }.forEach { base -> requireExtentMatches(boundary, base) }
}

private fun declaredFrameOf(logged: LoggedRow): GroundFrame? = when (val row = logged.row) {
    is ParcelBoundaryRow -> row.frame
    is BaseTerrainRow -> row.frame
    else -> null
}

private fun requireExtentMatches(boundary: ParcelBoundaryRow, base: BaseTerrainRow) {
    val expected = gridExtentOf(boundary, base.cellSize)
    val actual = GridExtent(base.columns, base.rows)
    if (expected != actual) {
        throw LogFrameDisagreement(
            "parcel ${boundary.parcelId} spans $expected cells at ${base.cellSize.value} m, " +
                "but the base terrain is $actual"
        )
    }
}
