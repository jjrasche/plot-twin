package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import plottwin.render.CUBIC_YARDS_PER_CUBIC_METER
import plottwin.render.StageDiffRender
import plottwin.render.StageDiffSpec
import plottwin.render.paintStageDiff
import plottwin.render.pngBytesOf
import plottwin.render.projectStageDiff

class StageDiffGateTest {

    @Test
    fun dig_here_diff_gates_and_shows_the_haul_off_arrow() {
        val gated = gatedDiffOf(digHereScene())
        assertNotNull(gated.spec.haulOff, "hauled-off material gets an explicit edge-of-plot arrow")
        assertEquals(
            gated.spec.ledger.haulOffCubicMeters * CUBIC_YARDS_PER_CUBIC_METER,
            gated.spec.haulOff!!.looseCubicYards,
            1e-9,
        )
        assertTrue(gated.spec.movements.isEmpty(), "nothing is placed on site, so no on-plot movement link")
    }

    @Test
    fun foundation_pad_diff_gates_and_hauls_its_cut_away() {
        val gated = gatedDiffOf(foundationPadScene())
        assertNotNull(gated.spec.haulOff)
        assertEquals(0.0, gated.spec.ledger.looseSpoilPlacedCubicMeters)
    }

    @Test
    fun berm_adjacent_diff_gates_and_links_cut_to_fill_weighted_by_the_ledger() {
        val gated = gatedDiffOf(bermSpoilScene())
        assertEquals(1, gated.spec.movements.size, "the material's short journey is one visible link")
        val movement = gated.spec.movements.single()
        assertEquals(
            gated.spec.ledger.looseSpoilPlacedCubicMeters * CUBIC_YARDS_PER_CUBIC_METER,
            movement.looseCubicYards,
            1e-9,
        )
        assertTrue(gated.spec.haulOff == null, "zero haul: no edge arrow")
    }

    @Test
    fun identical_log_yields_identical_diff_image_bytes() {
        assertContentEquals(
            pngBytesOf(paintStageDiff(specOf(bermSpoilScene())).image),
            pngBytesOf(paintStageDiff(specOf(bermSpoilScene())).image),
        )
    }

    @Test
    fun three_intents_write_the_stage_diff_contact_sheet() {
        val inspections = listOf(digHereScene(), foundationPadScene(), bermSpoilScene()).map { scene ->
            val gated = gatedDiffOf(scene, failFast = false)
            ViewpointInspection(topDownViewpoint(scene.stageName), gated.render.image, gated.findings)
        }
        val sheet = writeContactSheet(inspections, File(System.getProperty("user.dir"), "build/stage_diff_contact_sheet.png"))
        println(findingsReportOf(inspections))
        println("stage-diff contact sheet: ${sheet.absolutePath}")
        assertTrue(inspections.all { failedFindings(it.findings).isEmpty() }, findingsReportOf(inspections))
    }

    private class GatedDiff(val spec: StageDiffSpec, val render: StageDiffRender, val findings: List<EyeFinding>)

    private fun gatedDiffOf(scene: StageDiffScene, failFast: Boolean = true): GatedDiff {
        val spec = specOf(scene)
        val render = paintStageDiff(spec)
        val findings = stageDiffFindings(scene, spec, render)
        println("[${scene.stageName}]")
        findings.forEach { println("  " + it.line()) }
        if (failFast) assertTrue(failedFindings(findings).isEmpty(), findings.joinToString("\n") { it.line() })
        return GatedDiff(spec, render, findings)
    }

    private fun specOf(scene: StageDiffScene): StageDiffSpec = projectStageDiff(scene.state, scene.proposal)

    // the diff view is a flat top-down map; the sheet's viewpoint slot carries a nominal overhead pose
    private fun topDownViewpoint(name: String): Viewpoint =
        Viewpoint(name, Scene3dCameraPose(eye = Vec3(0f, 100f, 0f), target = Vec3(0f, 0f, 0f)), null)
}
