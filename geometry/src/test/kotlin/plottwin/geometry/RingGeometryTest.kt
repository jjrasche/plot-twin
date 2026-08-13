package plottwin.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))

private val TEN_METRE_SQUARE = listOf(
    groundPoint(0.0, 0.0),
    groundPoint(10.0, 0.0),
    groundPoint(10.0, 10.0),
    groundPoint(0.0, 10.0),
)

class RingGeometryTest {

    @Test
    fun ring_area_is_the_same_whichever_way_the_ring_winds() {
        assertEquals(100.0, ringAreaSquareMeters(TEN_METRE_SQUARE), 1e-9)
        assertEquals(100.0, ringAreaSquareMeters(TEN_METRE_SQUARE.reversed()), 1e-9)
    }

    @Test
    fun a_repeated_closing_vertex_makes_the_open_ring_non_simple() {
        assertTrue(isSimpleRing(TEN_METRE_SQUARE))
        assertFalse(isSimpleRing(TEN_METRE_SQUARE + TEN_METRE_SQUARE.first()))
    }

    @Test
    fun a_bowtie_is_not_a_simple_ring() {
        val bowtie = listOf(
            groundPoint(0.0, 0.0),
            groundPoint(10.0, 10.0),
            groundPoint(10.0, 0.0),
            groundPoint(0.0, 10.0),
        )
        assertFalse(isSimpleRing(bowtie))
    }

    @Test
    fun clipping_a_ring_that_straddles_the_box_keeps_only_the_overlap() {
        val clipped = clipRingToBox(TEN_METRE_SQUARE, groundPoint(5.0, -5.0), groundPoint(20.0, 20.0))
        assertEquals(50.0, ringAreaSquareMeters(clipped), 1e-9)
    }

    @Test
    fun clipping_a_ring_already_inside_the_box_changes_nothing() {
        val clipped = clipRingToBox(TEN_METRE_SQUARE, groundPoint(-1.0, -1.0), groundPoint(11.0, 11.0))
        assertEquals(100.0, ringAreaSquareMeters(clipped), 1e-9)
    }

    @Test
    fun a_ring_outside_the_box_clips_away_to_nothing() {
        val clipped = clipRingToBox(TEN_METRE_SQUARE, groundPoint(50.0, 50.0), groundPoint(60.0, 60.0))
        assertEquals(0.0, ringAreaSquareMeters(clipped), 1e-9)
    }
}
