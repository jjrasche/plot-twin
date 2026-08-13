package plottwin.eyes

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.solvers.ToyPlotFixture

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
        val expected = requireNotNull(
            expectedShadowScreenRadians(projector, groundPoint, scene.daylight.sun.azimuthDegrees),
        )
        val lit = paintShadowLobe(
            viewer.capture(viewpoint.pose),
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            expected,
            SHADOW_OUTER_RADIUS_PX,
        )
        val population = populationAt(scene, viewer, viewpoint)
        assertTrue(population.hasPrincipalCaster, "the toy plot must keep a principal caster: ${population.stated()}")
        val finding = shadowFinding(viewpoint.name, sample(scene, lit, anchor.x.toDouble(), anchor.y.toDouble()), expected, population)
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
        val expected = requireNotNull(
            expectedShadowScreenRadians(projector, groundPoint, scene.daylight.sun.azimuthDegrees),
        )
        val wrongWay = paintShadowLobe(
            viewer.capture(viewpoint.pose),
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            expected + PI / 2,
            SHADOW_OUTER_RADIUS_PX,
        )
        val population = populationAt(scene, viewer, viewpoint)
        val finding = shadowFinding(viewpoint.name, sample(scene, wrongWay, anchor.x.toDouble(), anchor.y.toDouble()), expected, population)
        assertTrue(!finding.advisory, "a one-caster plot must gate, not self-suppress: ${population.stated()}")
        assertTrue(!finding.passed, "a shadow 90 deg off the sun should fail: ${finding.line()}")
    }

    @Test
    fun the_rendered_evening_shadow_gates_and_agrees_with_the_solvers_sun() {
        val scene = toyPlotScene(ToyPlotFixture.toyEvening)
        val inspection = inspectViewpoint(scene, PlotViewer(scene.spec), greenhouseViewpoint(scene))
        val shadow = inspection.findings.first { it.check == "shadow-direction" }
        assertTrue(!shadow.advisory, "the renderer casts sun shadows now, so this reading must gate: ${shadow.line()}")
        assertTrue(shadow.passed, "rendered shadow disagrees with the sun: ${shadow.line()}")
    }

    @Test
    fun the_sun_the_check_compares_against_comes_from_the_solver_not_a_constant() {
        val morningAzimuth = toyPlotScene(ToyPlotFixture.toyMorning).daylight.sun.azimuthDegrees
        val eveningAzimuth = toyPlotScene(ToyPlotFixture.toyEvening).daylight.sun.azimuthDegrees
        assertTrue(morningAzimuth < 180.0, "a 9am August sun should stand east of south, got $morningAzimuth")
        assertTrue(eveningAzimuth > 180.0, "a 6:30pm August sun should stand west of south, got $eveningAzimuth")
    }

    private fun populationAt(scene: PlotScene, viewer: PlotViewer, viewpoint: Viewpoint): CasterPopulation =
        requireNotNull(
            shadowReadingAt(
                scene,
                viewer,
                viewpoint,
                viewer.capture(viewpoint.pose),
                skyClassifierOf(scene.spec, scene.daylight),
            ),
        ).population

    private fun sample(scene: PlotScene, image: java.awt.image.BufferedImage, centerX: Double, centerY: Double): ShadowEstimate =
        estimateShadowDirection(
            image,
            centerX,
            centerY,
            SHADOW_INNER_RADIUS_PX,
            SHADOW_OUTER_RADIUS_PX,
            skyPixel = skyPixelOf(skyClassifierOf(scene.spec, scene.daylight)),
        )
}
