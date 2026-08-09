package plottwin.solvers

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.Surface

class SolverRunnerTest {

    private val emptyWorld = SolverWorld(CurrentState.EMPTY, LocalDate.of(2026, 8, 5), Surface.Measured)

    @Test
    fun runner_fans_every_constraint_over_every_leaf_and_ranks_the_union() {
        val mildPerConstraint = LeafSolver { _, constraint -> listOf(violationAt(constraint.ruleName, east = 1.0, severity = 0.2)) }
        val severePerConstraint = LeafSolver { _, constraint -> listOf(violationAt(constraint.ruleName, east = 2.0, severity = 0.9)) }
        val twoConstraints = listOf(
            ClearanceConstraint("rule-a", "path", Meters(1.0)),
            ClearanceConstraint("rule-b", "path", Meters(1.0)),
        )

        val ranked = runSolvers(emptyWorld, twoConstraints, listOf(mildPerConstraint, severePerConstraint))

        assertEquals(4, ranked.size)
        assertEquals(listOf(0.9, 0.9, 0.2, 0.2), ranked.map { it.severity })
        assertEquals(listOf("rule-a", "rule-b", "rule-a", "rule-b"), ranked.map { it.ruleName })
    }

    @Test
    fun ranking_is_stable_and_deterministic_across_input_orders() {
        val tiedAndDistinct = listOf(
            violationAt("rule-b", east = 3.0, severity = 0.5),
            violationAt("rule-a", east = 7.0, severity = 0.5),
            violationAt("rule-a", east = 2.0, severity = 0.5),
            violationAt("rule-c", east = 1.0, severity = 0.8),
        )

        val rankedForward = rankViolations(tiedAndDistinct)
        val rankedReversed = rankViolations(tiedAndDistinct.reversed())

        assertEquals(rankedForward, rankedReversed)
        assertEquals(listOf("rule-c", "rule-a", "rule-a", "rule-b"), rankedForward.map { it.ruleName })
        assertEquals(listOf(1.0, 2.0, 7.0, 3.0), rankedForward.map { it.location.east.value })
    }

    private fun violationAt(ruleName: String, east: Double, severity: Double): Violation =
        Violation(ruleName, GroundPoint(Meters(east), Meters(0.0)), magnitude = severity, severity = severity)
}
