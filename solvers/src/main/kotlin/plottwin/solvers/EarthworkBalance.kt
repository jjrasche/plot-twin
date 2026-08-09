package plottwin.solvers

import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.LoggedEarthwork
import plottwin.worldstate.Meters
import plottwin.worldstate.Surface

object EarthworkBalance : LeafSolver {
    override fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation> {
        if (constraint !is EarthworkBalanceConstraint) return emptyList()
        return world.state.earthworks.mapNotNull { entry -> haulExcessViolation(world, constraint, entry) }
    }
}

private fun haulExcessViolation(
    world: SolverWorld,
    constraint: EarthworkBalanceConstraint,
    entry: LoggedEarthwork,
): Violation? {
    val excessCubicMeters = entry.row.haulOffCubicMeters - constraint.maxHaulOffCubicMeters
    if (excessCubicMeters <= 0.0) return null
    val servedRegionCenter = changedCellsCentroidOf(world.state, entry.row.surfaceName) ?: return null
    return Violation(
        ruleName = constraint.ruleName,
        location = servedRegionCenter,
        magnitude = excessCubicMeters,
        severity = ruleWeightOf(world.state, constraint.ruleName) *
            excessCubicMeters / constraint.maxHaulOffCubicMeters.coerceAtLeast(1.0),
    )
}

private fun changedCellsCentroidOf(state: CurrentState, surfaceName: String): GroundPoint? {
    val measuredGrid = state.terrain?.grid ?: return null
    val proposedGrid = state.terrainOn(Surface.Proposed(surfaceName))?.grid ?: return null
    var eastSum = 0.0
    var northSum = 0.0
    var changedCells = 0
    for (cell in 0 until measuredGrid.cellCount) {
        if (measuredGrid.surfaceHeights[cell] == proposedGrid.surfaceHeights[cell]) continue
        val center = measuredGrid.centerOf(cell)
        eastSum += center.east.value
        northSum += center.north.value
        changedCells++
    }
    if (changedCells == 0) return null
    return GroundPoint(Meters(eastSum / changedCells), Meters(northSum / changedCells))
}
