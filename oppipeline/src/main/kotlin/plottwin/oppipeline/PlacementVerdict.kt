package plottwin.oppipeline

import plottwin.solvers.Violation
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PositionDiffRow
import plottwin.worldstate.RejectionRow

sealed interface PlacementVerdict

data class Placement(val diff: PositionDiffRow) : PlacementVerdict

data class Rejection(val row: RejectionRow) : PlacementVerdict

data class RoomExtent(val widthEast: Meters, val depthNorth: Meters)

data class EvaluatedCandidate(
    val anchor: GroundPoint,
    val footprint: List<GroundPoint>,
    val hardViolations: List<Violation>,
    val softViolations: List<Violation>,
    val softScore: Double,
    val pathDistance: Double,
)
