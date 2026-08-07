package plottwin.solvers

import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.worldstate.SiteRow

// Reda & Andreas, NREL/TP-560-34302 (Solar Position Algorithm), worked example in appendix:
// 2003-10-17 12:30:30 local (UTC-7) at 39.742476 N, 105.1786 W reports azimuth 194.340241 deg.
private const val NREL_EXAMPLE_AZIMUTH_DEGREES = 194.340241
private const val NREL_EXAMPLE_ZENITH_DEGREES = 50.111622
private val nrelExampleSite = SiteRow(39.742476, -105.1786, "America/Denver")
private val nrelExampleMoment: ZonedDateTime = ZonedDateTime.parse("2003-10-17T12:30:30-07:00[America/Denver]")

class SunPositionTest {

    @Test
    fun the_azimuth_at_the_published_nrel_reference_case_matches_within_grena3_accuracy() {
        val ray = sunRayAt(nrelExampleSite, nrelExampleMoment)
        val azimuthError = abs(ray.azimuthDegrees - NREL_EXAMPLE_AZIMUTH_DEGREES)
        val altitudeError = abs(ray.altitudeDegrees - (90.0 - NREL_EXAMPLE_ZENITH_DEGREES))
        assertTrue(azimuthError < 0.1, "azimuth ${ray.azimuthDegrees} deg is ${azimuthError} deg off the published value")
        assertTrue(altitudeError < 0.1, "altitude ${ray.altitudeDegrees} deg is ${altitudeError} deg off the published value")
    }

    @Test
    fun the_michigan_sun_stands_south_and_high_at_summer_solstice_noon() {
        val ray = sunRayAt(ToyPlotFixture.toySite, solarNoonOn(ToyPlotFixture.toySite, LocalDate.of(2026, 6, 21)))
        assertTrue(abs(ray.azimuthDegrees - 180.0) < 2.0, "solar noon should bear due south, got ${ray.azimuthDegrees} deg")
        assertTrue(ray.altitudeDegrees > 65.0, "solstice noon at 42.6N should clear 65 deg, got ${ray.altitudeDegrees}")
    }

    @Test
    fun a_winter_day_carries_fewer_daylight_samples_than_a_summer_day() {
        val winter = daylightRaysOn(ToyPlotFixture.toySite, LocalDate.of(2026, 12, 21)).size
        val summer = daylightRaysOn(ToyPlotFixture.toySite, LocalDate.of(2026, 6, 21)).size
        assertTrue(summer > winter, "summer $summer samples should beat winter $winter")
    }
}
