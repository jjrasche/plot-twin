package plottwin.eyes

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.capture.RealParcelFixture
import plottwin.capture.appendParcelFeatures
import plottwin.capture.appendRealParcel
import plottwin.render.groundHeightAt
import plottwin.solvers.occluderSurfaceOf
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.WorldLog
import plottwin.worldstate.isTreeEntity
import plottwin.worldstate.isWaterEntity

const val CANOPY_COVER_TOLERANCE = 0.15

class TrueThreeDGateTest {

    private fun woodedScene() = realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features())

    private fun bareScene() = realParcelScene(RealParcelFixture.parcel())

    @Test
    fun canopy_entities_raise_the_occluder_surface_so_trees_cast_shade() {
        val state = WorldLog.openInMemory().use { log ->
            appendRealParcel(log, RealParcelFixture.parcel())
            appendParcelFeatures(log, RealParcelFixture.features())
            log.currentState()
        }
        val occluder = requireNotNull(occluderSurfaceOf(state)) { "no occluder surface" }
        val terrain = state.terrain!!.grid
        val tallest = RealParcelFixture.features().trees.maxByOrNull { it.heightMeters }!!
        val crownCenter = GroundPoint(Meters(tallest.eastMeters), Meters(tallest.northMeters))
        val column = (tallest.eastMeters / occluder.cellSize.value).toInt()
        val row = (tallest.northMeters / occluder.cellSize.value).toInt()
        val occluderHeight = occluder.heights[row * occluder.columns + column]
        val bareGround = groundHeightAt(terrain, crownCenter)
        val raised = occluderHeight - bareGround
        println("[gates] occluder under tallest crown raised %.1f m (tree %.1f m)".format(raised, tallest.heightMeters))
        assertTrue(
            raised >= tallest.heightMeters.toFloat() * 0.9f,
            "occluder under the tallest crown raised only $raised m for a ${tallest.heightMeters} m tree",
        )
    }

    @Test
    fun rendered_overhead_canopy_cover_stays_within_the_chm_band() {
        val scene = woodedScene()
        val viewer = PlotViewer(scene.spec)
        val overhead = plotViewpoints(scene.state).first { it.name == "overhead" }
        val projector = viewer.projectorFor(overhead.pose)
        val meshes = terrainAndEntityMeshesOf(scene.spec)
        val surface = rasterizeVisibleSurfaces(meshes, projector)
        val treeOwners = meshes.keys.withIndex().filter { isTreeEntity(it.value) }.map { it.index }.toHashSet()
        var canopyPixels = 0
        var plotPixels = 0
        for (owner in surface.owner) {
            if (owner == NO_SURFACE) continue
            plotPixels++
            if (owner in treeOwners) canopyPixels++
        }
        val renderedCover = canopyPixels.toDouble() / plotPixels
        val chmCover = RealParcelFixture.features().receipts.canopyCoverFraction
        println("[gates] rendered canopy cover %.3f vs CHM cover %.3f".format(renderedCover, chmCover))
        assertTrue(
            abs(renderedCover - chmCover) <= CANOPY_COVER_TOLERANCE,
            "rendered canopy cover $renderedCover strays over ${CANOPY_COVER_TOLERANCE} from CHM $chmCover",
        )
    }

    @Test
    fun orbit_skyline_is_canopy_rough_not_bare_terrain_flat() {
        val wooded = woodedScene()
        val bare = bareScene()
        val orbitPose = plotViewpoints(wooded.state).first { it.name.startsWith("orbit-1") }
        val woodedRoughness = skylineRoughnessOf(wooded, orbitPose)
        val bareRoughness = skylineRoughnessOf(bare, orbitPose)
        println("[gates] orbit skyline roughness wooded %.3f vs bare %.3f".format(woodedRoughness, bareRoughness))
        assertTrue(
            woodedRoughness > bareRoughness,
            "wooded skyline roughness $woodedRoughness is not above the bare-terrain baseline $bareRoughness",
        )
    }

    @Test
    fun water_renders_exactly_where_the_extraction_put_it_which_here_is_nowhere() {
        val features = RealParcelFixture.features()
        val scene = woodedScene()
        val waterEntities = scene.state.entities.keys.filter(::isWaterEntity)
        assertTrue(
            waterEntities.size == features.water.size,
            "water in the render (${waterEntities.size}) drifted from water in the extraction (${features.water.size})",
        )
        if (features.water.isEmpty()) {
            assertTrue(scene.spec.meshesByEntity.keys.none(::isWaterEntity), "a water mesh appeared without a water row")
            return
        }
        val viewer = PlotViewer(scene.spec)
        val overhead = plotViewpoints(scene.state).first { it.name == "overhead" }
        val projector = viewer.projectorFor(overhead.pose)
        val meshes = terrainAndEntityMeshesOf(scene.spec)
        val surface = rasterizeVisibleSurfaces(meshes, projector)
        val waterOwners = meshes.keys.withIndex().filter { isWaterEntity(it.value) }.map { it.index }.toHashSet()
        assertTrue(surface.owner.any { it in waterOwners }, "no water pixels landed where the polygon says")
    }

    @Test
    fun the_naip_pixel_under_each_crown_colors_its_canopy() {
        val wooded = woodedScene()
        val treeMeshes = wooded.spec.meshesByEntity.filterKeys(::isTreeEntity)
        assertTrue(treeMeshes.isNotEmpty(), "no tree meshes rendered")
        val canopyPalettes = treeMeshes.values.map { it.triColors.toSet() }.toSet()
        assertTrue(
            canopyPalettes.size > 1,
            "every canopy carries an identical palette — NAIP color is not reaching the trees",
        )
    }

    private fun skylineRoughnessOf(scene: PlotScene, viewpoint: Viewpoint): Double {
        val viewer = PlotViewer(scene.spec)
        val image = viewer.capture(viewpoint.pose)
        val classifier = skyClassifierOf(scene.spec, scene.daylight)
        val skyline = if (classifier == null) observedSkylineOf(image) else observedSkylineOf(image, classifier)
        var total = 0.0
        var compared = 0
        for (column in 1 until skyline.size) {
            if (skyline[column] == NOTHING_DRAWN || skyline[column - 1] == NOTHING_DRAWN) continue
            total += abs(skyline[column] - skyline[column - 1])
            compared++
        }
        return if (compared == 0) 0.0 else total / compared
    }
}
