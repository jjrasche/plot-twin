package plottwin.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters

class PointGeometryTest {

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

    @Test
    fun closest_point_projects_perpendicularly_onto_the_segment() {
        val closest = closestPointOnSegment(groundPoint(0.0, 0.0), groundPoint(10.0, 0.0), groundPoint(4.0, 3.0))
        assertEquals(groundPoint(4.0, 0.0), closest)
    }

    @Test
    fun closest_point_clamps_to_the_nearer_endpoint() {
        val closest = closestPointOnSegment(groundPoint(0.0, 0.0), groundPoint(10.0, 0.0), groundPoint(12.0, 3.0))
        assertEquals(groundPoint(10.0, 0.0), closest)
    }

    @Test
    fun zero_length_segment_answers_with_its_single_point() {
        val closest = closestPointOnSegment(groundPoint(2.0, 2.0), groundPoint(2.0, 2.0), groundPoint(5.0, 5.0))
        assertEquals(groundPoint(2.0, 2.0), closest)
    }

    @Test
    fun distance_to_segment_is_the_perpendicular_drop() {
        assertEquals(3.0, distanceToSegment(groundPoint(4.0, 3.0), groundPoint(0.0, 0.0), groundPoint(10.0, 0.0)))
    }

    @Test
    fun distance_between_is_euclidean() {
        assertEquals(5.0, distanceBetween(groundPoint(0.0, 0.0), groundPoint(3.0, 4.0)))
    }

    private fun unitSquareTimesTwo(): List<GroundPoint> = listOf(
        groundPoint(0.0, 0.0),
        groundPoint(2.0, 0.0),
        groundPoint(2.0, 2.0),
        groundPoint(0.0, 2.0),
    )

    private fun groundPoint(east: Double, north: Double) = GroundPoint(Meters(east), Meters(north))
}
