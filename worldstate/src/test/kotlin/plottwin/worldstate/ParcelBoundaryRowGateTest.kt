package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private val COUNTY_PROVENANCE = BoundaryProvenance(
    source = "https://example.gov/parcels/FeatureServer/0 PARCELID=0000",
    pulledAtUtc = "2026-08-13T03:47:59Z",
    observedAt = "2026-07-21T18:51:44Z",
    sha256 = "0".repeat(64),
    contract = "interim-county-service",
)

private val PLOT_FRAME = GroundFrame("EPSG:26916", Meters(694976.724), Meters(4728335.339))

private fun boundaryRow(ring: List<GroundPoint>) = ParcelBoundaryRow(
    parcelId = "0000",
    ring = ring,
    frame = PLOT_FRAME,
    acresStated = 1.0,
    provenance = COUNTY_PROVENANCE,
)

private val SQUARE_RING = listOf(
    GroundPoint(Meters(0.0), Meters(0.0)),
    GroundPoint(Meters(10.0), Meters(0.0)),
    GroundPoint(Meters(10.0), Meters(10.0)),
    GroundPoint(Meters(0.0), Meters(10.0)),
)

class ParcelBoundaryRowGateTest {

    @Test
    fun the_boundary_round_trips_through_the_log_with_its_frame_and_provenance() {
        WorldLog.openInMemory().use { log ->
            log.append(boundaryRow(SQUARE_RING), WriterRole.CAPTURE)
            val boundary = assertNotNull(log.currentState().parcelBoundary)
            assertEquals(SQUARE_RING, boundary.ring)
            assertEquals(PLOT_FRAME, boundary.frame)
            assertEquals(COUNTY_PROVENANCE, boundary.provenance)
        }
    }

    @Test
    fun the_property_line_is_measured_ground_so_only_capture_may_append_it() {
        WorldLog.openInMemory().use { log ->
            assertFailsWith<GeometryWriteRejected> {
                log.append(boundaryRow(SQUARE_RING), WriterRole.OPTIMIZER)
            }
        }
    }

    @Test
    fun a_ring_carrying_its_closing_vertex_is_rejected_as_stored_closed() {
        assertFailsWith<IllegalArgumentException> { boundaryRow(SQUARE_RING + SQUARE_RING.first()) }
    }

    @Test
    fun a_boundary_with_neither_an_observed_date_nor_a_reason_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            COUNTY_PROVENANCE.copy(observedAt = null, observedAtAbsentReason = null)
        }
    }

    @Test
    fun a_boundary_claiming_both_an_observed_date_and_a_reason_it_has_none_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            COUNTY_PROVENANCE.copy(observedAtAbsentReason = "service exposes no currency field")
        }
    }
}
