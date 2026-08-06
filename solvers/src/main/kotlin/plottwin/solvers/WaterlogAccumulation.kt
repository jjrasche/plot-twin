package plottwin.solvers

import kotlin.math.ceil
import kotlin.math.floor
import plottwin.geometry.isInsidePolygon
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.TerrainGrid

object WaterlogAccumulation : LeafSolver {
    override fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation> {
        if (constraint !is WaterlogConstraint) return emptyList()
        val terrain = world.state.terrain ?: return emptyList()
        val footprintRing = world.state.entities[constraint.entityName]?.footprint ?: return emptyList()
        if (footprintRing.size < 3) return emptyList()
        val upslopeCounts = world.upslopeFields.upslopeCellCountsOf(terrain)
        val wettestCell = wettestCellInside(terrain.grid, upslopeCounts, footprintRing)
        if (wettestCell == NO_FLOW) return emptyList()
        return listOfNotNull(excessViolation(world, terrain.grid, constraint, wettestCell, upslopeCounts[wettestCell]))
    }
}

private fun wettestCellInside(terrain: TerrainGrid, upslopeCounts: IntArray, footprintRing: List<GroundPoint>): Int {
    val eastBounds = footprintRing.map { it.east.value }
    val northBounds = footprintRing.map { it.north.value }
    val firstColumn = columnAt(eastBounds.min(), terrain).coerceAtLeast(0)
    val lastColumn = columnAt(eastBounds.max(), terrain).coerceAtMost(terrain.columns - 1)
    val firstRow = rowAt(northBounds.min(), terrain).coerceAtLeast(0)
    val lastRow = rowAt(northBounds.max(), terrain).coerceAtMost(terrain.rows - 1)

    var wettestCell = NO_FLOW
    var wettestCount = -1
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val cell = terrain.indexOf(column, row)
            if (upslopeCounts[cell] <= wettestCount) continue
            if (!isInsidePolygon(terrain.centerOf(cell), footprintRing)) continue
            wettestCell = cell
            wettestCount = upslopeCounts[cell]
        }
    }
    return wettestCell
}

private fun excessViolation(
    world: SolverWorld,
    grid: TerrainGrid,
    constraint: WaterlogConstraint,
    wettestCell: Int,
    upslopeCellCount: Int,
): Violation? {
    val cellArea = grid.cellSize.value * grid.cellSize.value
    val excessSquareMeters = upslopeCellCount * cellArea - constraint.maxUpslopeSquareMeters
    if (excessSquareMeters <= 0.0) return null
    return Violation(
        ruleName = constraint.ruleName,
        location = grid.centerOf(wettestCell),
        magnitude = excessSquareMeters,
        severity = ruleWeightOf(world.state, constraint.ruleName) * excessSquareMeters / constraint.maxUpslopeSquareMeters,
    )
}

private fun columnAt(east: Double, terrain: TerrainGrid): Int = floor(east / terrain.cellSize.value).toInt()

private fun rowAt(north: Double, terrain: TerrainGrid): Int = ceil(north / terrain.cellSize.value).toInt() - 1
