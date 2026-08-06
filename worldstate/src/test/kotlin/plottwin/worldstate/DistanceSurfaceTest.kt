package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceSurfaceTest {

    @Test
    fun feet_and_inches_convert_to_meters_internally() {
        assertEquals(0.3048, metersOf(feet = 1).value, 1e-9)
        assertEquals(0.0254, metersOf(feet = 0, inches = 1.0).value, 1e-9)
    }

    @Test
    fun meters_format_as_feet_and_inches_at_the_surface() {
        assertEquals("12' 6\"", metersOf(feet = 12, inches = 6.0).toFeetInchesText())
    }

    @Test
    fun inch_rounding_carries_into_the_foot() {
        assertEquals("2' 0\"", Meters(0.3048 * 2 - 0.001).toFeetInchesText())
    }
}
