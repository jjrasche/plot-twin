package plottwin.solvers

import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint

object ClearanceSweep : LeafSolver {
    override fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation> {
        if (constraint !is ClearanceConstraint) return emptyList()
        val pathPolyline = world.state.entities[constraint.pathName]?.footprint ?: return emptyList()
        if (pathPolyline.size < 2) return emptyList()
        val ruleWeight = ruleWeightOf(world.state, constraint.ruleName)
        return obstacleRingsAround(world.state, constraint.pathName)
            .mapNotNull { obstacleRing -> pinchViolation(pathPolyline, obstacleRing, constraint, ruleWeight) }
    }
}

private fun obstacleRingsAround(state: CurrentState, pathName: String): List<List<GroundPoint>> =
    state.entities
        .filterKeys { entityName -> entityName != pathName }
        .values
        .map { placed -> placed.footprint }
        .filter { footprint -> footprint.size >= 3 }

private fun pinchViolation(
    pathPolyline: List<GroundPoint>,
    obstacleRing: List<GroundPoint>,
    constraint: ClearanceConstraint,
    ruleWeight: Double,
): Violation? {
    val pinch = tightestPinch(pathPolyline, obstacleRing)
    val shortfall = constraint.minClearance.value - pinch.distance
    if (shortfall <= 0.0) return null
    return Violation(
        ruleName = constraint.ruleName,
        location = pinch.onPath,
        magnitude = shortfall,
        severity = ruleWeight * shortfall / constraint.minClearance.value,
    )
}

private fun tightestPinch(pathPolyline: List<GroundPoint>, obstacleRing: List<GroundPoint>): Pinch {
    var tightest: Pinch? = null
    for (pathIndex in 0 until pathPolyline.size - 1) {
        for (edgeIndex in obstacleRing.indices) {
            val candidate = pinchBetweenSegments(
                pathPolyline[pathIndex],
                pathPolyline[pathIndex + 1],
                obstacleRing[edgeIndex],
                obstacleRing[(edgeIndex + 1) % obstacleRing.size],
            )
            if (tightest == null || candidate.distance < tightest.distance) tightest = candidate
        }
    }
    return checkNotNull(tightest) { "polyline >=2 points and ring >=3 points guarantee a candidate" }
}
