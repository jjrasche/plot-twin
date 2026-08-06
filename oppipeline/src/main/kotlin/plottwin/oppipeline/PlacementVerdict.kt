package plottwin.oppipeline

import plottwin.worldstate.PositionDiffRow
import plottwin.worldstate.RejectionRow

sealed interface PlacementVerdict

data class Placement(val diff: PositionDiffRow) : PlacementVerdict

data class Rejection(val row: RejectionRow) : PlacementVerdict
