package plottwin.solvers

val registeredLeafSolvers: List<LeafSolver> = listOf(ClearanceSweep, WaterlogAccumulation)

fun runSolvers(
    world: SolverWorld,
    constraints: List<Constraint>,
    leafSolvers: List<LeafSolver> = registeredLeafSolvers,
): List<Violation> {
    val gatheredViolations = fanOutLeafSolvers(world, constraints, leafSolvers)
    return rankViolations(gatheredViolations)
}

private fun fanOutLeafSolvers(
    world: SolverWorld,
    constraints: List<Constraint>,
    leafSolvers: List<LeafSolver>,
): List<Violation> =
    leafSolvers.flatMap { solver -> constraints.flatMap { constraint -> solver.findViolations(world, constraint) } }
