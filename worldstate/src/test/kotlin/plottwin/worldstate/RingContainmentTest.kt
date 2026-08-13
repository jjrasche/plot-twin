package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingContainmentTest {

    @Test
    fun point_inside_a_square_is_inside() {
        assertTrue(isInsidePolygon(groundPoint(1.0, 1.0), unitSquareTimesTwo()))
    }

    @Test
    fun point_outside_a_square_is_outside() {
        assertFalse(isInsidePolygon(groundPoint(3.0, 1.0), unitSquareTimesTwo()))
    }

    @Test
    fun point_in_the_notch_of_a_concave_ring_is_outside() {
        val notchedRing = listOf(
            groundPoint(0.0, 0.0),
            groundPoint(4.0, 0.0),
            groundPoint(4.0, 4.0),
            groundPoint(2.0, 1.0),
            groundPoint(0.0, 4.0),
        )
        assertFalse(isInsidePolygon(groundPoint(2.0, 3.0), notchedRing))
        assertTrue(isInsidePolygon(groundPoint(0.5, 1.0), notchedRing))
    }

    private fun unitSquareTimesTwo(): List<GroundPoint> = listOf(
        groundPoint(0.0, 0.0),
        groundPoint(2.0, 0.0),
        groundPoint(2.0, 2.0),
        groundPoint(0.0, 2.0),
    )

    private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))
}
