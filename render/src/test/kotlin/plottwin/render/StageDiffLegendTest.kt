package plottwin.render

import kotlin.test.Test
import kotlin.test.assertEquals
import plottwin.worldstate.EarthworkTotals

class StageDiffLegendTest {

    @Test
    fun legend_speaks_owner_units_from_the_ledger_alone() {
        val legend = stageDiffLegendOf(
            EarthworkTotals(
                bankCutCubicMeters = 10.0,
                compactedFillCubicMeters = 0.0,
                looseSpoilPlacedCubicMeters = 13.0,
                haulOffCubicMeters = 0.0,
                topsoilStrippedCubicMeters = 0.0,
                topsoilRespreadCubicMeters = 0.0,
            ),
        )
        assertEquals("Dig out: 13.1 cubic yards of soil", legend.dugLine)
        assertEquals("Place on site: 17.0 cubic yards (dug soil fluffs up)", legend.placedLine)
        assertEquals("Haul away: 0.0 cubic yards", legend.hauledLine)
        assertEquals("That digging is about 2 dump-truck loads (a truck carries ~10 cubic yards)", legend.anchorLine)
    }

    @Test
    fun one_truck_load_reads_singular() {
        val legend = stageDiffLegendOf(EarthworkTotals(1.0, 0.0, 0.0, 1.3, 0.0, 0.0))
        assertEquals("That digging is about 1 dump-truck load (a truck carries ~10 cubic yards)", legend.anchorLine)
    }
}
