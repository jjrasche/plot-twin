package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dCameraState
import ai.factoredui.compose.scene3d.Scene3dEntity
import ai.factoredui.compose.scene3d.Scene3dMesh
import ai.factoredui.compose.scene3d.Scene3dWorldState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plottwin.worldstate.TerrainGrid
import plottwin.solvers.Violation
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.isTreeEntity

const val TERRAIN_ENTITY_ID = "terrain"
const val RENDER_DOWNSAMPLE_FACTOR = 4
const val SCENE_BACKGROUND = "1e222a"

@Serializable
data class WalkableSceneSpec(
    val world: Scene3dWorldState,
    @SerialName("meshes_by_entity") val meshesByEntity: Map<String, Scene3dMesh>,
)

fun projectWalkableScene(
    state: CurrentState,
    violations: List<Violation>,
    daylight: Daylight,
    terrainAlbedoTriples: FloatArray? = null,
): WalkableSceneSpec {
    val terrain = requireNotNull(state.terrain) { "cannot project a walkable scene before a base-terrain row is logged" }.grid
    val frame = sceneFrameOf(terrain)
    val meshes = LinkedHashMap<String, Scene3dMesh>()
    val shading = terrainShadingFor(state, daylight, RENDER_DOWNSAMPLE_FACTOR)
    val albedoOverride = terrainAlbedoTriples?.let { averagedAlbedoOf(it, terrain, RENDER_DOWNSAMPLE_FACTOR) }
    meshes[TERRAIN_ENTITY_ID] = terrainMeshOf(downsampleForRender(terrain, RENDER_DOWNSAMPLE_FACTOR), frame, shading, albedoOverride)
    for ((entityName, placed) in state.entities) {
        val canopyAlbedo = if (isTreeEntity(entityName)) naipAlbedoUnder(placed, terrain, terrainAlbedoTriples) else null
        meshes[entityName] = entityMeshOf(entityName, placed, terrain, frame, daylight, canopyAlbedo)
    }
    violations.forEachIndexed { rank, violation ->
        meshes[violationMarkerId(violation, rank)] = violationMarkerMeshOf(violation, terrain, frame)
    }
    return WalkableSceneSpec(
        world = Scene3dWorldState(
            entities = meshes.keys.map { Scene3dEntity(id = it) },
            camera = overviewCameraOf(terrain),
            background = SCENE_BACKGROUND,
        ),
        meshesByEntity = meshes,
    )
}

fun violationMarkerId(violation: Violation, rank: Int): String = "violation-${violation.ruleName}-$rank"

// the crown as the airplane saw it: the NAIP pixel under the crown center colors the canopy
fun naipAlbedoUnder(placed: PlacedEntity, terrain: TerrainGrid, triples: FloatArray?): Rgb? {
    if (triples == null) return null
    val centroid = GroundPoint(
        east = Meters(placed.footprint.sumOf { it.east.value } / placed.footprint.size),
        north = Meters(placed.footprint.sumOf { it.north.value } / placed.footprint.size),
    )
    val column = (centroid.east.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.columns - 1)
    val row = (centroid.north.value / terrain.cellSize.value).toInt().coerceIn(0, terrain.rows - 1)
    val cell = terrain.indexOf(column, row)
    return Rgb(triples[cell * 3], triples[cell * 3 + 1], triples[cell * 3 + 2])
}

// full-res rgb triples averaged down per channel, so a render cell carries its cells' mean color
fun averagedAlbedoOf(triples: FloatArray, terrain: TerrainGrid, downsampleFactor: Int): List<Rgb> {
    require(triples.size == terrain.cellCount * 3) { "expected ${terrain.cellCount * 3} albedo floats, got ${triples.size}" }
    val red = FloatArray(terrain.cellCount) { triples[it * 3] }
    val green = FloatArray(terrain.cellCount) { triples[it * 3 + 1] }
    val blue = FloatArray(terrain.cellCount) { triples[it * 3 + 2] }
    val coarseRed = averageDown(red, terrain.columns, terrain.rows, downsampleFactor)
    val coarseGreen = averageDown(green, terrain.columns, terrain.rows, downsampleFactor)
    val coarseBlue = averageDown(blue, terrain.columns, terrain.rows, downsampleFactor)
    return List(coarseRed.size) { Rgb(coarseRed[it], coarseGreen[it], coarseBlue[it]) }
}

fun overviewCameraOf(terrain: TerrainGrid): Scene3dCameraState {
    val plotWidth = (terrain.columns * terrain.cellSize.value).toFloat()
    val plotDepth = (terrain.rows * terrain.cellSize.value).toFloat()
    val lowest = terrain.surfaceHeights.min()
    val highest = terrain.surfaceHeights.max()
    return Scene3dCameraState(
        position = listOf(-plotWidth * 0.3f, highest + plotWidth * 0.45f, -plotDepth * 0.85f),
        target = listOf(0f, (lowest + highest) / 2f, 0f),
    )
}

private val specJson = Json { encodeDefaults = false }

fun WalkableSceneSpec.toJson(): String = specJson.encodeToString(WalkableSceneSpec.serializer(), this)

fun walkableSceneSpecFromJson(json: String): WalkableSceneSpec =
    specJson.decodeFromString(WalkableSceneSpec.serializer(), json)
