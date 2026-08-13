package plottwin.eyes

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val MINIMUM_VIEWPOINTS = 6

// A pinned defect, not an exemption. The toy plot's orbit-4 shadow-direction reading is a REAL
// failure: the swale trench out-darkens the greenhouse along that bearing, and the estimator
// attributes shade to entities only, so terrain shade goes unattributed and the caster-population
// floor cannot see it - the greenhouse holds 1.000 of a 3-to-6 sample annulus. A sample floor
// would suppress a true finding, so the failure is characterised instead: exactly this check at
// exactly this viewpoint, no more. When terrain attribution lands, this pin fails and whoever
// fixed it deletes it.
const val PINNED_SHADOW_CHECK = "shadow-direction"
const val PINNED_SHADOW_VIEWPOINT = "orbit-4-of-4"

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
        val failures = inspections.flatMap { failedFindings(it.findings) }
        val pinned = failures.filter { it.check == PINNED_SHADOW_CHECK && it.subject == PINNED_SHADOW_VIEWPOINT }
        val unpinned = failures - pinned.toSet()
        assertTrue(unpinned.isEmpty(), "pixel checks failed:" + report(unpinned))
        assertEquals(
            1,
            pinned.size,
            "the pinned terrain-attribution defect moved or is gone - delete the pin if it passes now:" + report(failures),
        )
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

    private fun report(findings: List<EyeFinding>): String =
        findings.joinToString(separator = System.lineSeparator(), prefix = System.lineSeparator()) { it.line() }
}
