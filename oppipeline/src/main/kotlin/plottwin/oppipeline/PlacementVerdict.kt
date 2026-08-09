package plottwin.oppipeline

import plottwin.worldstate.EarthworkRow
import plottwin.worldstate.PositionDiffRow
import plottwin.worldstate.RejectionRow
import plottwin.worldstate.TerrainDiffRow

sealed interface PlacementVerdict

data class Placement(val diff: PositionDiffRow) : PlacementVerdict

data class Regrade(val diffs: List<TerrainDiffRow>, val earthwork: EarthworkRow) : PlacementVerdict

data class Rejection(val row: RejectionRow) : PlacementVerdict
