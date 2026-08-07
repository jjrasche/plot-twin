package plottwin.eyes

import kotlin.test.Test
import kotlin.test.assertTrue

class SkylineCheckTest {

    @Test
    fun a_skyline_read_off_the_render_matches_the_one_the_heightfield_predicts() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = plotViewpoints(scene.state).first { it.name.startsWith("orbit-") }
        val comparison = compareSkylines(
            observedSkylineOf(viewer.capture(viewpoint.pose)),
            predictedSkylineOf(scene.spec.meshesByEntity.values, viewer.projectorFor(viewpoint.pose)),
        )
        assertTrue(comparison.coverage >= SKYLINE_COVERAGE_BOUND, "coverage ${comparison.coverage}")
        assertTrue(comparison.agreement >= SKYLINE_AGREEMENT_BOUND, "agreement ${comparison.agreement}, median error ${comparison.medianRowError}")
    }

    @Test
    fun a_skyline_predicted_from_the_wrong_camera_is_rejected() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoints = plotViewpoints(scene.state)
        val rendered = viewpoints.first { it.name == "orbit-1-of-$ORBIT_STEPS" }
        val elsewhere = viewpoints.first { it.name == "orbit-3-of-$ORBIT_STEPS" }
        val comparison = compareSkylines(
            observedSkylineOf(viewer.capture(rendered.pose)),
            predictedSkylineOf(scene.spec.meshesByEntity.values, viewer.projectorFor(elsewhere.pose)),
        )
        assertTrue(
            comparison.agreement < SKYLINE_AGREEMENT_BOUND,
            "a skyline from the opposite side of the plot should not agree, got ${comparison.agreement}",
        )
    }

    @Test
    fun columns_with_nothing_drawn_are_left_out_of_the_comparison() {
        val observed = intArrayOf(10, NOTHING_DRAWN, 30, 40)
        val predicted = intArrayOf(12, 20, NOTHING_DRAWN, 41)
        val comparison = compareSkylines(observed, predicted)
        assertTrue(comparison.comparedColumns == 2, "expected 2 comparable columns, got ${comparison.comparedColumns}")
        assertTrue(comparison.agreement == 1.0, "both comparable columns are within tolerance")
    }
}
