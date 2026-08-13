package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CELL_SIZE_METERS = 0.1

private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))

private fun frameAt(easting: Double, northing: Double) =
    GroundFrame("EPSG:26916", Meters(easting), Meters(northing))

private fun strip(frame: GroundFrame) = ParcelBoundaryRow(
    parcelId = "test-strip",
    ring = listOf(groundPoint(0.0, 0.0), groundPoint(2.0, 0.0), groundPoint(2.0, 5.0), groundPoint(0.0, 5.0)),
    frame = frame,
    acresStated = 10.0 / 4046.8564224,
    provenance = BoundaryProvenance(
        source = "test",
        pulledAtUtc = "2026-08-13T00:00:00Z",
        observedAt = "2026-08-13T00:00:00Z",
        sha256 = "0".repeat(64),
        contract = "test",
    ),
)

private fun terrainOver(columns: Int, rows: Int, frame: GroundFrame?) = BaseTerrainRow(
    columns = columns,
    rows = rows,
    cellSize = Meters(CELL_SIZE_METERS),
    heightsBase64 = encodeHeightsBase64(FloatArray(columns * rows) { 250.0f }),
    frame = frame,
)

class GroundFrameGateTest {

    @Test
    fun the_grid_extent_is_the_rings_bounding_box_snapped_outward_to_whole_cells() {
        val ragged = strip(frameAt(0.0, 0.0)).copy(
            ring = listOf(groundPoint(0.0, 0.0), groundPoint(1.94, 0.0), groundPoint(1.94, 4.01), groundPoint(0.0, 4.01)),
        )
        assertEquals(GridExtent(20, 41), gridExtentOf(ragged, Meters(CELL_SIZE_METERS)))
        assertEquals(GridExtent(2, 5), gridExtentOf(ragged, Meters(1.0)))
    }

    @Test
    fun a_log_whose_rows_name_two_origins_cannot_be_read() {
        WorldLog.openInMemory().use { log ->
            log.append(strip(frameAt(695000.7, 4728383.3)), WriterRole.CAPTURE)
            log.append(terrainOver(20, 50, frameAt(694976.7, 4728335.3)), WriterRole.CAPTURE)
            val disagreement = assertFailsWith<LogFrameDisagreement> { log.currentState() }
            println("[frame] ${disagreement.message}")
        }
    }

    @Test
    fun a_grid_recut_to_a_different_extent_beside_the_same_property_line_cannot_be_read() {
        WorldLog.openInMemory().use { log ->
            log.append(strip(frameAt(695000.7, 4728383.3)), WriterRole.CAPTURE)
            log.append(terrainOver(900, 900, frameAt(695000.7, 4728383.3)), WriterRole.CAPTURE)
            val disagreement = assertFailsWith<LogFrameDisagreement> { log.currentState() }
            println("[frame] ${disagreement.message}")
        }
    }

    @Test
    fun one_frame_across_the_boundary_and_the_grid_reads_cleanly() {
        WorldLog.openInMemory().use { log ->
            val frame = frameAt(695000.7, 4728383.3)
            log.append(strip(frame), WriterRole.CAPTURE)
            log.append(terrainOver(20, 50, frame), WriterRole.CAPTURE)
            val state = log.currentState()
            assertNotNull(state.parcelBoundary)
            assertEquals(GridExtent(20, 50), GridExtent(assertNotNull(state.terrain).grid.columns, state.terrain!!.grid.rows))
        }
    }

    @Test
    fun a_grid_that_names_no_frame_makes_no_claim_to_disagree_with() {
        WorldLog.openInMemory().use { log ->
            log.append(terrainOver(20, 50, null), WriterRole.CAPTURE)
            assertNotNull(log.currentState().terrain)
        }
    }

    @Test
    fun the_mask_marks_cells_outside_the_property_line_not_ours() {
        WorldLog.openInMemory().use { log ->
            val frame = frameAt(695000.7, 4728383.3)
            val leaning = strip(frame).copy(
                ring = listOf(groundPoint(0.0, 0.0), groundPoint(2.0, 0.0), groundPoint(2.0, 5.0), groundPoint(1.0, 5.0)),
            )
            log.append(leaning, WriterRole.CAPTURE)
            log.append(terrainOver(20, 50, frame), WriterRole.CAPTURE)
            val mask = assertNotNull(log.currentState().parcelMask)
            assertEquals(20 * 50, mask.columns * mask.rows)
            assertTrue(mask.isInsideBoundary(15, 5), "a cell well inside the line reads not-ours")
            assertFalse(mask.isInsideBoundary(2, 45), "a cell the leaning line excludes reads ours")
            assertEquals(7.5, mask.insideAreaSquareMeters, 0.2)
        }
    }
}
