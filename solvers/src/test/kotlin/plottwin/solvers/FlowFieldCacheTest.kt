package plottwin.solvers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowFieldCacheTest {

    @Test
    fun memoized_field_equals_the_uncached_field_on_the_toy_terrain() {
        val terrain = ToyPlotFixture.terrainWithSwale()

        val uncached = UNCACHED_UPSLOPE_FIELD.upslopeCellCountsOf(terrain)
        val memoized = FlowFieldCache().upslopeCellCountsOf(terrain)

        assertTrue(uncached.contentEquals(memoized))
    }

    @Test
    fun cache_computes_once_per_terrain_instance() {
        var computeCount = 0
        val countingSource = UpslopeFieldSource { terrain ->
            computeCount++
            UNCACHED_UPSLOPE_FIELD.upslopeCellCountsOf(terrain)
        }
        val cache = FlowFieldCache(countingSource)
        val terrain = flatTerrain(columns = 4, rows = 4)

        cache.upslopeCellCountsOf(terrain)
        cache.upslopeCellCountsOf(terrain)

        assertEquals(1, computeCount)
    }

    @Test
    fun distinct_terrain_instances_are_cached_separately() {
        val cache = FlowFieldCache()
        val flat = flatTerrain(columns = 4, rows = 4)
        val sloped = TerrainGrid(2, 2, flat.cellSize, floatArrayOf(1.0f, 0.0f, 1.0f, 0.0f))

        assertTrue(cache.upslopeCellCountsOf(flat).all { it == 0 })
        assertTrue(cache.upslopeCellCountsOf(sloped).any { it > 0 })
    }
}
