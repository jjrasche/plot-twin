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
        val finding = shadowFinding(viewpoint.name, sample(lit, anchor.x.toDouble(), anchor.y.toDouble()), expected, advisory = false)
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
        val finding = shadowFinding(viewpoint.name, sample(wrongWay, anchor.x.toDouble(), anchor.y.toDouble()), expected, advisory = false)
        assertTrue(!finding.passed, "a shadow 90 deg off the sun should fail: ${finding.line()}")
    }

    @Test
    fun the_reading_taken_off_a_sunless_render_is_marked_advisory_not_a_verdict() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val inspection = inspectViewpoint(scene, viewer, greenhouseViewpoint(scene))
        val shadow = inspection.findings.first { it.check == "shadow-direction" }
        assertTrue(shadow.advisory, "scene3d has no sun pass, so this reading must not gate: ${shadow.line()}")
        assertTrue(failedFindings(inspection.findings).none { it.check == "shadow-direction" }, "advisory findings must not count as failures")
    }

    private fun sample(image: java.awt.image.BufferedImage, centerX: Double, centerY: Double): ShadowEstimate =
        estimateShadowDirection(image, centerX, centerY, SHADOW_INNER_RADIUS_PX, SHADOW_OUTER_RADIUS_PX)
}
