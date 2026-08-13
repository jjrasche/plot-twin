package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import ai.factoredui.compose.scene3d.cameraOfPose
import ai.factoredui.compose.scene3d.orbitEyeOf
import plottwin.render.SceneFrame
import plottwin.render.groundHeightAt
import plottwin.render.sceneFrameOf
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.isRoadEntity
import plottwin.worldstate.isTreeEntity
import plottwin.worldstate.isWaterEntity

const val EYE_HEIGHT_METERS = 1.7f
const val STANDOFF_METERS = 14.0
const val ORBIT_STEPS = 4
const val ORBIT_PITCH_RADIANS = 0.20f
// An orbit frame looking straight down a plot's longer axis wastes itself: on a 31 x 242 m
// ribbon the land fills at most 29% of the frame's width from there, at any distance. A sweep
// that would produce such a frame is turned half a step off the axes; a plot with no such
// frame keeps its square-on sweep.
const val ORBIT_AXIS_FILL_SHARE = 0.9f
// the shape shot keeps a margin so no crown is clipped; the orbits go edge to edge, because a
// ribbon that must also hold its own length has no frame left to spare
const val OVERHEAD_FRAME_SHARE = 0.90f
const val ORBIT_FRAME_SHARE = 1.0f
const val POSE_CLEARANCE_METERS = 8.0f
// scene3d's orbit camera clamps its distance here, so a pose that asks for more is silently
// re-framed; the framing gate reads this ceiling and fails rather than let the clamp lie.
const val CAMERA_DISTANCE_CEILING_METERS = 300f
// looking all but straight down, with the eye's last sliver of offset laid to the east so the
// hard-coded world up-vector puts the parcel's long axis across the frame's width
const val OVERHEAD_PITCH_RADIANS = 1.5608f
const val OVERHEAD_YAW_RADIANS = 1.5708f

data class Viewpoint(val name: String, val pose: Scene3dCameraPose, val subject: String?)

fun plotViewpoints(state: CurrentState): List<Viewpoint> {
    val terrain = requireNotNull(state.terrain) { "viewpoints need a base-terrain row" }.grid
    val frame = sceneFrameOf(terrain)
    val walkViewpoints = state.entities
        .filter { (name, placed) -> placed.height.value > 0.0 && isNamedSubject(name) }
        .map { (name, placed) -> walkHeightViewpoint(name, placed, terrain, frame) }
    val naturalViewpoints = listOfNotNull(woodsViewpoint(state, terrain, frame), roadViewpoint(state, terrain, frame))
    val plot = plotBoxOf(state, terrain, frame)
    return listOf(overheadViewpoint(plot)) + walkViewpoints + naturalViewpoints + orbitViewpoints(plot)
}

// a wood is one place to stand in, not a walk viewpoint per crown
private fun isNamedSubject(entityName: String): Boolean =
    !isTreeEntity(entityName) && !isWaterEntity(entityName) && !isRoadEntity(entityName)

fun woodsViewpoint(state: CurrentState, terrain: TerrainGrid, frame: SceneFrame): Viewpoint? {
    val trees = state.entities.filterKeys(::isTreeEntity)
    if (trees.isEmpty()) return null
    val centroids = trees.mapValues { (_, placed) -> footprintCentroid(placed.footprint) }
    val standing = GroundPoint(
        east = Meters(centroids.values.sumOf { it.east.value } / centroids.size),
        north = Meters(centroids.values.sumOf { it.north.value } / centroids.size),
    )
    val lookAt = trees.entries
        .filter { (name, _) -> distanceMeters(centroids.getValue(name), standing) > 4.0 }
        .maxByOrNull { (_, placed) -> placed.height.value }
        ?: return null
    val lookAtCentroid = centroids.getValue(lookAt.key)
    val eye = Vec3(
        frame.sceneX(standing.east.value),
        groundHeightAt(terrain, standing) + EYE_HEIGHT_METERS,
        frame.sceneZ(standing.north.value),
    )
    val target = Vec3(
        frame.sceneX(lookAtCentroid.east.value),
        groundHeightAt(terrain, lookAtCentroid) + lookAt.value.height.value.toFloat() * 0.5f,
        frame.sceneZ(lookAtCentroid.north.value),
    )
    return Viewpoint("walk-height-in-woods", Scene3dCameraPose(eye = eye, target = target), subject = null)
}

