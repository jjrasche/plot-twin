package plottwin.solvers

import kotlin.math.ceil
import kotlin.math.floor
import plottwin.geometry.isInsidePolygon
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.TerrainGrid

object SunshedSweep : LeafSolver {
    override fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation> {
        if (constraint !is SunHoursConstraint) return emptyList()
        val terrain = world.state.terrainOn(world.surface) ?: return emptyList()
        val footprintRing = world.state.entities[constraint.bedName]?.footprint ?: return emptyList()
        if (footprintRing.size < 3) return emptyList()
        val directSunHours = world.sunshedFields.directSunHoursOf(world.state, world.date, world.surface) ?: return emptyList()
        val darkestCell = darkestCellInside(terrain.grid, directSunHours, footprintRing)
        if (darkestCell == NO_SUNSHED_CELL) return emptyList()
        return listOfNotNull(shortfallViolation(world, terrain.grid, constraint, darkestCell, directSunHours[darkestCell]))
    }
}

private const val NO_SUNSHED_CELL = -1

private fun darkestCellInside(terrain: TerrainGrid, directSunHours: FloatArray, footprintRing: List<GroundPoint>): Int {
    val eastBounds = footprintRing.map { it.east.value }
    val northBounds = footprintRing.map { it.north.value }
    val firstColumn = floor(eastBounds.min() / terrain.cellSize.value).toInt().coerceAtLeast(0)
    val lastColumn = ceil(eastBounds.max() / terrain.cellSize.value).toInt().coerceAtMost(terrain.columns - 1)
    val firstRow = floor(northBounds.min() / terrain.cellSize.value).toInt().coerceAtLeast(0)
    val lastRow = ceil(northBounds.max() / terrain.cellSize.value).toInt().coerceAtMost(terrain.rows - 1)

    var darkestCell = NO_SUNSHED_CELL
    var darkestHours = Float.MAX_VALUE
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val cell = terrain.indexOf(column, row)
            if (directSunHours[cell] >= darkestHours) continue
            if (!isInsidePolygon(terrain.centerOf(cell), footprintRing)) continue
            darkestCell = cell
            darkestHours = directSunHours[cell]
        }
    }
    return darkestCell
}

private fun shortfallViolation(
    world: SolverWorld,
    grid: TerrainGrid,
    constraint: SunHoursConstraint,
    darkestCell: Int,
    directSunHours: Float,
): Violation? {
    val shortfallHours = constraint.minimumDirectHours - directSunHours
    if (shortfallHours <= 0.0) return null
    return Violation(
        ruleName = constraint.ruleName,
        location = grid.centerOf(darkestCell),
        magnitude = shortfallHours,
        severity = ruleWeightOf(world.state, constraint.ruleName) * shortfallHours / constraint.minimumDirectHours,
    )
}
