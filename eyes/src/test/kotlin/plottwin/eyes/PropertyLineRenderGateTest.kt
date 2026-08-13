package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.capture.RealParcelFixture
import plottwin.capture.parcelBoundaryRowOf
import plottwin.render.PROPERTY_LINE_ENTITY_ID
import plottwin.render.SKY_ENTITY_ID
import plottwin.render.SURROUND_ENTITY_ID
import plottwin.render.PROPERTY_LINE_WIDTH_METERS
import plottwin.render.TERRAIN_ENTITY_ID
import plottwin.render.groundHeightAt
import plottwin.render.sceneFrameOf
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.isInsidePolygon
import plottwin.worldstate.isTreeEntity

const val SILHOUETTE_AREA_TOLERANCE = 0.02
const val BOUNDARY_EXTENT_TOLERANCE_METERS = 0.05
const val OVERHEAD_ACROSS_FRAME_FLOOR = 0.7
// the painter antialiases the edge the mask rasterizer draws hard, so judgements about what lies
// under the ground start two pixels below it
const val PAINTER_EDGE_PIXELS = 2

fun lumaSpanOf(colors: List<Int>): String {
    val luma = colors.map { 0.299 * ((it shr 16) and 0xFF) + 0.587 * ((it shr 8) and 0xFF) + 0.114 * (it and 0xFF) }
    return "%.0f..%.0f".format(luma.min(), luma.max())
}

fun chebyshevBetween(first: Int, second: Int): Int = maxOf(
    kotlin.math.abs(((first shr 16) and 0xFF) - ((second shr 16) and 0xFF)),
    kotlin.math.abs(((first shr 8) and 0xFF) - ((second shr 8) and 0xFF)),
    kotlin.math.abs((first and 0xFF) - (second and 0xFF)),
)

class PropertyLineRenderGateTest {

