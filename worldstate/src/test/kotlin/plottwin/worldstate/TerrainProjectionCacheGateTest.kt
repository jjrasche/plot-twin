package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TerrainProjectionCacheGateTest {

    @Test
    fun cache_reflects_a_diff_that_landed_after_the_first_read() {
        WorldLog.openInMemory().use { log ->
            val cache = TerrainProjectionCache.over(log)
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            val baseGrid = cache.terrain()!!.grid

            val diffSeq = log.append(carveDiff(), WriterRole.CAPTURE)
            val patchedTerrain = cache.terrain()!!

            assertEquals(diffSeq, patchedTerrain.versionSeq)
            assertEquals(-9.0f, patchedTerrain.grid.surfaceHeights[patchedTerrain.grid.indexOf(1, 1)])
            assertEquals(baseGrid.surfaceHeights[0], patchedTerrain.grid.surfaceHeights[0])
        }
    }

    @Test
    fun cache_folds_only_the_rows_that_arrived_since_the_last_read() {
        WorldLog.openInMemory().use { log ->
            val foldedRowCounts = mutableListOf<Int>()
            val cache = TerrainProjectionCache { seq ->
                log.readRowsAfter(seq).also { arrivedRows -> foldedRowCounts.add(arrivedRows.size) }
            }

            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            cache.terrain()
            log.append(carveDiff(), WriterRole.CAPTURE)
            cache.terrain()
            cache.terrain()

            assertEquals(listOf(1, 1, 0), foldedRowCounts)
        }
    }

    @Test
    fun cache_matches_a_full_replay_after_every_append() {
        WorldLog.openInMemory().use { log ->
            val cache = TerrainProjectionCache.over(log)
            assertNull(cache.terrain())

            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            assertEquals(log.currentState().terrain, cache.terrain())

            log.append(carveDiff(), WriterRole.CAPTURE)
            assertEquals(log.currentState().terrain, cache.terrain())
        }
    }

    @Test
    fun a_second_base_terrain_row_replaces_the_cached_grid() {
        WorldLog.openInMemory().use { log ->
            val cache = TerrainProjectionCache.over(log)
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            log.append(carveDiff(), WriterRole.CAPTURE)
            cache.terrain()

            val recaptureSeq = log.append(GriddedElevationOperator.compileBaseTerrain(flatRaw()), WriterRole.CAPTURE)
            val recapturedTerrain = cache.terrain()!!

            assertEquals(recaptureSeq, recapturedTerrain.versionSeq)
            assertEquals(TerrainGrid(4, 3, Meters(0.1), flatHeights()), recapturedTerrain.grid)
        }
    }

    @Test
    fun non_terrain_rows_advance_the_watermark_without_changing_the_grid() {
        WorldLog.openInMemory().use { log ->
            val cache = TerrainProjectionCache.over(log)
            log.append(GriddedElevationOperator.compileBaseTerrain(slopedRaw()), WriterRole.CAPTURE)
            val baseGrid = cache.terrain()!!.grid

            val ruleSeq = log.append(RuleRow("drains-away-from-barn", Hardness.HARD, 1.0, "water leaves the barn pad"), WriterRole.OWNER)

            assertEquals(baseGrid, cache.terrain()!!.grid)
            assertEquals(ruleSeq, cache.foldedThroughSeq)
        }
    }

    private fun slopedHeights(): FloatArray = FloatArray(12) { cell -> (cell / 4) * 0.1f }

    private fun flatHeights(): FloatArray = FloatArray(12) { 2.5f }

    private fun slopedRaw() = RawElevation(columns = 4, rows = 3, cellSize = Meters(0.1), surfaceHeights = slopedHeights())

    private fun flatRaw() = RawElevation(columns = 4, rows = 3, cellSize = Meters(0.1), surfaceHeights = flatHeights())

    private fun carveDiff() = TerrainDiffRow(
        firstColumn = 1,
        firstRow = 1,
        columns = 2,
        rows = 1,
        heightsBase64 = encodeHeightsBase64(floatArrayOf(-9.0f, -9.0f)),
    )
}
