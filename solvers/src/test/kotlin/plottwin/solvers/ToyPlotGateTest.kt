package plottwin.solvers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToyPlotGateTest {

    @Test
    fun toy_plot_flags_the_pergola_waterlog_then_the_path_pinch() {
        val ranked = runSolvers(ToyPlotFixture.toyWorld(), ToyPlotFixture.toyConstraints())

        assertEquals(listOf("pergola-drainage", "path-clearance"), ranked.map { it.ruleName })

        val waterlog = ranked[0]
        assertTrue(waterlog.location.east.value in 29.0..31.0)
        assertTrue(waterlog.location.north.value in 10.0..13.0)
        assertTrue(waterlog.magnitude > 0.0)

        val pinch = ranked[1]
        assertEquals(ToyPlotFixture.pathClearanceBound.value - 0.75, pinch.magnitude)
        assertEquals(49.25, pinch.location.east.value)
        assertEquals(75.0, pinch.location.north.value)
    }

    @Test
    fun toy_run_is_deterministic_across_repeats() {
        val world = ToyPlotFixture.toyWorld()
        val constraints = ToyPlotFixture.toyConstraints()

        assertEquals(runSolvers(world, constraints), runSolvers(world, constraints))
    }
}
