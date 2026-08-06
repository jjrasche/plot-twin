package plottwin.eyes

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

class ShadowDirectionCheckTest {

    private fun greenhouseViewpoint(scene: PlotScene): Viewpoint =
        plotViewpoints(scene.state).first { it.subject == "greenhouse" }

    @Test
    fun a_shadow_cast_at_the_suns_own_angle_reads_back_as_agreement() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = greenhouseViewpoint(scene)
        val projector = viewer.projectorFor(viewpoint.pose)
        val groundPoint = groundSampleOf(scene.state, viewpoint)
        val anchor = projector.project(groundPoint)
        val expected = requireNotNull(expectedShadowScreenRadians(projector, groundPoint, SUN_AZIMUTH_DEGREES_AT_TOY_NOON))
        val lit = paintShadowLobe(
            viewer.capture(viewpoint.pose),
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            expected,
            SHADOW_OUTER_RADIUS_PX,
        )
        val finding = shadowFinding(viewpoint.name, sample(lit, anchor.x.toDouble(), anchor.y.toDouble()), expected)
        assertTrue(finding.passed, "sun-aligned shadow was rejected: ${finding.line()}")
        assertTrue(finding.measured <= SHADOW_AZIMUTH_TOLERANCE_DEGREES, "azimuth error ${finding.measured} deg")
    }

    @Test
    fun a_shadow_cast_across_the_sun_is_rejected() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = greenhouseViewpoint(scene)
        val projector = viewer.projectorFor(viewpoint.pose)
        val groundPoint = groundSampleOf(scene.state, viewpoint)
        val anchor = projector.project(groundPoint)
        val expected = requireNotNull(expectedShadowScreenRadians(projector, groundPoint, SUN_AZIMUTH_DEGREES_AT_TOY_NOON))
        val wrongWay = paintShadowLobe(
            viewer.capture(viewpoint.pose),
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            expected + PI / 2,
            SHADOW_OUTER_RADIUS_PX,
        )
        val finding = shadowFinding(viewpoint.name, sample(wrongWay, anchor.x.toDouble(), anchor.y.toDouble()), expected)
        assertTrue(!finding.passed, "a shadow 90 deg off the sun should fail: ${finding.line()}")
    }

    @Test
    fun a_frame_with_no_directional_darkening_reports_no_signal() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val viewpoint = greenhouseViewpoint(scene)
        val projector = viewer.projectorFor(viewpoint.pose)
        val anchor = projector.project(groundSampleOf(scene.state, viewpoint))
        val estimate = sample(viewer.capture(viewpoint.pose), anchor.x.toDouble(), anchor.y.toDouble())
        assertTrue(!estimate.hasSignal, "scene3d has no sun pass yet, so contrast ${estimate.contrast} should stay under the floor")
    }

    private fun sample(image: java.awt.image.BufferedImage, centerX: Double, centerY: Double): ShadowEstimate =
        estimateShadowDirection(image, centerX, centerY, SHADOW_INNER_RADIUS_PX, SHADOW_OUTER_RADIUS_PX)
}
