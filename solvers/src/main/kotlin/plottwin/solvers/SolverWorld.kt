package plottwin.solvers

import java.time.LocalDate
import plottwin.worldstate.CurrentState
import plottwin.worldstate.Surface

// Date and surface are the two projection parameters; neither has a default (D-005 as amended)
data class SolverWorld(
    val state: CurrentState,
    val date: LocalDate,
    val surface: Surface,
    val upslopeFields: UpslopeFieldSource = UNCACHED_UPSLOPE_FIELD,
    val sunshedFields: SunshedFieldSource = UNCACHED_SUNSHED_FIELD,
)

fun interface LeafSolver {
    fun findViolations(world: SolverWorld, constraint: Constraint): List<Violation>
}