fun roadViewpoint(state: CurrentState, terrain: TerrainGrid, frame: SceneFrame): Viewpoint? {
    val road = state.entities[ROAD_ENTITY_NAME] ?: return null
    val centerNorth = road.footprint.sumOf { it.north.value } / road.footprint.size
    val westmost = road.footprint.minOf { it.east.value }
    val eastmost = road.footprint.maxOf { it.east.value }
    val standing = GroundPoint(Meters(westmost + (eastmost - westmost) * 0.15), Meters(centerNorth))
    val ahead = GroundPoint(Meters(westmost + (eastmost - westmost) * 0.85), Meters(centerNorth))
    val eye = Vec3(
        frame.sceneX(standing.east.value),
        groundHeightAt(terrain, standing) + EYE_HEIGHT_METERS,
        frame.sceneZ(standing.north.value),
    )
    val target = Vec3(
        frame.sceneX(ahead.east.value),
        groundHeightAt(terrain, ahead) + EYE_HEIGHT_METERS * 0.8f,
        frame.sceneZ(ahead.north.value),
    )
    return Viewpoint("on-road", Scene3dCameraPose(eye = eye, target = target), subject = null)
}

private fun distanceMeters(first: GroundPoint, second: GroundPoint): Double =
    kotlin.math.hypot(first.east.value - second.east.value, first.north.value - second.north.value)

// How far in from the near end the owner stands, as a share of the plot's own length: inside the
// line, with the whole length still ahead of him.
const val DOWN_THE_LENGTH_STANDOFF_SHARE = 0.03

// The view an owner asks for by walking to one end of his land and looking down it. Level, at
// eye height, from inside the line. The look is turned a stated angle off the plot's long axis
// because a bearing exactly along it puts the far end on the vanishing point and leaves the land
// a thin wedge up the middle of the frame; off the axis, both side lines stay in view and the
// ribbon runs across the frame instead of into it.
fun downTheLengthViewpoint(
    name: String,
    terrain: TerrainGrid,
    frame: SceneFrame,
    offAxisDegrees: Double,
): Viewpoint {
    val east = terrain.columns * terrain.cellSize.value
    val north = terrain.rows * terrain.cellSize.value
    val length = maxOf(east, north)
    val alongEast = if (east >= north) 1.0 else 0.0
    val alongNorth = if (east >= north) 0.0 else 1.0
    val standing = GroundPoint(
        east = Meters(if (east >= north) length * DOWN_THE_LENGTH_STANDOFF_SHARE else east / 2.0),
        north = Meters(if (east >= north) north / 2.0 else length * DOWN_THE_LENGTH_STANDOFF_SHARE),
    )
    val turn = Math.toRadians(offAxisDegrees)
    val lookEast = alongEast * kotlin.math.cos(turn) - alongNorth * kotlin.math.sin(turn)
    val lookNorth = alongNorth * kotlin.math.cos(turn) + alongEast * kotlin.math.sin(turn)
    val eyeHeight = groundHeightAt(terrain, standing) + EYE_HEIGHT_METERS
    val eye = Vec3(frame.sceneX(standing.east.value), eyeHeight, frame.sceneZ(standing.north.value))
    // level: the target sits at the eye's own height, so nothing about the pose but its bearing
    // changes between two off-axis angles
    val target = Vec3(
        frame.sceneX(standing.east.value + lookEast * length),
        eyeHeight,
        frame.sceneZ(standing.north.value + lookNorth * length),
    )
    return Viewpoint(name, Scene3dCameraPose(eye = eye, target = target), subject = null)
}

// The one pose that owes the whole parcel, crowns included, with a margin around it.
fun overheadViewpoint(plot: PlotBox): Viewpoint = Viewpoint(
    name = "overhead",
    pose = nearestPoseHolding(plot, plot.corners, OVERHEAD_FRAME_SHARE, OVERHEAD_YAW_RADIANS, OVERHEAD_PITCH_RADIANS),
    subject = null,
)

