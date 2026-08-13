package plottwin.worldstate

const val INSIDE_THE_PROPERTY_LINE_RULE = "terrain diffs stay inside the property line"

class TerrainDiffOutsideBoundary(val violations: List<RejectedViolation>) : IllegalArgumentException(
    "a terrain diff would regrade ground outside the property line: " +
        violations.joinToString("; ") { "${it.ruleName} at ${it.location} over ${it.magnitude} m2" }
)

// You cannot regrade your neighbour's land, so the mask gates the write rather than decorating it.
fun outsideBoundaryViolationsOf(diff: TerrainDiffRow, mask: ParcelMask): List<RejectedViolation> {
    var outsideCells = 0
    var firstOutside: GroundPoint? = null
    for (patchRow in 0 until diff.rows) {
        for (patchColumn in 0 until diff.columns) {
            val column = diff.firstColumn + patchColumn
            val row = diff.firstRow + patchRow
            if (column >= mask.columns || row >= mask.rows || mask.isInsideBoundary(column, row)) continue
            outsideCells++
            if (firstOutside == null) firstOutside = mask.centerOf(column, row)
        }
    }
    val location = firstOutside ?: return emptyList()
    return listOf(
        RejectedViolation(
            ruleName = INSIDE_THE_PROPERTY_LINE_RULE,
            location = location,
            magnitude = outsideCells * mask.cellSize.value * mask.cellSize.value,
        )
    )
}
