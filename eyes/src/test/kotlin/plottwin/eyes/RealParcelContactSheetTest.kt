package plottwin.eyes

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import plottwin.capture.RealParcelFixture
import plottwin.capture.albedoTriplesOf
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene

class RealParcelContactSheetTest {

    @Test
    fun the_real_parcel_renders_from_every_named_viewpoint_and_passes_pixel_checks() {
        val scene = realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features())
        val viewer = PlotViewer(scene.spec)
        val inspections = inspectPlot(scene, viewer)
        val sheet = writeContactSheet(inspections, File(System.getProperty("user.dir"), "build/real_parcel_contact_sheet.png"))
        println("[real-parcel] wrote ${sheet.absolutePath}")
        println("[real-parcel] sun ${scene.daylight.sun}")
        println(findingsReportOf(inspections))

        assertTrue(inspections.size >= 7, "expected overhead + woods + road + orbit viewpoints, got ${inspections.size}")
        val poseNames = inspections.map { it.viewpoint.name }
        assertTrue("walk-height-in-woods" in poseNames, "missing the walk-height pose inside the woods")
        assertTrue("on-road" in poseNames, "missing the pose on the road corridor")
        val failures = inspections
            .flatMap { failedFindings(it.findings) }
            .filterNot { it.advisory }
        assertTrue(failures.isEmpty(), "pixel checks failed:\n${failures.joinToString("\n") { it.line() }}")
    }

    @Test
    fun the_dem_predicted_skyline_agrees_with_the_rendered_skyline() {
        val scene = realParcelScene(RealParcelFixture.parcel())
        val viewer = PlotViewer(scene.spec)
        val classifier = skyClassifierOf(scene.spec, scene.daylight)
        val comparisons = plotViewpoints(scene.state).map { viewpoint ->
            val image = viewer.capture(viewpoint.pose)
            val projector = viewer.projectorFor(viewpoint.pose)
            val observed = if (classifier == null) observedSkylineOf(image) else observedSkylineOf(image, classifier)
            viewpoint.name to compareSkylines(observed, predictedSkylineOf(terrainAndEntityMeshesOf(scene.spec).values, projector))
        }
        comparisons.forEach { (name, comparison) ->
            println("[real-parcel] $name skyline agreement %.3f coverage %.3f".format(comparison.agreement, comparison.coverage))
        }
        val gated = comparisons.filter { (_, comparison) -> comparison.coverage >= SKYLINE_COVERAGE_BOUND }
        assertTrue(gated.isNotEmpty(), "no viewpoint had enough skyline coverage to compare")
        assertTrue(
            gated.all { (_, comparison) -> comparison.agreement >= SKYLINE_AGREEMENT_BOUND },
            "skyline disagreement: ${gated.filter { it.second.agreement < SKYLINE_AGREEMENT_BOUND }.map { it.first }}",
        )
    }

    @Test
    fun the_full_resolution_compiled_parcel_renders_offline_when_the_capture_cache_is_populated() {
        val compiled = Path.of(System.getProperty("user.dir"), "..", "capture", "data", "compiled", "parcel.json").normalize()
        assumeTrue(Files.exists(compiled), "capture cache absent — run capture/scripts/compile_parcel.py first")
        val scene = realParcelSceneFromFile(compiled)
        val terrain = scene.state.terrain!!.grid
        assertTrue(terrain.columns == 900 && terrain.rows == 900 && terrain.cellSize.value == 0.1, "expected the 10cm 900x900 grid")
        val viewer = PlotViewer(scene.spec)
        val inspections = inspectPlot(scene, viewer)
        val sheet = writeContactSheet(inspections, File(System.getProperty("user.dir"), "build/real_parcel_full_res_contact_sheet.png"))
        println("[real-parcel] wrote ${sheet.absolutePath}")
        val failures = inspections
            .flatMap { failedFindings(it.findings) }
            .filterNot { it.advisory }
        assertTrue(failures.isEmpty(), "pixel checks failed:\n${failures.joinToString("\n") { it.line() }}")
    }

    @Test
    fun naip_color_visibly_changes_the_rendered_parcel() {
        val parcel = RealParcelFixture.parcel()
        val nairColored = realParcelScene(parcel)
        val moment = realParcelMidday(parcel)
        val daylight = daylightOverPlot(nairColored.state, moment)
        val grassOnly = nairColored.copy(spec = projectWalkableScene(nairColored.state, emptyList(), daylight))
        requireNotNull(albedoTriplesOf(parcel)) { "fixture carries no NAIP albedo" }

        val overhead = plotViewpoints(nairColored.state).first { it.name == "overhead" }
        val withNaip = PlotViewer(nairColored.spec).capture(overhead.pose)
        val withGrass = PlotViewer(grassOnly.spec).capture(overhead.pose)
        var differing = 0
        var compared = 0
        for (row in 0 until withNaip.height step 2) {
            for (column in 0 until withNaip.width step 2) {
                compared++
                if (withNaip.getRGB(column, row) != withGrass.getRGB(column, row)) differing++
            }
        }
        val differingShare = differing.toDouble() / compared
        println("[real-parcel] NAIP vs grass albedo: %.1f%% of sampled pixels differ".format(differingShare * 100))
        assertTrue(differingShare > 0.2, "NAIP albedo changed only ${differingShare * 100}% of pixels — texture not visible")
    }
}
