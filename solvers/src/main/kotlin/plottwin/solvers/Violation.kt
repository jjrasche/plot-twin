package plottwin.solvers

import plottwin.worldstate.GroundPoint

data class Violation(
    val ruleName: String,
    val location: GroundPoint,
    val magnitude: Double,
    val severity: Double,
)

private val severityFirstRanking: Comparator<Violation> =
    compareByDescending<Violation> { it.severity }
        .thenBy { it.ruleName }
        .thenBy { it.location.east.value }
        .thenBy { it.location.north.value }
        .thenBy { it.magnitude }

fun rankViolations(violations: List<Violation>): List<Violation> =
    violations.sortedWith(severityFirstRanking)
