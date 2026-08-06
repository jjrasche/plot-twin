package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dCameraState
import ai.factoredui.compose.scene3d.Scene3dEntity
import ai.factoredui.compose.scene3d.Scene3dMesh
import ai.factoredui.compose.scene3d.Scene3dWorldState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plottwin.solvers.TerrainGrid
import plottwin.solvers.Violation
import plottwin.worldstate.CurrentState

const val TERRAIN_ENTITY_ID = "terrain"
const val RENDER_DOWNSAMPLE_FACTOR = 4
const val SCENE_BACKGROUND = "1e222a"

@Serializable
data class WalkableSceneSpec(
    val world: Scene3dWorldState,
    @SerialName("meshes_by_entity") val meshesByEntity: Map<String, Scene3dMesh>,
)

fun projectWalkableScene(state: CurrentState, terrain: TerrainGrid, violations: List<Violation>): WalkableSceneSpec {
    val frame = sceneFrameOf(terrain)
    val meshes = LinkedHashMap<String, Scene3dMesh>()
    meshes[TERRAIN_ENTITY_ID] = terrainMeshOf(downsampleForRender(terrain, RENDER_DOWNSAMPLE_FACTOR), frame)
    for ((entityName, placed) in state.entities) {
        meshes[entityName] = entityMeshOf(entityName, placed, terrain, frame)
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
