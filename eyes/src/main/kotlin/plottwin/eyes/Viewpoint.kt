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
const val ORBIT_PITCH_RADIANS = 0.42f
const val POSE_FRAME_FILL_SHARE = 0.90f
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
    return listOf(overheadViewpoint(terrain, frame)) + walkViewpoints + naturalViewpoints + orbitViewpoints(terrain, frame)
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

fun overheadViewpoint(terrain: TerrainGrid, frame: SceneFrame): Viewpoint = Viewpoint(
    name = "overhead",
    pose = framedPoseAt(terrain, frame, OVERHEAD_YAW_RADIANS, OVERHEAD_PITCH_RADIANS),
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

fun orbitViewpoints(terrain: TerrainGrid, frame: SceneFrame): List<Viewpoint> =
    (0 until ORBIT_STEPS).map { step ->
        val yaw = (2.0 * Math.PI * step / ORBIT_STEPS).toFloat()
        Viewpoint("orbit-${step + 1}-of-$ORBIT_STEPS", framedPoseAt(terrain, frame, yaw, ORBIT_PITCH_RADIANS), null)
    }

// A pose stated as a bearing, not a distance: the plot's own corner box decides how far back
// the eye must stand, so a 31 x 242 m ribbon fills the frame the same way a square plot does.
fun framedPoseAt(terrain: TerrainGrid, frame: SceneFrame, yawRadians: Float, pitchRadians: Float): Scene3dCameraPose {
    val target = plotCenter(terrain, frame)
    val corners = plotCornersOf(terrain, frame)
    val nearest = clearanceDistanceOf(corners, target, yawRadians, pitchRadians)
    val distance = framingDistanceOf(corners, target, yawRadians, pitchRadians, nearest)
    return poseAt(target, distance, yawRadians, pitchRadians)
}

fun plotCornersOf(terrain: TerrainGrid, frame: SceneFrame): List<Vec3> {
    val westEast = listOf(frame.sceneX(0.0), frame.sceneX(terrain.columns * terrain.cellSize.value))
    val lowHigh = listOf(terrain.surfaceHeights.min(), terrain.surfaceHeights.max())
    val southNorth = listOf(frame.sceneZ(0.0), frame.sceneZ(terrain.rows * terrain.cellSize.value))
    return westEast.flatMap { x -> lowHigh.flatMap { y -> southNorth.map { z -> Vec3(x, y, z) } } }
}

private fun poseAt(target: Vec3, distance: Float, yawRadians: Float, pitchRadians: Float): Scene3dCameraPose =
    Scene3dCameraPose(eye = orbitEyeOf(target, distance, pitchRadians, yawRadians), target = target)

// The eye stands outside the box it is looking at: half the box's own reach along the view
// axis, plus a clearance, or the camera would be buried in the ground it is framing.
private fun clearanceDistanceOf(corners: List<Vec3>, target: Vec3, yawRadians: Float, pitchRadians: Float): Float {
    val direction = orbitEyeOf(Vec3.ZERO, 1f, pitchRadians, yawRadians)
    val reach = corners.maxOf { corner ->
        kotlin.math.abs(
            (corner.x - target.x) * direction.x + (corner.y - target.y) * direction.y + (corner.z - target.z) * direction.z,
        )
    }
    return reach + POSE_CLEARANCE_METERS
}

private fun framingDistanceOf(
    corners: List<Vec3>,
    target: Vec3,
    yawRadians: Float,
    pitchRadians: Float,
    nearest: Float,
): Float {
    if (framesWholePlot(corners, poseAt(target, nearest, yawRadians, pitchRadians))) return nearest
    var tooClose = nearest
    var farEnough = CAMERA_DISTANCE_CEILING_METERS
    if (!framesWholePlot(corners, poseAt(target, farEnough, yawRadians, pitchRadians))) return farEnough
    repeat(FRAMING_SEARCH_STEPS) {
        val midpoint = (tooClose + farEnough) / 2f
        if (framesWholePlot(corners, poseAt(target, midpoint, yawRadians, pitchRadians))) farEnough = midpoint
        else tooClose = midpoint
    }
    return farEnough
}

private const val FRAMING_SEARCH_STEPS = 24

fun framesWholePlot(corners: List<Vec3>, pose: Scene3dCameraPose): Boolean {
    val projector = ScreenProjector(cameraOfPose(pose), VIEW_WIDTH, VIEW_HEIGHT)
    val marginColumns = VIEW_WIDTH * (1f - POSE_FRAME_FILL_SHARE) / 2f
    val marginRows = VIEW_HEIGHT * (1f - POSE_FRAME_FILL_SHARE) / 2f
    return corners.map(projector::project).all { projected ->
        projected.visible &&
            projected.x >= marginColumns && projected.x <= VIEW_WIDTH - marginColumns &&
            projected.y >= marginRows && projected.y <= VIEW_HEIGHT - marginRows
    }
}

private fun plotCenter(terrain: TerrainGrid, frame: SceneFrame): Vec3 {
    val lowest = terrain.surfaceHeights.min()
    val highest = terrain.surfaceHeights.max()
    return Vec3(
        frame.sceneX(terrain.columns * terrain.cellSize.value / 2.0),
        (lowest + highest) / 2f,
        frame.sceneZ(terrain.rows * terrain.cellSize.value / 2.0),
    )
}

fun footprintCentroid(footprint: List<GroundPoint>): GroundPoint = GroundPoint(
    east = Meters(footprint.sumOf { it.east.value } / footprint.size),
    north = Meters(footprint.sumOf { it.north.value } / footprint.size),
)

private fun clampToPlot(point: GroundPoint, terrain: TerrainGrid): GroundPoint = GroundPoint(
    east = Meters(point.east.value.coerceIn(0.0, terrain.columns * terrain.cellSize.value - 1e-6)),
    north = Meters(point.north.value.coerceIn(0.0, terrain.rows * terrain.cellSize.value - 1e-6)),
)
