package plottwin.eyes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.capture.CaptureCache
import plottwin.capture.RealParcelFixture
import plottwin.capture.albedoTriplesOf
import plottwin.capture.fixtureBindingDisagreementsOf
import plottwin.capture.parcelBoundaryRowOf
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene
import plottwin.render.TERRAIN_ENTITY_ID
import plottwin.worldstate.GridExtent
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.gridExtentOf

class RealParcelContactSheetTest {

    @Test
    fun the_real_parcel_renders_from_every_named_viewpoint_and_passes_pixel_checks() {
        val scene = realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features(), RealParcelFixture.boundary())
        val viewer = PlotViewer(scene.spec)
        val inspections = inspectPlot(scene, viewer)
        val sheet = writeContactSheet(inspections, judgedSheetFile("real_parcel_fixture_contact_sheet"))
        announceSheet(sheet)
        println("[real-parcel] sun ${scene.daylight.sun}")
        println(findingsReportOf(inspections))

        val poseNames = inspections.map { it.viewpoint.name }
        assertEquals(expectedPoseCountOf(scene.state), inspections.size, "poses drifted from what the log holds")
        assertTrue("walk-height-in-woods" in poseNames, "missing the walk-height pose inside the woods")
        assertEquals(
            scene.state.entities.containsKey(ROAD_ENTITY_NAME),
            "on-road" in poseNames,
            "the on-road pose and the road corridor row disagree",
        )
        val failures = inspections
            .flatMap { failedFindings(it.findings) }
            .filterNot { it.advisory }
        assertTrue(failures.isEmpty(), "pixel checks failed:\n${failures.joinToString("\n") { it.line() }}")
    }

    @Test
    fun every_viewpoint_carries_enough_skyline_to_compare_and_agrees_with_the_dem_prediction() {
        val scene = realParcelScene(RealParcelFixture.parcel(), boundary = RealParcelFixture.boundary())
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
        val thin = comparisons.filter { (_, comparison) -> comparison.coverage < SKYLINE_COVERAGE_BOUND }
        assertTrue(
            thin.isEmpty(),
            "too little skyline to compare at ${thin.map { "${it.first} %.3f".format(it.second.coverage) }}, bound $SKYLINE_COVERAGE_BOUND",
        )
        val disagreeing = comparisons.filter { (_, comparison) -> comparison.agreement < SKYLINE_AGREEMENT_BOUND }
        assertTrue(disagreeing.isEmpty(), "skyline disagreement: ${disagreeing.map { it.first }}")
    }

    @Test
    fun the_full_resolution_compiled_parcel_renders_offline_when_the_capture_cache_is_populated() {
        val compiled = CaptureCache.compiledParcel()
        // Asserted before a pixel is drawn: a sheet rendered from a cut the tracked fixture does
        // not name is an image no reader of this repo can reproduce, which is what a receipt is for.
        val disagreements = fixtureBindingDisagreementsOf(RealParcelFixture.parcel(), compiled)
        assertTrue(disagreements.isEmpty(), disagreements.joinToString("\n"))
        println("[real-parcel] fixture binds ${compiled.fileName} at sha256 ${RealParcelFixture.parcel().provenance.compiledParcel?.sha256}")
        val scene = realParcelSceneFromFile(compiled)
        val terrain = scene.state.terrain!!.grid
        val expectedExtent = gridExtentOf(parcelBoundaryRowOf(RealParcelFixture.boundary()), terrain.cellSize)
        println("[real-parcel] full-res grid ${terrain.columns}x${terrain.rows} at ${terrain.cellSize.value} m")
        assertEquals(0.1, terrain.cellSize.value, "the full-res cut is not the 10cm grid")
        assertEquals(expectedExtent, GridExtent(terrain.columns, terrain.rows), "the full-res cut is not the property line's bounding box")
        val viewer = PlotViewer(scene.spec)
        val inspections = inspectPlot(scene, viewer)
        val sheet = writeContactSheet(inspections, judgedSheetFile("real_parcel_full_res_contact_sheet"))
        announceSheet(sheet)
        val failures = inspections
            .flatMap { failedFindings(it.findings) }
            .filterNot { it.advisory }
        assertTrue(failures.isEmpty(), "pixel checks failed:\n${failures.joinToString("\n") { it.line() }}")
    }

    @Test
    fun naip_color_visibly_changes_the_rendered_parcel() {
        val parcel = RealParcelFixture.parcel()
        val nairColored = realParcelScene(parcel, boundary = RealParcelFixture.boundary())
        val moment = realParcelMidday(parcel)
        val daylight = daylightOverPlot(nairColored.state, moment)
        val grassOnly = nairColored.copy(spec = projectWalkableScene(nairColored.state, emptyList(), daylight))
        requireNotNull(albedoTriplesOf(parcel)) { "fixture carries no NAIP albedo" }

        val overhead = plotViewpoints(nairColored.state).first { it.name == "overhead" }
        val viewer = PlotViewer(nairColored.spec)
        val withNaip = viewer.capture(overhead.pose)
        val withGrass = PlotViewer(grassOnly.spec).capture(overhead.pose)
        // Only the parcel's own ground carries NAIP, so only its own pixels can answer whether the
        // texture arrived: the sky and the neutral surround are identical in both arms and would
        // otherwise dilute the share, or worse, carry it.
        val groundOnly = nairColored.spec.meshesByEntity.filterKeys { it == TERRAIN_ENTITY_ID }
        val ground = rasterizeVisibleSurfaces(groundOnly, viewer.projectorFor(overhead.pose))
        var differing = 0
        var compared = 0
        for (pixel in ground.owner.indices) {
            if (ground.owner[pixel] == NO_SURFACE) continue
            compared++
            val column = pixel % ground.width
            val row = pixel / ground.width
            if (withNaip.getRGB(column, row) != withGrass.getRGB(column, row)) differing++
        }
        val differingShare = differing.toDouble() / compared
        println("[real-parcel] NAIP vs grass albedo: %.1f%% of %d ground pixels differ".format(differingShare * 100, compared))
        assertTrue(differingShare > 0.9, "NAIP albedo changed only ${differingShare * 100}% of the parcel's ground — texture not visible")
    }

}
