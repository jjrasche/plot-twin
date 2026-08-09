package plottwin.worldstate

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SurfacedTerrainGateTest {

    @Test
    fun a_proposed_diff_branches_off_measured_and_leaves_measured_untouched() {
        WorldLog.openInMemory().use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(pondProposalDiff(baseSeq), WriterRole.OPTIMIZER)

            val state = log.currentState()
            val measuredGrid = state.terrainOn(Surface.Measured)!!.grid
            val proposedGrid = state.terrainOn(Surface.Proposed("pond plan"))!!.grid
            assertEquals(TerrainGrid(4, 3, Meters(0.1), slopedHeights()), measuredGrid)
            for (row in 0 until 3) {
                for (column in 0 until 4) {
                    val cell = measuredGrid.indexOf(column, row)
                    val isDug = column in 1..2 && row == 1
                    val expectedHeight = if (isDug) -1.0f else measuredGrid.surfaceHeights[cell]
                    assertEquals(expectedHeight, proposedGrid.surfaceHeights[cell], "cell ($column, $row)")
                }
            }
        }
    }

    @Test
    fun new_capture_after_a_proposal_moves_measured_but_never_rewrites_the_proposal() {
        WorldLog.openInMemory().use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(pondProposalDiff(baseSeq), WriterRole.OPTIMIZER)
            val proposalBefore = log.currentState().terrainOn(Surface.Proposed("pond plan"))!!

            log.append(measuredCarveDiff(), WriterRole.CAPTURE)

            val state = log.currentState()
            assertEquals(-9.0f, state.terrainOn(Surface.Measured)!!.grid.surfaceHeights[5])
            assertEquals(proposalBefore, state.terrainOn(Surface.Proposed("pond plan")))
        }
    }

    @Test
    fun writer_role_derives_from_surface_not_from_the_caller() {
        WorldLog.openInMemory().use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            assertFailsWith<GeometryWriteRejected> { log.append(pondProposalDiff(baseSeq), WriterRole.CAPTURE) }
            assertFailsWith<GeometryWriteRejected> { log.append(measuredCarveDiff(), WriterRole.OPTIMIZER) }
        }
    }

    @Test
    fun a_proposed_diff_names_the_measured_baseline_it_branched_from_or_does_not_construct() {
        assertFailsWith<IllegalArgumentException> {
            TerrainDiffRow(1, 1, 2, 1, encodeHeightsBase64(floatArrayOf(-1.0f, -1.0f)), Surface.Proposed("pond plan"), null)
        }
        assertFailsWith<IllegalArgumentException> {
            TerrainDiffRow(1, 1, 2, 1, encodeHeightsBase64(floatArrayOf(-1.0f, -1.0f)), Surface.Measured, 7L)
        }
    }

    @Test
    fun surface_realized_is_captures_word_and_retires_the_proposal_into_the_projection() {
        WorldLog.openInMemory().use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(pondProposalDiff(baseSeq), WriterRole.OPTIMIZER)
            val confirmingSeq = log.append(measuredCarveDiff(), WriterRole.CAPTURE)

            assertFailsWith<GeometryWriteRejected> {
                log.append(SurfaceRealizedRow("pond plan", confirmingSeq), WriterRole.OPTIMIZER)
            }
            log.append(SurfaceRealizedRow("pond plan", confirmingSeq), WriterRole.CAPTURE)
            assertEquals(mapOf("pond plan" to confirmingSeq), log.currentState().realizedSurfaces)
        }
    }

    @Test
    fun surfaced_log_replays_from_disk_to_an_identical_projection() {
        val dbPath = createTempDirectory("surface-gate").resolve("world.db")
        val liveProjection = WorldLog.open(dbPath).use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(pondProposalDiff(baseSeq), WriterRole.OPTIMIZER)
            log.append(measuredCarveDiff(), WriterRole.CAPTURE)
            log.append(SurfaceRealizedRow("pond plan", 3L), WriterRole.CAPTURE)
            log.currentState()
        }

        val replayedProjection = WorldLog.open(dbPath).use { reopened -> projectCurrentState(reopened.readAll()) }

        assertEquals(liveProjection, replayedProjection)
    }

    @Test
    fun an_unknown_surface_projects_to_no_terrain() {
        WorldLog.openInMemory().use { log ->
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            assertNull(log.currentState().terrainOn(Surface.Proposed("never proposed")))
        }
    }

    @Test
    fun an_op_slot_carrying_a_coordinate_pair_is_rejected_at_the_waist() {
        WorldLog.openInMemory().use { log ->
            val coordinateOp = OpRow(
                verb = OpVerb.REGRADE,
                slots = mapOf(OpSlot.SUBJECT to "pond region", OpSlot.EXTENT_TEXT to "dig at 12.5, 30.2"),
            )
            assertFailsWith<CoordinateInOpRejected> { log.append(coordinateOp, WriterRole.LLM) }
            assertTrue(log.readAll().isEmpty())
        }
    }

    @Test
    fun prose_extents_and_names_pass_the_waist() {
        WorldLog.openInMemory().use { log ->
            val proseOp = OpRow(
                verb = OpVerb.REGRADE,
                slots = mapOf(
                    OpSlot.SUBJECT to "pond region",
                    OpSlot.GROUND_FORM to "pond",
                    OpSlot.EXTENT_TEXT to "4x4, roughly knee deep",
                ),
            )
            log.append(proseOp, WriterRole.LLM)
            assertEquals(listOf(proseOp), log.currentState().pendingOps)
        }
    }

    private fun slopedHeights(): FloatArray = FloatArray(12) { cell -> (cell / 4) * 0.1f }

    private fun slopedRaw() = RawElevation(4, 3, Meters(0.1), slopedHeights())

    private fun pondProposalDiff(baseSeq: Long) = TerrainDiffRow(
        firstColumn = 1,
        firstRow = 1,
        columns = 2,
        rows = 1,
        heightsBase64 = encodeHeightsBase64(floatArrayOf(-1.0f, -1.0f)),
        surface = Surface.Proposed("pond plan"),
        branchedFromSeq = baseSeq,
    )

    private fun measuredCarveDiff() = TerrainDiffRow(
        firstColumn = 1,
        firstRow = 1,
        columns = 2,
        rows = 1,
        heightsBase64 = encodeHeightsBase64(floatArrayOf(-9.0f, -9.0f)),
    )
}
