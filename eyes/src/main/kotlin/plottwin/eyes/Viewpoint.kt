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
import plottwin.worldstate.ROAD_ENTITY_NAME
import plottwin.worldstate.isRoadEntity
import plottwin.worldstate.isTreeEntity
import plottwin.worldstate.isWaterEntity

const val EYE_HEIGHT_METERS = 1.7f
const val STANDOFF_METERS = 14.0
const val ORBIT_STEPS = 4
const val ORBIT_PITCH_RADIANS = 0.42f

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
