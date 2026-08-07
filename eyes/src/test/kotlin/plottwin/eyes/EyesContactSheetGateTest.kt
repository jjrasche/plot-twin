package plottwin.eyes

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

const val MINIMUM_VIEWPOINTS = 6

class EyesContactSheetGateTest {

    @Test
    fun every_named_viewpoint_of_the_toy_plot_passes_its_pixel_checks() {
        val scene = toyPlotScene()
        val viewer = PlotViewer(scene.spec)
        val inspections = inspectPlot(scene, viewer)
        val sheet = writeContactSheet(inspections, File(System.getProperty("user.dir"), "build/eyes_contact_sheet.png"))
        println("[eyes] wrote ${sheet.absolutePath}")
        println("[eyes] sun ${scene.daylight.sun}")
        println(findingsReportOf(inspections))

        assertTrue(
            inspections.size >= MINIMUM_VIEWPOINTS,
            "contact sheet needs at least $MINIMUM_VIEWPOINTS viewpoints, got ${inspections.size}",
        )
        val failures = inspections.flatMap { failedFindings(it.findings) }.filterNot { it.check == "shadow-direction" }
        assertTrue(failures.isEmpty(), "pixel checks failed:\n${failures.joinToString("\n") { it.line() }}")
    }

    @Test
    fun no_viewpoint_throws_the_shadow_into_the_suns_own_half_plane_and_most_land_on_it() {
        val scene = toyPlotScene()
        val readings = inspectPlot(scene, PlotViewer(scene.spec)).map { inspection ->
            inspection.findings.first { it.check == "shadow-direction" }
        }
        println(readings.joinToString("\n") { it.line() })

        val agreeing = readings.count { it.passed }
        assertTrue(
            agreeing * 3 >= readings.size * 2,
            "only $agreeing of ${readings.size} viewpoints put the shadow within $SHADOW_AZIMUTH_TOLERANCE_DEGREES deg of the sun",
        )
        val wrongHalfPlane = readings.filter { it.measured > 90.0 }
        assertTrue(
            wrongHalfPlane.isEmpty(),
            "a shadow pointing into the sun's own half-plane:\n${wrongHalfPlane.joinToString("\n") { it.line() }}",
        )
    }

    @Test
    fun the_named_viewpoints_cover_overhead_walk_height_and_an_orbit() {
        val names = plotViewpoints(toyPlotScene().state).map { it.name }
        assertTrue(names.contains("overhead"), "missing the overhead viewpoint, got $names")
        assertTrue(names.contains("walk-height-at-greenhouse"), "missing a walk-height viewpoint, got $names")
        assertTrue(names.count { it.startsWith("orbit-") } == ORBIT_STEPS, "expected $ORBIT_STEPS orbit steps, got $names")
    }
}