    private fun parcelScene() =
        realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features(), RealParcelFixture.boundary())

    @Test
    fun the_rendered_ground_silhouette_holds_the_same_area_share_as_the_masks_true_cells() {
        val scene = parcelScene()
        val mask = requireNotNull(scene.state.parcelMask) { "the boundary row did not reach the projection" }
        val viewer = PlotViewer(scene.spec)
        val overhead = plotViewpoints(scene.state).first { it.name == "overhead" }
        val projector = viewer.projectorFor(overhead.pose)
        // the crowns standing on the ground project past the ground's own outline, so the
        // silhouette question is asked of the ground mesh alone
        val groundOnly = scene.spec.meshesByEntity.filterKeys { it == TERRAIN_ENTITY_ID }
        val surface = rasterizeVisibleSurfaces(groundOnly, projector)
        val terrain = scene.state.terrain!!.grid
        val boundingBoxPixels = screenAreaOf(boundingBoxCornersOf(terrain), terrain, projector)
        val groundPixels = surface.owner.count { it != NO_SURFACE }
        val drawnShare = groundPixels / boundingBoxPixels
        val maskShare = mask.insideCellCount.toDouble() / (mask.columns * mask.rows)
        println(
            "[line] overhead ground silhouette share %.4f of the bounding box vs mask true-share %.4f (%d px of %.0f px)"
                .format(drawnShare, maskShare, groundPixels, boundingBoxPixels),
        )
        assertTrue(
            abs(drawnShare - maskShare) <= SILHOUETTE_AREA_TOLERANCE,
            "the drawn silhouette holds $drawnShare of the bounding box, the mask says $maskShare",
        )
    }

    @Test
    fun the_drawn_property_line_spans_exactly_the_boundary_rows_own_extent() {
        val scene = parcelScene()
        val ring = parcelBoundaryRowOf(RealParcelFixture.boundary()).ring
        val terrain = scene.state.terrain!!.grid
        val frame = sceneFrameOf(terrain)
        val mesh = requireNotNull(scene.spec.meshesByEntity[PROPERTY_LINE_ENTITY_ID]) { "no property-line mesh was drawn" }
        val drawnEast = (0 until mesh.vertices.size / 3).map { mesh.vertices[it * 3] }
        val drawnNorth = (0 until mesh.vertices.size / 3).map { mesh.vertices[it * 3 + 2] }
        val rowEast = ring.map { frame.sceneX(it.east.value) }
        val rowNorth = ring.map { frame.sceneZ(it.north.value) }
        // the kerb steps inward off the line, so it may sit up to one kerb width inside the
        // row's extent and never a millimetre outside it
        val slack = PROPERTY_LINE_WIDTH_METERS + BOUNDARY_EXTENT_TOLERANCE_METERS
        println(
            "[line] drawn east %.3f..%.3f vs row %.3f..%.3f, drawn north %.3f..%.3f vs row %.3f..%.3f m"
                .format(
                    drawnEast.min(), drawnEast.max(), rowEast.min(), rowEast.max(),
                    drawnNorth.min(), drawnNorth.max(), rowNorth.min(), rowNorth.max(),
                ),
        )
        assertTrue(drawnEast.min() >= rowEast.min() - BOUNDARY_EXTENT_TOLERANCE_METERS, "the kerb reaches west of the row")
        assertTrue(drawnEast.max() <= rowEast.max() + BOUNDARY_EXTENT_TOLERANCE_METERS, "the kerb reaches east of the row")
        assertTrue(drawnNorth.min() >= rowNorth.min() - BOUNDARY_EXTENT_TOLERANCE_METERS, "the kerb reaches south of the row")
        assertTrue(drawnNorth.max() <= rowNorth.max() + BOUNDARY_EXTENT_TOLERANCE_METERS, "the kerb reaches north of the row")
        assertTrue(drawnEast.min() <= rowEast.min() + slack, "the kerb stops short of the row's west edge")
        assertTrue(drawnEast.max() >= rowEast.max() - slack, "the kerb stops short of the row's east edge")
        assertTrue(drawnNorth.min() <= rowNorth.min() + slack, "the kerb stops short of the row's south edge")
        assertTrue(drawnNorth.max() >= rowNorth.max() - slack, "the kerb stops short of the row's north edge")
    }

    @Test
    fun the_overhead_frame_contains_every_vertex_of_the_property_line() {
        val scene = parcelScene()
        val ring = parcelBoundaryRowOf(RealParcelFixture.boundary()).ring
        val terrain = scene.state.terrain!!.grid
        val viewer = PlotViewer(scene.spec)
        val overhead = plotViewpoints(scene.state).first { it.name == "overhead" }
        val projector = viewer.projectorFor(overhead.pose)
        val projected = ring.map { vertex -> projector.project(scenePointOf(vertex, terrain)) }
        val acrossFrame = (projected.maxOf { it.x } - projected.minOf { it.x }) / viewer.width
        println(
            "[line] overhead holds the ring across %.3f of the frame, columns %.1f..%.1f, rows %.1f..%.1f"
                .format(
                    acrossFrame,
                    projected.minOf { it.x }, projected.maxOf { it.x },
                    projected.minOf { it.y }, projected.maxOf { it.y },
                ),
        )
        assertTrue(projected.all { it.visible }, "a boundary vertex does not project into the overhead frame")
        assertTrue(
            projected.all { it.x >= 0f && it.x <= viewer.width && it.y >= 0f && it.y <= viewer.height },
            "the overhead frame crops the property line",
        )
        assertTrue(
            acrossFrame >= OVERHEAD_ACROSS_FRAME_FLOOR,
            "the overhead frame wastes the parcel: it spans only $acrossFrame of the width",
        )
    }

    @Test
    fun every_tree_the_render_draws_stands_inside_the_property_line() {
        val scene = parcelScene()
        val ring = parcelBoundaryRowOf(RealParcelFixture.boundary()).ring
        val outsideTrees = scene.state.entities
            .filterKeys(::isTreeEntity)
            .filterValues { placed -> !isInsidePolygon(footprintCentroid(placed.footprint), ring) }
        println("[line] ${scene.state.entities.keys.count(::isTreeEntity)} trees drawn, ${outsideTrees.size} outside the line")
        assertTrue(outsideTrees.isEmpty(), "trees stand outside the property line: ${outsideTrees.keys}")
    }

    @Test
    fun every_drawn_ground_cell_centre_lies_inside_the_property_line() {
        val scene = parcelScene()
        val ring = parcelBoundaryRowOf(RealParcelFixture.boundary()).ring
        val terrain = scene.state.terrain!!.grid
        val mesh = requireNotNull(scene.spec.meshesByEntity[TERRAIN_ENTITY_ID]) { "no ground mesh was drawn" }
        val perRow = mesh.gridCellsX + 1
        var outside = 0
        for (row in 0 until mesh.gridCellsZ) {
            for (column in 0 until mesh.gridCellsX) {
                val centre = cellCentreOf(mesh.vertices, row * perRow + column, perRow, terrain)
                if (!isInsidePolygon(centre, ring)) outside++
            }
        }
        println("[line] ${mesh.gridCellsX * mesh.gridCellsZ} drawn ground cells, $outside outside the line")
        assertTrue(outside == 0, "$outside drawn ground cells fall outside the property line")
    }

    // The surround only reads as "not mine" if no pixel of it can be confused with the parcel.
    // Measured on the palettes rather than on one frame, so the margin is a property of the
    // render and not of a camera angle.
    @Test
    fun the_surrounds_palette_stays_clear_of_every_colour_the_parcel_draws() {
        val scene = parcelScene()
        val surround = requireNotNull(scene.spec.meshesByEntity[SURROUND_ENTITY_ID]) { "no surround was drawn" }
        val parcelColors = (scene.spec.meshesByEntity - SURROUND_ENTITY_ID - SKY_ENTITY_ID)
            .values
            .flatMap { it.triColors }
            .toSet()
            .map(::argbOfHex)
        val surroundColors = surround.triColors.toSet().map(::argbOfHex)
        val closestPair = surroundColors
            .flatMap { backdrop -> parcelColors.map { parcel -> Triple(chebyshevBetween(backdrop, parcel), backdrop, parcel) } }
            .minBy { it.first }
        val closest = closestPair.first
        println(
            "[line] surround palette ${surroundColors.size} colours, parcel palette ${parcelColors.size}, closest pair $closest apart (tolerance $SKY_MATCH_TOLERANCE): surround %06x vs parcel %06x"
                .format(closestPair.second and 0xFFFFFF, closestPair.third and 0xFFFFFF),
        )
        println("[line] surround luma ${lumaSpanOf(surroundColors)}, parcel luma ${lumaSpanOf(parcelColors)}")
        assertTrue(
            closest > SKY_MATCH_TOLERANCE,
            "a surround colour sits $closest from a colour the parcel draws, inside the classifier's $SKY_MATCH_TOLERANCE tolerance",
        )
    }

    // The parcel may not hover: below the ground's own silhouette there must be neighbour ground,
    // never open sky, or the frame reads as a cut-out prop on a table.
    @Test
    fun no_pose_sees_sky_beneath_the_parcels_own_ground() {
        val scene = parcelScene()
        val viewer = PlotViewer(scene.spec)
        val withBackdrop = scene.spec.meshesByEntity - SKY_ENTITY_ID
        val groundOwner = ownerIndexOf(withBackdrop, TERRAIN_ENTITY_ID)
        for (viewpoint in plotViewpoints(scene.state)) {
            val surface = rasterizeVisibleSurfaces(withBackdrop, viewer.projectorFor(viewpoint.pose))
            var holes = 0
            for (column in 0 until surface.width) {
                val lowestGroundRow = (surface.height - 1 downTo 0)
                    .firstOrNull { row -> surface.owner[row * surface.width + column] == groundOwner }
                    ?: continue
                for (row in lowestGroundRow + PAINTER_EDGE_PIXELS until surface.height) {
                    if (surface.owner[row * surface.width + column] == NO_SURFACE) holes++
                }
            }
            println("[line] %-22s %d pixels of open sky beneath the parcel's ground".format(viewpoint.name, holes))
            assertTrue(holes == 0, "${viewpoint.name} sees $holes pixels of sky under the parcel")
        }
    }

    // Omitting the neighbours' land leaves the parcel standing in void wherever their ground
    // would have carried the horizon. This measures how much of each frame that is, so the
    // omission ruling can be re-read against a number instead of an impression.
    @Test
    fun the_void_the_omission_leaves_under_each_frame_is_measured() {
        val scene = parcelScene()
        val viewer = PlotViewer(scene.spec)
        val plotMeshes = terrainAndEntityMeshesOf(scene.spec)
        val withBackdrop = scene.spec.meshesByEntity - SKY_ENTITY_ID
        for (viewpoint in plotViewpoints(scene.state)) {
            val projector = viewer.projectorFor(viewpoint.pose)
            val skyline = predictedSkylineOf(rasterizeVisibleSurfaces(plotMeshes, projector))
            val surface = rasterizeVisibleSurfaces(withBackdrop, projector)
            var belowSkyline = 0
            var voidBelowSkyline = 0
            for (column in 0 until surface.width) {
                if (skyline[column] == NOTHING_DRAWN) continue
                for (row in skyline[column] until surface.height) {
                    belowSkyline++
                    if (surface.owner[row * surface.width + column] == NO_SURFACE) voidBelowSkyline++
                }
            }
            val voidShare = if (belowSkyline == 0) 0.0 else voidBelowSkyline.toDouble() / belowSkyline
            println(
                "[line] %-22s void under the skyline %.3f (%d of %d pixels below the drawn silhouette)"
                    .format(viewpoint.name, voidShare, voidBelowSkyline, belowSkyline),
            )
        }
    }

    @Test
    fun every_pose_frames_the_plot_without_hitting_the_cameras_own_distance_ceiling() {
        val scene = parcelScene()
        val terrain = scene.state.terrain!!.grid
        val plot = plotBoxOf(scene.state, terrain, sceneFrameOf(terrain))
        for (viewpoint in plotViewpoints(scene.state)) {
            val eye = viewpoint.pose.eye
            val target = viewpoint.pose.target
            val distance = sqrt(
                (eye.x - target.x) * (eye.x - target.x) +
                    (eye.y - target.y) * (eye.y - target.y) +
                    (eye.z - target.z) * (eye.z - target.z),
            )
            val share = framedShareOf(plot.groundCorners, viewpoint.pose)
            println(
                "[line] %-22s eye %.1f %.1f %.1f distance %.1f m, ground fills %.3f across %.3f down"
                    .format(viewpoint.name, eye.x, eye.y, eye.z, distance, share.acrossFrame, share.downFrame),
            )
            assertTrue(
                distance < CAMERA_DISTANCE_CEILING_METERS,
                "${viewpoint.name} asks for $distance m, past the camera's own $CAMERA_DISTANCE_CEILING_METERS m clamp",
            )
        }
    }

    // the mesh carries scene coordinates, so the frame's own centre comes back off to read
    // the plot-local metres the ring is stated in
    private fun cellCentreOf(vertices: List<Float>, corner: Int, perRow: Int, terrain: TerrainGrid): GroundPoint {
        val corners = listOf(corner, corner + 1, corner + perRow, corner + perRow + 1)
        return GroundPoint(
            east = Meters(
                corners.sumOf { vertices[it * 3].toDouble() } / corners.size +
                    terrain.columns * terrain.cellSize.value / 2.0,
            ),
            north = Meters(
                corners.sumOf { vertices[it * 3 + 2].toDouble() } / corners.size +
                    terrain.rows * terrain.cellSize.value / 2.0,
            ),
        )
    }

    private fun boundingBoxCornersOf(terrain: TerrainGrid): List<GroundPoint> {
        val east = terrain.columns * terrain.cellSize.value
        val north = terrain.rows * terrain.cellSize.value
        return listOf(
            GroundPoint(Meters(0.0), Meters(0.0)),
            GroundPoint(Meters(east), Meters(0.0)),
            GroundPoint(Meters(east), Meters(north)),
            GroundPoint(Meters(0.0), Meters(north)),
        )
    }

    private fun scenePointOf(vertex: GroundPoint, terrain: TerrainGrid): Vec3 {
        val frame = sceneFrameOf(terrain)
        return Vec3(frame.sceneX(vertex.east.value), groundHeightAt(terrain, vertex), frame.sceneZ(vertex.north.value))
    }

    private fun screenAreaOf(corners: List<GroundPoint>, terrain: TerrainGrid, projector: ScreenProjector): Double {
        val projected = corners.map { projector.project(scenePointOf(it, terrain)) }
        val twiceArea = projected.indices.sumOf { corner ->
            val next = projected[(corner + 1) % projected.size]
            (projected[corner].x * next.y - next.x * projected[corner].y).toDouble()
        }
        return abs(twiceArea) / 2.0
    }
}
