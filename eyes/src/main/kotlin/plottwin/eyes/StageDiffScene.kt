package plottwin.eyes

import plottwin.oppipeline.OpPipeline
import plottwin.oppipeline.proposalSurfaceNameOf
import plottwin.solvers.ToyPlotFixture
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.LoggedRow
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.OpVerb
import plottwin.worldstate.StageRow
import plottwin.worldstate.Surface
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

data class StageDiffScene(
    val stageName: String,
    val state: CurrentState,
    val proposal: Surface.Proposed,
    val history: List<LoggedRow>,
)

// The three earthworks intents from charter 16, each staged as a named stage over its regrade op.
fun digHereScene(): StageDiffScene =
    regradeScene("intent-1 dig here") { log -> log.append(pondOp(spoilDestination = null), WriterRole.LLM) }

fun foundationPadScene(): StageDiffScene =
    regradeScene("intent-2 foundation") { log ->
        log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "greenhouse", OpSlot.GROUND_FORM to "pad")), WriterRole.LLM)
    }

fun bermSpoilScene(): StageDiffScene =
    regradeScene("intent-3 berm adjacent") { log -> log.append(pondOp(spoilDestination = "spoil berm"), WriterRole.LLM) }

fun proposalOfStage(state: CurrentState, stageName: String): Surface.Proposed {
    val stage = requireNotNull(state.stages[stageName]) { "no stage named $stageName" }
    return Surface.Proposed(proposalSurfaceNameOf(stage.memberOpSeqs.first()))
}

private fun regradeScene(stageName: String, appendRegrade: (WorldLog) -> Long): StageDiffScene =
    WorldLog.openInMemory().use { log ->
        ToyPlotFixture.appendToyPlot(log)
        log.append(EntityRow("pond region", rectangleRing(10.0, 14.0, 20.0, 24.0), Meters(0.0)), WriterRole.CAPTURE)
        log.append(EntityRow("spoil berm", rectangleRing(16.0, 20.0, 20.0, 24.0), Meters(0.0)), WriterRole.CAPTURE)
        OpPipeline(log = log, date = ToyPlotFixture.toyDate, surface = Surface.Measured, constraints = emptyList()).attach()
        val opSeq = appendRegrade(log)
        log.append(StageRow(stageName, listOf(opSeq)), WriterRole.LLM)
        val state = log.currentState()
        StageDiffScene(stageName, state, proposalOfStage(state, stageName), log.readAll())
    }

private fun pondOp(spoilDestination: String?): OpRow {
    val namedSlots = mutableMapOf(OpSlot.SUBJECT to "pond region", OpSlot.GROUND_FORM to "pond")
    if (spoilDestination != null) namedSlots[OpSlot.SPOIL_DESTINATION] = spoilDestination
    return OpRow(OpVerb.REGRADE, namedSlots)
}

private fun rectangleRing(westEast: Double, eastEast: Double, southNorth: Double, northNorth: Double): List<GroundPoint> =
    listOf(
        GroundPoint(Meters(westEast), Meters(southNorth)),
        GroundPoint(Meters(eastEast), Meters(southNorth)),
        GroundPoint(Meters(eastEast), Meters(northNorth)),
        GroundPoint(Meters(westEast), Meters(northNorth)),
    )
