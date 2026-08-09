package plottwin.solvers

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.worldstate.Surface

private val measuredDay: LocalDate = LocalDate.of(2026, 6, 21)

class SunshedScaleMeasurementTest {

    @Test
    fun one_sweep_over_the_whole_two_acre_grid_stays_inside_a_render_frame_budget() {
        val surface = requireNotNull(occluderSurfaceOf(ToyPlotFixture.toyState()))
        val noonRay = sunRayAt(ToyPlotFixture.toySite, solarNoonOn(ToyPlotFixture.toySite, measuredDay))
        repeat(3) { sunlitFractionsAt(surface, noonRay) }

        val sweepCount = 10
        val startedAt = System.nanoTime()
        repeat(sweepCount) { sunlitFractionsAt(surface, noonRay) }
        val millisPerSweep = (System.nanoTime() - startedAt) / 1e6 / sweepCount

        println("[sunshed] ${surface.cellCount} cells, one sunlit sweep %.1f ms".format(millisPerSweep))
        assertTrue(millisPerSweep < 100.0, "a single sweep took %.1f ms".format(millisPerSweep))
    }

    @Test
    fun a_whole_solstice_day_of_sun_hours_over_two_acres_stays_inside_a_solver_run() {
        val state = ToyPlotFixture.toyState()
        val startedAt = System.nanoTime()
        val hours = requireNotNull(UNCACHED_SUNSHED_FIELD.directSunHoursOf(state, measuredDay, Surface.Measured))
        val elapsedMillis = (System.nanoTime() - startedAt) / 1e6

        val daylightSamples = daylightRaysOn(ToyPlotFixture.toySite, measuredDay).size
        println("[sunshed] ${hours.size} cells x $daylightSamples samples in %.0f ms".format(elapsedMillis))
        println("[sunshed] open-ground hours %.2f, shadiest cell %.2f".format(hours.max(), hours.min()))
        assertTrue(elapsedMillis < 20_000.0, "a day of sunshed took %.0f ms".format(elapsedMillis))
    }
}
