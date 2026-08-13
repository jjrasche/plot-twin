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
    val reflectanceTriples = terrainAlbedoTriples?.let { deshadowedAlbedo(it) }
    val groundTriples = reflectanceTriples?.let { forestFloorAlbedo(it, terrain, state.entities) }
    val albedoOverride = groundTriples?.let { averagedAlbedoOf(it, terrain, RENDER_DOWNSAMPLE_FACTOR) }
    val renderTerrain = downsampleForRender(terrain, RENDER_DOWNSAMPLE_FACTOR)
    val boundaryRing = state.parcelBoundary?.ring
    meshes[TERRAIN_ENTITY_ID] = if (boundaryRing == null) {
        terrainMeshOf(renderTerrain, frame, shading, albedoOverride)
    } else {
        parcelGroundMeshOf(boundaryRing, renderTerrain, frame, shading, albedoOverride)
    }
    if (boundaryRing != null) {
        meshes[PROPERTY_LINE_ENTITY_ID] = propertyLineMeshOf(boundaryRing, renderTerrain, frame)
    }
    for ((entityName, placed) in state.entities) {
        val canopyAlbedo = if (isTreeEntity(entityName)) naipAlbedoUnder(placed, terrain, reflectanceTriples) else null
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

val FOREST_FLOOR_LITTER = Rgb(0.32f, 0.27f, 0.17f)
const val FOREST_FLOOR_LITTER_SHARE = 0.55f
const val ALBEDO_LUMA_FLOOR = 0.24f

// NAIP baked the flight's lighting into its pixels; albedo is reflectance, so shadow-dark
// pixels are lifted to a luma floor (hue preserved) before our own light re-shades them.
fun deshadowedAlbedo(triples: FloatArray): FloatArray {
    val lifted = triples.copyOf()
    for (cell in 0 until triples.size / 3) {
        val red = triples[cell * 3]
        val green = triples[cell * 3 + 1]
        val blue = triples[cell * 3 + 2]
        val luma = 0.299f * red + 0.587f * green + 0.114f * blue
        if (luma >= ALBEDO_LUMA_FLOOR || luma <= 0f) continue
        val gain = ALBEDO_LUMA_FLOOR / luma
        lifted[cell * 3] = (red * gain).coerceAtMost(1f)
        lifted[cell * 3 + 1] = (green * gain).coerceAtMost(1f)
        lifted[cell * 3 + 2] = (blue * gain).coerceAtMost(1f)
    }
    return lifted
}

// Under a crown the NAIP pixel is the crown's own shadowed top, not the ground; the floor
// beneath gets a leaf-litter blend so the woods interior is not painted with its own shadow.
fun forestFloorAlbedo(triples: FloatArray, terrain: TerrainGrid, entities: Map<String, PlacedEntity>): FloatArray {
    val floored = triples.copyOf()
    for ((entityName, placed) in entities) {
        if (!isTreeEntity(entityName)) continue
        val centroidEast = placed.footprint.sumOf { it.east.value } / placed.footprint.size
        val centroidNorth = placed.footprint.sumOf { it.north.value } / placed.footprint.size
        val crownRadius = placed.footprint.maxOf {
            val de = it.east.value - centroidEast
            val dn = it.north.value - centroidNorth
            kotlin.math.sqrt(de * de + dn * dn)
        }
        val cellSize = terrain.cellSize.value
        val columnRange = (((centroidEast - crownRadius) / cellSize).toInt()).coerceAtLeast(0)..
            (((centroidEast + crownRadius) / cellSize).toInt()).coerceAtMost(terrain.columns - 1)
        val rowRange = (((centroidNorth - crownRadius) / cellSize).toInt()).coerceAtLeast(0)..
            (((centroidNorth + crownRadius) / cellSize).toInt()).coerceAtMost(terrain.rows - 1)
        for (row in rowRange) {
            for (column in columnRange) {
                val de = (column + 0.5) * cellSize - centroidEast
                val dn = (row + 0.5) * cellSize - centroidNorth
                if (de * de + dn * dn > crownRadius * crownRadius) continue
                val cell = terrain.indexOf(column, row) * 3
                floored[cell] = blendTowardLitter(triples[cell], FOREST_FLOOR_LITTER.red)
                floored[cell + 1] = blendTowardLitter(triples[cell + 1], FOREST_FLOOR_LITTER.green)
                floored[cell + 2] = blendTowardLitter(triples[cell + 2], FOREST_FLOOR_LITTER.blue)
            }
        }
    }
    return floored
}

fun blendTowardLitter(channel: Float, litter: Float): Float =
    channel * (1f - FOREST_FLOOR_LITTER_SHARE) + litter * FOREST_FLOOR_LITTER_SHARE

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

// The window opens on a corner of the plot, half a step off both its axes, standing back by a
// share of its own longest span: a 90 m square and a 31 x 242 m ribbon both open in frame.
const val OVERVIEW_STANDOFF_SHARE = 0.25f
const val OVERVIEW_HEIGHT_SHARE = 0.18f

fun overviewCameraOf(terrain: TerrainGrid): Scene3dCameraState {
    val plotWidth = (terrain.columns * terrain.cellSize.value).toFloat()
    val plotDepth = (terrain.rows * terrain.cellSize.value).toFloat()
    val longestSpan = maxOf(plotWidth, plotDepth)
    val lowest = terrain.surfaceHeights.min()
    val highest = terrain.surfaceHeights.max()
    return Scene3dCameraState(
        position = listOf(
            -(plotWidth / 2f + longestSpan * OVERVIEW_STANDOFF_SHARE),
            highest + longestSpan * OVERVIEW_HEIGHT_SHARE,
            -(plotDepth / 2f + longestSpan * OVERVIEW_STANDOFF_SHARE),
        ),
        target = listOf(0f, (lowest + highest) / 2f, 0f),
    )
}

private val specJson = Json { encodeDefaults = false }

fun WalkableSceneSpec.toJson(): String = specJson.encodeToString(WalkableSceneSpec.serializer(), this)

fun walkableSceneSpecFromJson(json: String): WalkableSceneSpec =
    specJson.decodeFromString(WalkableSceneSpec.serializer(), json)
