package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import plottwin.worldstate.GridExtent
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.WorldLog
import plottwin.worldstate.gridExtentOf
import plottwin.worldstate.isInsidePolygon

private const val SQUARE_METERS_PER_ACRE = 4046.8564224
private const val MASK_AREA_AGREEMENT_SHARE = 0.01
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
    fun the_grid_extent_is_the_property_lines_own_bounding_box() {
        val parcel = RealParcelFixture.parcel()
        WorldLog.openInMemory().use { log ->
            appendParcelBoundary(log, RealParcelFixture.boundary())
            appendRealParcel(log, parcel)
            val state = log.currentState()
            val terrain = assertNotNull(state.terrain).grid
            val boundary = assertNotNull(state.parcelBoundary)
            println(
                "[extent] %d x %d cells at %.2f m covers the %.2f x %.2f m property line".format(
                    terrain.columns,
                    terrain.rows,
                    terrain.cellSize.value,
                    boundary.ring.maxOf { it.east.value },
                    boundary.ring.maxOf { it.north.value },
                )
            )
            assertEquals(gridExtentOf(boundary, terrain.cellSize), GridExtent(terrain.columns, terrain.rows))
        }
    }

    @Test
    fun the_mask_measures_the_acreage_the_county_states() {
        WorldLog.openInMemory().use { log ->
            appendParcelBoundary(log, RealParcelFixture.boundary())
            appendRealParcel(log, RealParcelFixture.parcel())
            val state = log.currentState()
            val mask = assertNotNull(state.parcelMask)
            val statedSquareMeters = assertNotNull(state.parcelBoundary).acresStated * SQUARE_METERS_PER_ACRE
            val disagreement = kotlin.math.abs(mask.insideAreaSquareMeters / statedSquareMeters - 1.0)
            println(
                "[mask] %d of %d cells inside the line = %.1f m2 vs %.1f m2 county-stated, %+.3f%%".format(
                    mask.insideCellCount,
                    mask.columns * mask.rows,
                    mask.insideAreaSquareMeters,
                    statedSquareMeters,
                    100.0 * (mask.insideAreaSquareMeters / statedSquareMeters - 1.0),
                )
            )
            assertTrue(disagreement < MASK_AREA_AGREEMENT_SHARE, "the mask measures ${100.0 * disagreement}% off the county's acreage")
        }
    }

    @Test
    fun every_retained_tree_stands_inside_the_property_line() {
        val boundary = RealParcelFixture.boundary()
        val ring = openRingOf(boundary.ringLocalClosed)
        val trees = RealParcelFixture.features().trees
        val outside = trees.filterNot { tree ->
            isInsidePolygon(GroundPoint(Meters(tree.eastMeters), Meters(tree.northMeters)), ring)
        }
        println("[features] ${trees.size} trees retained, ${outside.size} outside the property line")
        assertTrue(trees.isNotEmpty(), "no trees survived the clip")
        assertTrue(outside.isEmpty(), "trees stand on the neighbour's land: $outside")
    }

    @Test
    fun the_grid_the_naip_clip_was_cut_from_contains_the_whole_property_line() {
        val parcel = RealParcelFixture.parcel()
        val boundary = RealParcelFixture.boundary()
        assertEquals(boundary.plotLocalOrigin.crs, parcel.frame.crs)
        assertEquals(boundary.plotLocalOrigin.eastingMeters, parcel.frame.originEastingMeters, 1e-9)
        assertEquals(boundary.plotLocalOrigin.northingMeters, parcel.frame.originNorthingMeters, 1e-9)
        val ring = openRingOf(boundary.ringLocalClosed)
        assertTrue(ring.all { it.east.value in 0.0..(parcel.columns * parcel.cellSizeMeters) }, "the ring runs past the grid east-west")
        assertTrue(ring.all { it.north.value in 0.0..(parcel.rows * parcel.cellSizeMeters) }, "the ring runs past the grid north-south")
    }

    @Test
    fun fixture_site_matches_the_ratified_parcel_point() {
        val site = siteRowOf(RealParcelFixture.parcel())
        assertEquals(42.68317626142, site.latitudeDegrees, 1e-9)
        assertEquals(-84.619591093007, site.longitudeDegrees, 1e-9)
        assertEquals("America/Detroit", site.timeZoneId)
    }
}