fun walkHeightViewpoint(entityName: String, placed: PlacedEntity, terrain: TerrainGrid, frame: SceneFrame): Viewpoint {
    val centroid = footprintCentroid(placed.footprint)
    val standing = GroundPoint(centroid.east, Meters(centroid.north.value - STANDOFF_METERS))
    val eye = Vec3(
        frame.sceneX(standing.east.value),
        groundHeightAt(terrain, clampToPlot(standing, terrain)) + EYE_HEIGHT_METERS,
        frame.sceneZ(standing.north.value),
    )
    val target = Vec3(
        frame.sceneX(centroid.east.value),
        groundHeightAt(terrain, centroid) + placed.height.value.toFloat() / 2f,
        frame.sceneZ(centroid.north.value),
    )
    return Viewpoint("walk-height-at-$entityName", Scene3dCameraPose(eye = eye, target = target), entityName)
}

// The orbit owes the land, not the crowns: it comes in until the ground itself reaches the
// frame's edges, so the broadside views hold the ribbon end to end and the end-on views look
// down its whole length with the near ground still in frame.
fun orbitViewpoints(plot: PlotBox): List<Viewpoint> {
    val startYaw = orbitStartYawOf(plot)
    return (0 until ORBIT_STEPS).map { step ->
        Viewpoint("orbit-${step + 1}-of-$ORBIT_STEPS", orbitPoseAt(plot, startYaw + orbitYawOfStep(step)), null)
    }
}

fun orbitStartYawOf(plot: PlotBox): Float {
    val squareOnFill = (0 until ORBIT_STEPS).map { step ->
        framedShareOf(plot.groundCorners, orbitPoseAt(plot, orbitYawOfStep(step))).acrossFrame
    }
    return if (squareOnFill.min() >= ORBIT_AXIS_FILL_SHARE) 0f else halfOrbitStepYaw()
}

private fun orbitPoseAt(plot: PlotBox, yawRadians: Float): Scene3dCameraPose =
    nearestPoseHolding(plot, plot.groundCorners, ORBIT_FRAME_SHARE, yawRadians, ORBIT_PITCH_RADIANS)

private fun orbitYawOfStep(step: Int): Float = (2.0 * Math.PI * step / ORBIT_STEPS).toFloat()

private fun halfOrbitStepYaw(): Float = (Math.PI / ORBIT_STEPS).toFloat()

// A pose stated as a bearing, not a distance: the plot's own corner box decides how far back
// the eye must stand, so a 31 x 242 m ribbon frames the same way a square plot does. Two corner
// sets, because holding the whole plot must not crop a crown while an orbit answers to the land.
data class PlotBox(val target: Vec3, val corners: List<Vec3>, val groundCorners: List<Vec3>)

fun plotBoxOf(state: CurrentState, terrain: TerrainGrid, frame: SceneFrame): PlotBox {
    val lowest = terrain.surfaceHeights.min()
    val highest = highestDrawnHeightOf(state, terrain)
    val westEast = listOf(frame.sceneX(0.0), frame.sceneX(terrain.columns * terrain.cellSize.value))
    val southNorth = listOf(frame.sceneZ(0.0), frame.sceneZ(terrain.rows * terrain.cellSize.value))
    return PlotBox(
        target = Vec3(
            frame.sceneX(terrain.columns * terrain.cellSize.value / 2.0),
            (lowest + highest) / 2f,
            frame.sceneZ(terrain.rows * terrain.cellSize.value / 2.0),
        ),
        corners = westEast.flatMap { x -> listOf(lowest, highest).flatMap { y -> southNorth.map { z -> Vec3(x, y, z) } } },
        groundCorners = westEast.flatMap { x -> southNorth.map { z -> Vec3(x, lowest, z) } },
    )
}

// Framing that stops at the ground crops every crown, so the box rises to the tallest thing drawn.
private fun highestDrawnHeightOf(state: CurrentState, terrain: TerrainGrid): Float {
    val canopyTops = state.entities.values.map { placed ->
        groundHeightAt(terrain, footprintCentroid(placed.footprint)) + placed.height.value.toFloat()
    }
    return (canopyTops + terrain.surfaceHeights.max()).max()
}

