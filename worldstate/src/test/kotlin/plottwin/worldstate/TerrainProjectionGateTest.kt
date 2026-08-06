package plottwin.worldstate

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerrainProjectionGateTest {

    @Test
    fun base_terrain_row_projects_to_the_ingested_grid() {
        WorldLog.openInMemory().use { log ->
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)

            val terrain = log.currentState().terrain
            assertTrue(terrain != null)
            assertEquals(TerrainGrid(4, 3, Meters(0.1), slopedHeights()), terrain.grid)
        }
    }

    @Test
    fun base_and_diff_replay_from_disk_yield_an_identical_grid() {
        val dbPath = createTempDirectory("terrain-gate").resolve("world.db")
        val liveProjection = WorldLog.open(dbPath).use { log ->
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(carveDiff(), WriterRole.CAPTURE)
            log.currentState()
        }

        val replayedProjection = WorldLog.open(dbPath).use { reopened ->
            projectCurrentState(reopened.readAll())
        }

        assertEquals(liveProjection, replayedProjection)
    }

    @Test
    fun terrain_diff_changes_only_its_region_cells() {
        WorldLog.openInMemory().use { log ->
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            val baseGrid = log.currentState().terrain!!.grid

            log.append(carveDiff(), WriterRole.CAPTURE)
            val patchedGrid = log.currentState().terrain!!.grid

            for (row in 0 until 3) {
                for (column in 0 until 4) {
                    val cell = patchedGrid.indexOf(column, row)
                    val isCarved = column in 1..2 && row == 1
                    val expectedHeight = if (isCarved) -9.0f else baseGrid.surfaceHeights[cell]
                    assertEquals(expectedHeight, patchedGrid.surfaceHeights[cell], "cell ($column, $row)")
                }
            }
        }
    }

    @Test
    fun terrain_version_seq_follows_the_last_terrain_row() {
        WorldLog.openInMemory().use { log ->
            val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            assertEquals(baseSeq, log.currentState().terrain!!.versionSeq)

            val diffSeq = log.append(carveDiff(), WriterRole.CAPTURE)
            assertEquals(diffSeq, log.currentState().terrain!!.versionSeq)
        }
    }

    @Test
    fun heights_roundtrip_through_the_base64_codec() {
        val heights = floatArrayOf(0.0f, -0.5f, 1.25f, 3.75f)
        assertEquals(heights.toList(), decodeHeightsBase64(encodeHeightsBase64(heights), 4).toList())
    }

    @Test
    fun base_terrain_from_llm_is_rejected() {
        WorldLog.openInMemory().use { log ->
            assertFailsWith<GeometryWriteRejected> {
                log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.LLM)
            }
        }
    }

    @Test
    fun terrain_diff_from_optimizer_is_rejected() {
        WorldLog.openInMemory().use { log ->
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)

            assertFailsWith<GeometryWriteRejected> {
                log.append(carveDiff(), WriterRole.OPTIMIZER)
            }
        }
    }

    private fun slopedHeights(): FloatArray = FloatArray(12) { cell -> (cell / 4) * 0.1f }

    private fun slopedRaw() = RawElevation(
        columns = 4,
        rows = 3,
        cellSize = Meters(0.1),
        surfaceHeights = slopedHeights(),
    )

    private fun carveDiff() = TerrainDiffRow(
        firstColumn = 1,
        firstRow = 1,
        columns = 2,
        rows = 1,
        heightsBase64 = encodeHeightsBase64(floatArrayOf(-9.0f, -9.0f)),
    )
}
