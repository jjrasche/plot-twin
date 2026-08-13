package plottwin.eyes

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.capture.RealParcelFixture
import plottwin.solvers.ToyPlotFixture

const val MUTATED_BEARING_RADIANS = PI / 2
const val WOODLOT_MINIMUM_CASTERS = 4

class NoPrincipalShadowGateTest {

    @Test
    fun the_woodlot_suppresses_its_bearing_and_states_the_distribution_that_bought_the_suppression() {
        val scene = realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features())
        val readings = inspectPlot(scene, PlotViewer(scene.spec)).map { inspection ->
            inspection.findings.first { it.check == "shadow-direction" }
        }
        println(readings.joinToString("\n") { it.line() })

        assertTrue(readings.size >= 7, "expected at least seven woodlot poses, got ${readings.size}")
        assertTrue(
            readings.all { it.advisory },
            "a 97-caster woodlot still gated a bearing:\n${readings.filterNot { it.advisory }.joinToString("\n") { it.line() }}",
        )
        assertTrue(
            readings.all { "shade in this annulus comes from" in it.detail },
            "an advisory reading did not state the distribution it rests on:\n${readings.joinToString("\n") { it.line() }}",
        )
    }

    private fun populationsOf(scene: PlotScene, viewer: PlotViewer): List<CasterPopulation> {
        val shadowedGround = shadowedGroundOf(scene.state, scene.daylight.sun)
        val classifier = skyClassifierOf(scene.spec, scene.daylight)
        return plotViewpoints(scene.state).mapNotNull { viewpoint ->
            shadowReadingAt(scene, viewer, viewpoint, viewer.capture(viewpoint.pose), classifier, shadowedGround)
                ?.population
        }
    }

    @Test
    fun the_toy_plots_one_caster_keeps_gating_and_a_mutated_bearing_still_reads_red() {
        val scene = toyPlotScene(ToyPlotFixture.toyEvening)
        val viewer = PlotViewer(scene.spec)
        val viewpoint = plotViewpoints(scene.state).first { it.subject == "greenhouse" }
        val reading = requireNotNull(
            shadowReadingAt(
                scene,
                viewer,
                viewpoint,
                viewer.capture(viewpoint.pose),
                skyClassifierOf(scene.spec, scene.daylight),
            ),
        )
        val honest = shadowFinding(viewpoint.name, reading.estimate, reading.expectedScreenRadians, reading.population)
        // Only the bearing moves: same render, same annulus, same contrast, same population.
        val mutated = shadowFinding(
            viewpoint.name,
            reading.estimate,
            reading.expectedScreenRadians + MUTATED_BEARING_RADIANS,
            reading.population,
        )
        println("honest  ${honest.line()}")
        println("mutated ${mutated.line()}")

        assertTrue(!honest.advisory && honest.passed, "the toy plot must still gate and agree: ${honest.line()}")
        assertTrue(!mutated.advisory, "suppression rescued a wrong bearing on a one-caster plot: ${mutated.line()}")
        assertTrue(!mutated.passed, "a bearing turned a quarter turn still read as agreement: ${mutated.line()}")
    }
}