// One framing rule, asked twice: how near can the eye stand and still hold these corners
// inside this much of the frame.
fun nearestPoseHolding(
    plot: PlotBox,
    corners: List<Vec3>,
    frameShare: Float,
    yawRadians: Float,
    pitchRadians: Float,
): Scene3dCameraPose {
    val nearest = clearanceDistanceOf(plot, yawRadians, pitchRadians)
    val distance = solveDistance(nearest) { candidate ->
        holdsWithin(corners, poseAt(plot, candidate, yawRadians, pitchRadians), frameShare)
    }
    return poseAt(plot, distance, yawRadians, pitchRadians)
}

private fun poseAt(plot: PlotBox, distance: Float, yawRadians: Float, pitchRadians: Float): Scene3dCameraPose =
    Scene3dCameraPose(eye = orbitEyeOf(plot.target, distance, pitchRadians, yawRadians), target = plot.target)

// The eye stands outside the box it is looking at: half the box's own reach along the view
// axis, plus a clearance, or the camera would be buried in the ground it is framing.
private fun clearanceDistanceOf(plot: PlotBox, yawRadians: Float, pitchRadians: Float): Float {
    val direction = orbitEyeOf(Vec3.ZERO, 1f, pitchRadians, yawRadians)
    val reach = plot.corners.maxOf { corner ->
        kotlin.math.abs(
            (corner.x - plot.target.x) * direction.x +
                (corner.y - plot.target.y) * direction.y +
                (corner.z - plot.target.z) * direction.z,
        )
    }
    return reach + POSE_CLEARANCE_METERS
}

// The projected extent shrinks with distance, so holding is monotone and the nearest distance
// that holds is one bisection.
private fun solveDistance(nearest: Float, holdsAt: (Float) -> Boolean): Float {
    if (holdsAt(nearest)) return nearest
    if (!holdsAt(CAMERA_DISTANCE_CEILING_METERS)) return CAMERA_DISTANCE_CEILING_METERS
    var tooClose = nearest
    var farEnough = CAMERA_DISTANCE_CEILING_METERS
    while (farEnough - tooClose > FRAMING_SEARCH_TOLERANCE_METERS) {
        val midpoint = (tooClose + farEnough) / 2f
        if (holdsAt(midpoint)) farEnough = midpoint else tooClose = midpoint
    }
    return farEnough
}

private const val FRAMING_SEARCH_TOLERANCE_METERS = 0.25f

data class FramedShare(val acrossFrame: Float, val downFrame: Float)

fun framedShareOf(corners: List<Vec3>, pose: Scene3dCameraPose): FramedShare {
    val projected = projectedCornersOf(corners, pose)
    if (projected.any { !it.visible }) return FramedShare(OVERFLOWING_SHARE, OVERFLOWING_SHARE)
    return FramedShare(
        acrossFrame = (projected.maxOf { it.x } - projected.minOf { it.x }) / VIEW_WIDTH,
        downFrame = (projected.maxOf { it.y } - projected.minOf { it.y }) / VIEW_HEIGHT,
    )
}

fun holdsWithin(corners: List<Vec3>, pose: Scene3dCameraPose, frameShare: Float): Boolean {
    val projected = projectedCornersOf(corners, pose)
    if (projected.any { !it.visible }) return false
    val marginColumns = VIEW_WIDTH * (1f - frameShare) / 2f
    val marginRows = VIEW_HEIGHT * (1f - frameShare) / 2f
    return projected.all {
        it.x >= marginColumns && it.x <= VIEW_WIDTH - marginColumns &&
            it.y >= marginRows && it.y <= VIEW_HEIGHT - marginRows
    }
}

private fun projectedCornersOf(corners: List<Vec3>, pose: Scene3dCameraPose) =
    ScreenProjector(cameraOfPose(pose), VIEW_WIDTH, VIEW_HEIGHT).let { projector -> corners.map(projector::project) }

private const val OVERFLOWING_SHARE = 9f

fun footprintCentroid(footprint: List<GroundPoint>): GroundPoint = GroundPoint(
    east = Meters(footprint.sumOf { it.east.value } / footprint.size),
    north = Meters(footprint.sumOf { it.north.value } / footprint.size),
)

private fun clampToPlot(point: GroundPoint, terrain: TerrainGrid): GroundPoint = GroundPoint(
    east = Meters(point.east.value.coerceIn(0.0, terrain.columns * terrain.cellSize.value - 1e-6)),
    north = Meters(point.north.value.coerceIn(0.0, terrain.rows * terrain.cellSize.value - 1e-6)),
)
