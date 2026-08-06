package plottwin.solvers

import plottwin.worldstate.CurrentState
import plottwin.worldstate.Meters

sealed interface Constraint {
    val ruleName: String
}

data class ClearanceConstraint(
    override val ruleName: String,
    val pathName: String,
    val minClearance: Meters,
) : Constraint

data class WaterlogConstraint(
    override val ruleName: String,
    val entityName: String,
    val maxUpslopeSquareMeters: Double,
) : Constraint

internal fun ruleWeightOf(state: CurrentState, ruleName: String): Double =
    state.rules[ruleName]?.weight ?: 1.0
