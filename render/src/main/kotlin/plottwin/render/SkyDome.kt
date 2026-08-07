package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dEntity
import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.pow
import kotlin.math.sqrt
import plottwin.worldstate.TerrainGrid

const val SKY_ENTITY_ID = "sky"
const val SKY_DOME_CELLS = 48
const val SKY_DOME_RADIUS_MULTIPLE = 6.0f
const val SUN_GLOW_TIGHTNESS = 24.0f

// scene3d draws heightfield entities in list order before every other mesh, so a dome expressed
// as a heightfield and listed first is the one sky the batched painter can already draw.
fun withSkyDome(spec: WalkableSceneSpec, terrain: TerrainGrid, daylight: Daylight): WalkableSceneSpec {
    val dome = skyDomeMeshOf(skyDomeRadiusOf(terrain), daylight)
    return spec.copy(
        world = spec.world.copy(entities = listOf(Scene3dEntity(id = SKY_ENTITY_ID)) + spec.world.entities),
        meshesByEntity = linkedMapOf(SKY_ENTITY_ID to dome) + spec.meshesByEntity,
    )
}

fun skyDomeRadiusOf(terrain: TerrainGrid): Float =
    (maxOf(terrain.columns, terrain.rows) * terrain.cellSize.value).toFloat() * SKY_DOME_RADIUS_MULTIPLE

fun skyDomeMeshOf(radius: Float, daylight: Daylight, cells: Int = SKY_DOME_CELLS): Scene3dMesh {
    val cellSpan = 2f * radius / cells
    val vertices = ArrayList<Float>((cells + 1) * (cells + 1) * 3)
    for (vertexZ in 0..cells) {
        for (vertexX in 0..cells) {
            val east = -radius + vertexX * cellSpan
            val north = -radius + vertexZ * cellSpan
            vertices.add(east)
            vertices.add(domeHeightAt(east, north, radius))
            vertices.add(north)
        }
    }
    val triColors = ArrayList<String>(cells * cells * 2)
    for (row in 0 until cells) {
        for (column in 0 until cells) {
            val east = -radius + (column + 0.5f) * cellSpan
            val north = -radius + (row + 0.5f) * cellSpan
            val face = hexOf(skyColorToward(east, domeHeightAt(east, north, radius), north, radius, daylight))
            triColors.add(face)
            triColors.add(face)
        }
    }
    return Scene3dMesh(vertices = vertices, triColors = triColors, gridCellsX = cells, gridCellsZ = cells)
}

private fun domeHeightAt(east: Float, north: Float, radius: Float): Float {
    val fromZenith = east * east + north * north
    if (fromZenith >= radius * radius) return 0f
    return sqrt(radius * radius - fromZenith)
}

private fun skyColorToward(east: Float, up: Float, north: Float, radius: Float, daylight: Daylight): Rgb {
    val elevation = (up / radius).coerceIn(0f, 1f)
    val base = blend(daylight.horizonTint, daylight.zenithTint, sqrt(elevation))
    val length = sqrt(east * east + up * up + north * north).coerceAtLeast(1e-6f)
    val towardSun = (east * daylight.sunDirection.east + up * daylight.sunDirection.up + north * daylight.sunDirection.north) / length
    val glow = towardSun.coerceAtLeast(0f).pow(SUN_GLOW_TIGHTNESS)
    return Rgb(
        red = (base.red + daylight.sunGlow.red * glow).coerceIn(0f, 1f),
        green = (base.green + daylight.sunGlow.green * glow).coerceIn(0f, 1f),
        blue = (base.blue + daylight.sunGlow.blue * glow).coerceIn(0f, 1f),
    )
}

private fun blend(low: Rgb, high: Rgb, towardHigh: Float): Rgb = Rgb(
    red = low.red + (high.red - low.red) * towardHigh,
    green = low.green + (high.green - low.green) * towardHigh,
    blue = low.blue + (high.blue - low.blue) * towardHigh,
)
