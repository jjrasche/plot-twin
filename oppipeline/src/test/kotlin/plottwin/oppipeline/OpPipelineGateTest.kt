package plottwin.oppipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.solvers.ClearanceConstraint
import plottwin.solvers.Constraint
import plottwin.solvers.SolverWorld
import plottwin.solvers.ToyPlotFixture
import plottwin.solvers.WaterlogConstraint
import plottwin.solvers.runSolvers
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.LockKind
import plottwin.worldstate.LockRow
import plottwin.worldstate.LoggedRow
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.OpVerb
import plottwin.worldstate.PositionDiffRow
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole
import plottwin.worldstate.metersOf

class OpPipelineGateTest {

    @Test
    fun add_room_places_the_seedling_greenhouse_clear_of_path_and_swale() {
        WorldLog.openInMemory().use { log ->
            stageToyPlotWithRegions(log)
            val preOpViolations = runToyConstraintSolvers(log)
            attachPipeline(log)

            log.append(addSeedlingGreenhouseOp(), WriterRole.LLM)

            val placed = log.currentState().entities.getValue("seedling greenhouse")
            assertEquals(rectangleRing(24.0, 28.0, 69.0, 73.0), placed.footprint)
            assertEquals(metersOf(feet = 9), placed.height)
            assertEquals(preOpViolations, runToyConstraintSolvers(log))

            val placementRow = log.readAll().last()
            assertEquals(WriterRole.OPTIMIZER, placementRow.writer)
            assertTrue(placementRow.row is PositionDiffRow)
        }
    }

    @Test
    fun locked_greenhouse_never_moves() {
        WorldLog.openInMemory().use { log ->
            stageToyPlotWithRegions(log)
            log.append(LockRow("greenhouse", LockKind.LOCK), WriterRole.OWNER)
            val footprintBeforeOp = log.currentState().entities.getValue("greenhouse").footprint
            attachPipeline(log)

            val moveOp = OpRow(
                verb = OpVerb.MOVE,
                slots = mapOf(OpSlot.SUBJECT to "greenhouse", OpSlot.DESTINATION to "north terrace"),
            )
            log.append(moveOp, WriterRole.LLM)

            val projection = log.currentState()
            assertEquals(footprintBeforeOp, projection.entities.getValue("greenhouse").footprint)
            val rejection = projection.rejections.single()
            assertEquals(moveOp, rejection.op)
            assertEquals(listOf("entity-locked"), rejection.violations.map { it.ruleName })
        }
    }

    @Test
    fun impossible_op_appends_a_rejection_row_with_the_violations() {
        WorldLog.openInMemory().use { log ->
            stageToyPlotWithRegions(log)
            attachPipeline(log)

            val addIntoSwale = OpRow(
                verb = OpVerb.ADD_ROOM,
                slots = mapOf(
                    OpSlot.SUBJECT to "seedling greenhouse",
                    OpSlot.ROOM_KIND to "greenhouse",
                    OpSlot.DESTINATION to "swale terrace",
                    OpSlot.EXTENT_TEXT to "4x4",
                ),
            )
            log.append(addIntoSwale, WriterRole.LLM)

            val projection = log.currentState()
            assertTrue("seedling greenhouse" !in projection.entities)
            val rejection = projection.rejections.single()
            assertEquals(addIntoSwale, rejection.op)
            assertTrue(rejection.violations.isNotEmpty())
            assertTrue(rejection.violations.all { it.ruleName == "greenhouse-drainage" })
            assertTrue(rejection.violations.all { it.magnitude > 0.0 })
        }
    }

    @Test
    fun identical_log_replay_yields_identical_placement() {
        assertEquals(resolveSeedlingGreenhouseLog(), resolveSeedlingGreenhouseLog())
    }

    @Test
    fun move_op_relocates_the_unlocked_pergola_out_of_the_swale() {
        WorldLog.openInMemory().use { log ->
            stageToyPlotWithRegions(log)
            attachPipeline(log, candidateSpacing = Meters(2.0))

            val movePergola = OpRow(
                verb = OpVerb.MOVE,
                slots = mapOf(OpSlot.SUBJECT to "pergola", OpSlot.DESTINATION to "north terrace"),
            )
            log.append(movePergola, WriterRole.LLM)

            val moved = log.currentState().entities.getValue("pergola")
            assertEquals(rectangleRing(24.0, 26.0, 70.0, 73.0), moved.footprint)
            assertEquals(metersOf(feet = 8), moved.height)
            assertTrue(runToyConstraintSolvers(log).none { it.ruleName == "pergola-drainage" })
        }
    }

    private fun resolveSeedlingGreenhouseLog(): List<LoggedRow> = WorldLog.openInMemory().use { log ->
        stageToyPlotWithRegions(log)
        attachPipeline(log)
        log.append(addSeedlingGreenhouseOp(), WriterRole.LLM)
        log.readAll()
    }

    private fun stageToyPlotWithRegions(log: WorldLog) {
        ToyPlotFixture.appendToyPlot(log)
        log.append(EntityRow("north terrace", rectangleRing(24.0, 40.0, 66.0, 80.0), Meters(0.0)), WriterRole.CAPTURE)
        log.append(EntityRow("swale terrace", rectangleRing(26.0, 34.0, 56.0, 64.0), Meters(0.0)), WriterRole.CAPTURE)
    }

    private fun attachPipeline(log: WorldLog, candidateSpacing: Meters = Meters(1.0)) {
        OpPipeline(
            log = log,
            terrain = ToyPlotFixture.terrainWithSwale(),
            date = ToyPlotFixture.toyDate,
            constraints = pipelineConstraints(),
            candidateSpacing = candidateSpacing,
        ).attach()
    }

    private fun pipelineConstraints(): List<Constraint> = listOf(
        ClearanceConstraint("path-clearance", "garden path", ToyPlotFixture.pathClearanceBound),
        WaterlogConstraint("pergola-drainage", "pergola", ToyPlotFixture.WATERLOG_THRESHOLD_SQUARE_METERS),
        WaterlogConstraint("greenhouse-drainage", "seedling greenhouse", ToyPlotFixture.WATERLOG_THRESHOLD_SQUARE_METERS),
    )

    private fun runToyConstraintSolvers(log: WorldLog) = runSolvers(
        SolverWorld(log.currentState(), ToyPlotFixture.terrainWithSwale(), ToyPlotFixture.toyDate),
        pipelineConstraints(),
    )

    private fun addSeedlingGreenhouseOp() = OpRow(
        verb = OpVerb.ADD_ROOM,
        slots = mapOf(
            OpSlot.SUBJECT to "seedling greenhouse",
            OpSlot.ROOM_KIND to "greenhouse",
            OpSlot.DESTINATION to "north terrace",
            OpSlot.EXTENT_TEXT to "4x4",
        ),
    )

    private fun rectangleRing(westEast: Double, eastEast: Double, southNorth: Double, northNorth: Double): List<GroundPoint> =
        listOf(
            GroundPoint(Meters(westEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(northNorth)),
            GroundPoint(Meters(westEast), Meters(northNorth)),
        )
}
