package plottwin.worldstate

// Incremental so the writer's boundary check costs one pass over rows it has not yet seen, not a
// whole re-projection per append.
class BoundaryWatch {

    private var watchedThroughSeq: Long = WorldLog.BEFORE_FIRST_SEQ
    private var boundary: ParcelBoundaryRow? = null
    private var gridShape: GridShape? = null
    private var mask: ParcelMask? = null

    fun maskOver(readRowsAfter: (Long) -> List<LoggedRow>): ParcelMask? {
        catchUp(readRowsAfter)
        val line = boundary ?: return null
        val shape = gridShape ?: return null
        return mask ?: parcelMaskOf(line, shape.columns, shape.rows, shape.cellSize).also { mask = it }
    }

    private fun catchUp(readRowsAfter: (Long) -> List<LoggedRow>) {
        val arrived = readRowsAfter(watchedThroughSeq)
        arrived.forEach { logged -> absorb(logged.row) }
        watchedThroughSeq = arrived.lastOrNull()?.seq ?: watchedThroughSeq
    }

    private fun absorb(row: WorldRow) {
        when (row) {
            is ParcelBoundaryRow -> {
                boundary = row
                mask = null
            }
            is BaseTerrainRow -> {
                gridShape = GridShape(row.columns, row.rows, row.cellSize)
                mask = null
            }
            else -> Unit
        }
    }

    private data class GridShape(val columns: Int, val rows: Int, val cellSize: Meters)
}
