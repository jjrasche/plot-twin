package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import plottwin.worldstate.WorldLog

private const val EATON_COUNTY_PLAUSIBLE_MIN_METERS = 250.0
private const val EATON_COUNTY_PLAUSIBLE_MAX_METERS = 280.0

class RealParcelFixtureGateTest {

    @Test
    fun real_dem_elevations_sit_in_the_plausible_band_for_eaton_county() {
        val heights = rawElevationOf(RealParcelFixture.parcel()).surfaceHeights
        val lowest = heights.min().toDouble()
        val highest = heights.max().toDouble()
        assertTrue(
            lowest > EATON_COUNTY_PLAUSIBLE_MIN_METERS && highest < EATON_COUNTY_PLAUSIBLE_MAX_METERS,
            "parcel elevations [$lowest, $highest] m fall outside the NAVD88 plausibility band",
        )
    }

    @Test
    fun receipt_min_max_match_the_decoded_heights() {
        val parcel = RealParcelFixture.parcel()
        val heights = rawElevationOf(parcel).surfaceHeights
        assertEquals(heights.min().toDouble(), parcel.provenance.elevationMinMeters, 1e-4)
        assertEquals(heights.max().toDouble(), parcel.provenance.elevationMaxMeters, 1e-4)
    }

    @Test
    fun real_parcel_projects_a_grid_matching_its_declared_extent() {
        val parcel = RealParcelFixture.parcel()
        WorldLog.openInMemory().use { log ->
            appendRealParcel(log, parcel)
            val terrain = assertNotNull(log.currentState().terrain).grid
            assertEquals(90.0, terrain.columns * terrain.cellSize.value, 1e-9)
            assertEquals(90.0, terrain.rows * terrain.cellSize.value, 1e-9)
        }
    }

    @Test
    fun fixture_site_matches_the_toy_site_row_ground_truth() {
        val site = siteRowOf(RealParcelFixture.parcel())
        assertEquals(42.6006, site.latitudeDegrees, 1e-9)
        assertEquals(-84.6547, site.longitudeDegrees, 1e-9)
        assertEquals("America/Detroit", site.timeZoneId)
    }
}
