package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import ai.factoredui.compose.scene3d.Scene3dCameraPose
import ai.factoredui.compose.scene3d.orbitPoseSequence
import plottwin.render.SceneFrame
import plottwin.render.groundHeightAt
import plottwin.render.sceneFrameOf
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.TerrainGrid

const val EYE_HEIGHT_METERS = 1.7f
const val STANDOFF_METERS = 14.0
const val ORBIT_STEPS = 4
const val ORBIT_PITCH_RADIANS = 0.42f

data class Viewpoint(val name: String, val pose: Scene3dCameraPose, val subject: String?)

fun plotViewpoints(state: CurrentState): List<Viewpoint> {
    val terrain = requireNotNull(state.terrain) { "viewpoints need a base-terrain row" }.grid
    val frame = sceneFrameOf(terrain)
    val walkViewpoints = state.entities
        .filter { (_, placed) -> placed.height.value > 0.0 }
        .map { (name, placed) -> walkHeightViewpoint(name, placed, terrain, frame) }
    return listOf(overheadViewpoint(terrain, frame)) + walkViewpoints + orbitViewpoints(terrain, frame)
}

fun overheadViewpoint(terrain: TerrainGrid, frame: SceneFrame): Viewpoint {
    val plotSpan = plotSpanMeters(terrain)
    val target = plotCenter(terrain, frame)
    return Viewpoint(
        name = "overhead",
        pose = Scene3dCameraPose(eye = Vec3(target.x, target.y + plotSpan * 0.9f, target.z), target = target),
        subject = null,
    )
}

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

fun orbitViewpoints(terrain: TerrainGrid, frame: SceneFrame): List<Viewpoint> {
    val target = plotCenter(terrain, frame)
    return orbitPoseSequence(
        target = target,
        distance = plotSpanMeters(terrain) * 1.15f,
        pitchRadians = ORBIT_PITCH_RADIANS,
        steps = ORBIT_STEPS,
    ).mapIndexed { step, pose -> Viewpoint("orbit-${step + 1}-of-$ORBIT_STEPS", pose, null) }
}

private fun plotSpanMeters(terrain: TerrainGrid): Float =
    (maxOf(terrain.columns, terrain.rows) * terrain.cellSize.value).toFloat()

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
