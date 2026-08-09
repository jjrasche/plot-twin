package plottwin.eyes

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.solvers.SolverWorld
import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.Meters
import plottwin.worldstate.RawElevation
import plottwin.worldstate.SiteRow
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

private const val HILL_CELLS_PER_SIDE = 300
private const val HILL_CELL_SIZE_METERS = 0.1

// Rolling hills with a knoll — a heightfield the toy fixture never drew, entered through the
// same log rows a real parcel will use.
private fun rollingHillHeights(): FloatArray {
    val heights = FloatArray(HILL_CELLS_PER_SIDE * HILL_CELLS_PER_SIDE)
    for (row in 0 until HILL_CELLS_PER_SIDE) {
        for (column in 0 until HILL_CELLS_PER_SIDE) {
            val alongNorth = row.toDouble() / HILL_CELLS_PER_SIDE
            val alongEast = column.toDouble() / HILL_CELLS_PER_SIDE
            val ridges = 2.5 * sin(PI * alongNorth) + 1.2 * cos(2 * PI * alongEast)
            val knoll = 3.0 * exp(-(((alongEast - 0.7) * 6).let { it * it } + ((alongNorth - 0.3) * 6).let { it * it }))
            heights[row * HILL_CELLS_PER_SIDE + column] = (ridges + knoll).toFloat()
        }
    }
    return heights
}

private fun rollingHillWorld(): SolverWorld = WorldLog.openInMemory().use { log ->
    log.append(
        GriddedElevationOperator.compileBaseTerrain(
            RawElevation(HILL_CELLS_PER_SIDE, HILL_CELLS_PER_SIDE, Meters(HILL_CELL_SIZE_METERS), rollingHillHeights()),
        ),
        WriterRole.CAPTURE,
    )
    log.append(SiteRow(latitudeDegrees = 42.6006, longitudeDegrees = -84.6547, timeZoneId = "America/Detroit"), WriterRole.CAPTURE)
    SolverWorld(log.currentState(), LocalDate.of(2026, 8, 5))
}

class HeightfieldSkylineGateTest {

    @Test
    fun the_gate_holds_on_a_heightfield_the_toy_fixture_never_drew() {
        val world = rollingHillWorld()
        val midday = LocalDate.of(2026, 8, 5).atTime(13, 0).atZone(ZoneId.of("America/Detroit"))
        val scene = plotSceneOf(world, emptyList(), midday)
        val inspections = inspectPlot(scene, PlotViewer(scene.spec))
        val sheet = writeContactSheet(inspections, File(System.getProperty("user.dir"), "build/heightfield_contact_sheet.png"))
        println("[heightfield] wrote ${sheet.absolutePath}")
        println(findingsReportOf(inspections))

        assertTrue(inspections.size >= 5, "expected overhead plus orbits, got ${inspections.size}")
        val failures = inspections.flatMap { failedFindings(it.findings) }.filterNot { it.check == "shadow-direction" }
        assertTrue(failures.isEmpty(), "pixel checks failed on the rolling hills:\n${failures.joinToString("\n") { it.line() }}")
    }
}
