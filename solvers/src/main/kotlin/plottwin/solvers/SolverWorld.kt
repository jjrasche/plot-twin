package plottwin.solvers

import java.time.LocalDate
import plottwin.worldstate.CurrentState

data class SolverWorld(
    val state: CurrentState,
    val terrain: TerrainGrid,
    val date: LocalDate,
)

fun interface LeafSolver {
    fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation>
}
