package plottwin.solvers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.Meters

class D8FlowTest {

    @Test
    fun flat_terrain_accumulates_nothing() {
        val terrain = flatTerrain(columns = 20, rows = 20, height = 1.0f)

        val flowTargets = computeFlowTargets(terrain)
        val upslopeCounts = computeUpslopeCellCounts(flowTargets)

        assertTrue(flowTargets.all { it == NO_FLOW })
        assertTrue(upslopeCounts.all { it == 0 })
    }

    @Test
    fun single_basin_drains_every_cell_to_the_pit() {
        val basin = paraboloidBasin(side = 9)
        val pit = basin.indexOf(column = 4, row = 4)

        val flowTargets = computeFlowTargets(basin)
        val upslopeCounts = computeUpslopeCellCounts(flowTargets)

        val outletCells = flowTargets.indices.filter { flowTargets[it] == NO_FLOW }
        assertEquals(listOf(pit), outletCells)
        assertEquals(basin.cellCount - 1, upslopeCounts[pit])
    }

    @Test
    fun equal_steepest_drops_resolve_to_the_first_neighbor_in_fixed_order() {
        val peak = TerrainGrid(3, 3, Meters(0.1), floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f))

        val flowTargets = computeFlowTargets(peak)

        assertEquals(peak.indexOf(column = 1, row = 0), flowTargets[peak.indexOf(column = 1, row = 1)])
    }

    private fun paraboloidBasin(side: Int): TerrainGrid {
        val center = side / 2
        val heights = FloatArray(side * side)
        for (row in 0 until side) {
            for (column in 0 until side) {
                val eastOffset = column - center
                val northOffset = row - center
                heights[row * side + column] = (eastOffset * eastOffset + northOffset * northOffset).toFloat()
            }
        }
        return TerrainGrid(side, side, Meters(0.1), heights)
    }
}
