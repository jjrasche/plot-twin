package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import plottwin.geometry.clipRingToBox
import plottwin.geometry.distanceToSegment
import plottwin.geometry.isInsidePolygon
import plottwin.geometry.isSimpleRing
import plottwin.geometry.ringAreaSquareMeters
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.ParcelBoundaryRow
import plottwin.worldstate.WorldLog

private const val SQUARE_METERS_PER_ACRE = 4046.8564224
private const val AREA_AGREEMENT_SHARE = 0.01
private const val ADDRESS_POINT_BAND_METERS = 10.0

private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))

private fun distanceToRing(point: GroundPoint, ring: List<GroundPoint>): Double =
    ring.indices.minOf { vertex -> distanceToSegment(point, ring[vertex], ring[(vertex + 1) % ring.size]) }

private fun loggedBoundary(): ParcelBoundaryRow =
    WorldLog.openInMemory().use { log ->
        appendParcelBoundary(log, RealParcelFixture.boundary())
        assertNotNull(log.currentState().parcelBoundary)
    }

class ParcelBoundaryGateTest {

    @Test
    fun the_county_ring_arrives_closed_and_lands_in_the_log_open() {
        val boundary = RealParcelFixture.boundary()
        assertEquals(boundary.ringWgs84Closed.first(), boundary.ringWgs84Closed.last())
        assertEquals(boundary.ringUtmClosed.first(), boundary.ringUtmClosed.last())
        assertEquals(boundary.ringWgs84Closed.size - 1, loggedBoundary().ring.size)
    }

    @Test
    fun the_property_line_is_a_simple_ring_with_no_self_intersection() {
        assertTrue(isSimpleRing(loggedBoundary().ring))
    }

    @Test
    fun the_derived_area_agrees_with_the_acreage_the_county_states() {
        val boundary = RealParcelFixture.boundary()
        val statedSquareMeters = boundary.acresCountyStated * SQUARE_METERS_PER_ACRE
        val derivedSquareMeters = ringAreaSquareMeters(loggedBoundary().ring)
        val disagreement = kotlin.math.abs(derivedSquareMeters / statedSquareMeters - 1.0)
        println(
            "[boundary] %.1f m2 derived vs %.1f m2 stated (%.8f ac), %+.3f%%".format(
                derivedSquareMeters, statedSquareMeters, boundary.acresCountyStated, 100.0 * (derivedSquareMeters / statedSquareMeters - 1.0),
            )
        )
        assertEquals(boundary.areaSquareMetersDerived, derivedSquareMeters, 1e-6)
        assertTrue(disagreement < AREA_AGREEMENT_SHARE, "derived area disagrees with the county by ${100.0 * disagreement}%")
    }

    @Test
    fun the_logged_ring_projects_back_onto_the_source_utm_vertices() {
        val boundary = RealParcelFixture.boundary()
        val projected = projectedRingOf(loggedBoundary())
        val sourceVertices = boundary.ringUtmClosed.dropLast(1)
        assertEquals(sourceVertices.size, projected.size)
        sourceVertices.forEachIndexed { vertex, source ->
            assertEquals(source[0], projected[vertex][0], 1e-6)
            assertEquals(source[1], projected[vertex][1], 1e-6)
        }
    }

    @Test
    fun the_boundary_carries_the_receipt_that_names_it_an_interim_county_pull() {
        val provenance = loggedBoundary().provenance
        assertTrue(provenance.source.contains("PARCELID=04003630009000"), "source does not name the parcel: ${provenance.source}")
        assertEquals(64, provenance.sha256.length)
        assertEquals("interim-county-service", provenance.contract)
        assertTrue(provenance.pulledAtUtc.endsWith("Z"), "pulled_at is not a UTC instant: ${provenance.pulledAtUtc}")
        assertNotNull(provenance.observedAt, "the layer publishes a data-currency date; the row must carry it")
    }

    @Test
    fun the_geocoded_address_point_sits_just_outside_the_south_line_not_inside_the_parcel() {
        val parcel = RealParcelFixture.parcel()
        val squareSideMeters = parcel.columns * parcel.cellSizeMeters
        val addressPoint = groundPoint(squareSideMeters / 2.0, squareSideMeters / 2.0)
        val ring = loggedBoundary().ring
        val offset = distanceToRing(addressPoint, ring)
        println("[boundary] the geocoded address point sits %.3f m outside the property line".format(offset))
        assertFalse(isInsidePolygon(addressPoint, ring), "the address point is inside the parcel after all")
        assertTrue(offset < ADDRESS_POINT_BAND_METERS, "the address point is $offset m from the boundary")
    }

    @Test
    fun the_ninety_metre_square_covered_only_a_sliver_of_the_real_parcel() {
        val parcel = RealParcelFixture.parcel()
        val squareSideMeters = parcel.columns * parcel.cellSizeMeters
        val ring = loggedBoundary().ring
        val parcelArea = ringAreaSquareMeters(ring)
        val overlapArea = ringAreaSquareMeters(
            clipRingToBox(ring, groundPoint(0.0, 0.0), groundPoint(squareSideMeters, squareSideMeters))
        )
        val squareArea = squareSideMeters * squareSideMeters
        println(
            "[boundary] the %.0fm square held %.1f m2 of the %.1f m2 parcel: %.4f of the parcel, %.4f of the square".format(
                squareSideMeters, overlapArea, parcelArea, overlapArea / parcelArea, overlapArea / squareArea,
            )
        )
        assertTrue(overlapArea / parcelArea < 0.25, "the square covered ${overlapArea / parcelArea} of the parcel")
        assertTrue(overlapArea / squareArea < 0.25, "the parcel filled ${overlapArea / squareArea} of the square")
    }

    @Test
    fun the_parcel_is_a_deep_narrow_strip_not_a_square() {
        val ring = loggedBoundary().ring
        val eastSpan = ring.maxOf { it.east.value } - ring.minOf { it.east.value }
        val northSpan = ring.maxOf { it.north.value } - ring.minOf { it.north.value }
        println("[boundary] parcel extent %.2f m east-west by %.2f m north-south".format(eastSpan, northSpan))
        assertTrue(northSpan > 200.0, "north-south extent is only $northSpan m")
        assertTrue(eastSpan < 50.0, "east-west extent is $eastSpan m")
    }
}
