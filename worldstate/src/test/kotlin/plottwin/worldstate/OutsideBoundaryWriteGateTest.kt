package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val CELL_SIZE_METERS = 1.0
private const val GRID_CELLS_PER_SIDE = 10

private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))

// A leaning parcel: its bounding box is the whole grid, and the west sliver is the neighbour's.
private val EAST_OF_THE_LEAN_IS_OURS = ParcelBoundaryRow(
    parcelId = "test-lean",
    ring = listOf(groundPoint(0.0, 0.0), groundPoint(10.0, 0.0), groundPoint(10.0, 10.0), groundPoint(5.0, 10.0)),
    frame = GroundFrame("EPSG:26916", Meters(695000.0), Meters(4728383.0)),
    acresStated = 75.0 / 4046.8564224,
    provenance = BoundaryProvenance(
        source = "test",
        pulledAtUtc = "2026-08-13T00:00:00Z",
        observedAt = "2026-08-13T00:00:00Z",
        sha256 = "0".repeat(64),
        contract = "test",
    ),
)

private fun flatTerrain() = BaseTerrainRow(
    columns = GRID_CELLS_PER_SIDE,
    rows = GRID_CELLS_PER_SIDE,
    cellSize = Meters(CELL_SIZE_METERS),
    heightsBase64 = encodeHeightsBase64(FloatArray(GRID_CELLS_PER_SIDE * GRID_CELLS_PER_SIDE) { 250.0f }),
    frame = null,
)

private fun carve(firstColumn: Int, firstRow: Int, columns: Int, rows: Int) = TerrainDiffRow(
    firstColumn = firstColumn,
    firstRow = firstRow,
    columns = columns,
    rows = rows,
    heightsBase64 = encodeHeightsBase64(FloatArray(columns * rows) { 249.0f }),
)

class OutsideBoundaryWriteGateTest {

    @Test
    fun a_regrade_inside_the_property_line_is_accepted() {
        WorldLog.openInMemory().use { log ->
            log.append(EAST_OF_THE_LEAN_IS_OURS, WriterRole.CAPTURE)
            log.append(flatTerrain(), WriterRole.CAPTURE)
            log.append(carve(firstColumn = 6, firstRow = 2, columns = 3, rows = 2), WriterRole.CAPTURE)
            val heights = assertNotNull(log.currentState().terrain).grid.surfaceHeights
            assertEquals(249.0f, heights[2 * GRID_CELLS_PER_SIDE + 6])
        }
    }

    @Test
    fun a_regrade_reaching_across_the_property_line_is_rejected_naming_the_first_stolen_cell() {
        WorldLog.openInMemory().use { log ->
            log.append(EAST_OF_THE_LEAN_IS_OURS, WriterRole.CAPTURE)
            log.append(flatTerrain(), WriterRole.CAPTURE)
            val rejection = assertFailsWith<TerrainDiffOutsideBoundary> {
                log.append(carve(firstColumn = 0, firstRow = 6, columns = 3, rows = 2), WriterRole.CAPTURE)
            }
            println("[boundary-write] ${rejection.message}")
            val violation = rejection.violations.single()
            assertEquals(INSIDE_THE_PROPERTY_LINE_RULE, violation.ruleName)
            assertEquals(groundPoint(0.5, 6.5), violation.location)
            assertEquals(6.0, violation.magnitude)
        }
    }

    @Test
    fun the_rejected_regrade_never_reaches_the_log() {
        WorldLog.openInMemory().use { log ->
            log.append(EAST_OF_THE_LEAN_IS_OURS, WriterRole.CAPTURE)
            log.append(flatTerrain(), WriterRole.CAPTURE)
            runCatching { log.append(carve(firstColumn = 0, firstRow = 6, columns = 3, rows = 2), WriterRole.CAPTURE) }
            assertTrue(log.readAll().none { it.row is TerrainDiffRow }, "a rejected regrade landed anyway")
        }
    }

    @Test
    fun a_proposal_on_the_neighbours_land_is_rejected_for_the_same_reason() {
        WorldLog.openInMemory().use { log ->
            log.append(EAST_OF_THE_LEAN_IS_OURS, WriterRole.CAPTURE)
            val baseSeq = log.append(flatTerrain(), WriterRole.CAPTURE)
            val proposal = carve(firstColumn = 0, firstRow = 8, columns = 2, rows = 2)
                .copy(surface = Surface.Proposed("pond plan"), branchedFromSeq = baseSeq)
            assertFailsWith<TerrainDiffOutsideBoundary> { log.append(proposal, WriterRole.OPTIMIZER) }
        }
    }

    @Test
    fun a_log_with_no_property_line_regrades_freely() {
        WorldLog.openInMemory().use { log ->
            log.append(flatTerrain(), WriterRole.CAPTURE)
            log.append(carve(firstColumn = 0, firstRow = 6, columns = 3, rows = 2), WriterRole.CAPTURE)
            assertNotNull(log.currentState().terrain)
        }
    }
}
