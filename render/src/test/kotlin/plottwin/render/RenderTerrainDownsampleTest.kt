package plottwin.render

import kotlin.test.Test
import kotlin.test.assertEquals
import plottwin.solvers.TerrainGrid
import plottwin.solvers.ToyPlotFixture
import plottwin.worldstate.Meters

class RenderTerrainDownsampleTest {

    @Test
    fun toy_terrain_downsamples_to_the_batched_painter_band() {
        val renderTerrain = downsampleForRender(ToyPlotFixture.terrainWithSwale(), RENDER_DOWNSAMPLE_FACTOR)

        assertEquals(225, renderTerrain.columns)
        assertEquals(225, renderTerrain.rows)
        assertEquals(0.4, renderTerrain.cellSize.value, 1e-9)
    }

    @Test
    fun downsample_samples_every_factor_th_source_cell() {
        val source = ToyPlotFixture.terrainWithSwale()
        val renderTerrain = downsampleForRender(source, 4)

        assertEquals(
            source.surfaceHeights[source.indexOf(40, 8)],
            renderTerrain.surfaceHeights[renderTerrain.indexOf(10, 2)],
        )
    }

    @Test
    fun downsample_of_non_divisible_grid_clamps_the_last_sample() {
        val source = TerrainGrid(10, 10, Meters(0.1), FloatArray(100) { it.toFloat() })
        val renderTerrain = downsampleForRender(source, 4)

        assertEquals(3, renderTerrain.columns)
        assertEquals(3, renderTerrain.rows)
        assertEquals(source.surfaceHeights[source.indexOf(8, 8)], renderTerrain.surfaceHeights[renderTerrain.indexOf(2, 2)])
    }

    @Test
    fun factor_one_is_identity() {
        val source = ToyPlotFixture.terrainWithSwale()

        assertEquals(source, downsampleForRender(source, 1))
    }
}
