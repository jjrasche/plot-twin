package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dEntity
import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import plottwin.worldstate.TerrainGrid

const val SKY_ENTITY_ID = "sky"
const val SKY_DOME_RING_CELLS = 144
const val SKY_DOME_SPOKE_CELLS = 288
const val SKY_SKIRT_RING_CELLS = 16
const val SKY_SKIRT_DROP_SHARE = 0.25f
const val SKY_DOME_RADIUS_MULTIPLE = 6.0f
const val SUN_GLOW_TIGHTNESS = 8.0f

// scene3d draws heightfield entities in list order before every other mesh, so a dome expressed
// as a heightfield and listed first is the one sky the batched painter can already draw.
fun withSkyDome(spec: WalkableSceneSpec, terrain: TerrainGrid, daylight: Daylight): WalkableSceneSpec {
    val dome = skyDomeMeshOf(skyDomeRadiusOf(terrain), daylight, groundDatumOf(terrain))
    return spec.copy(
        world = spec.world.copy(
            entities = listOf(Scene3dEntity(id = SKY_ENTITY_ID)) + spec.world.entities,
            background = hexOf(daylight.horizonTint),
        ),
        meshesByEntity = linkedMapOf(SKY_ENTITY_ID to dome) + spec.meshesByEntity,
    )
}

fun skyDomeRadiusOf(terrain: TerrainGrid): Float =
    (maxOf(terrain.columns, terrain.rows) * terrain.cellSize.value).toFloat() * SKY_DOME_RADIUS_MULTIPLE

// Polar lattice: rows are rings uniform in the horizon-to-zenith blend parameter, columns are
// azimuth spokes, so iso-colour contours align with the triangulation instead of cutting it.
// Below the horizon a short skirt at full radius drops under every terrain silhouette.
// The dome's equator stands on the ground datum, not on scene zero: a plot whose elevations are
// hundreds of metres above the origin would otherwise look at its own horizon and see mid-sky.
fun skyDomeMeshOf(radius: Float, daylight: Daylight, groundDatum: Float = 0f): Scene3dMesh {
    val ringVertexRows = SKY_SKIRT_RING_CELLS + SKY_DOME_RING_CELLS + 1
    val perRow = SKY_DOME_SPOKE_CELLS + 1
    val vertices = ArrayList<Float>(ringVertexRows * perRow * 3)
    for (ringVertex in 0 until ringVertexRows) {
        val up = ringHeightAt(ringVertex, radius)
        val ringRadius = ringRadiusAt(ringVertex, radius)
        for (spokeVertex in 0 until perRow) {
            val azimuth = 2.0 * Math.PI * spokeVertex / SKY_DOME_SPOKE_CELLS
            vertices.add((ringRadius * sin(azimuth)).toFloat())
            vertices.add(up)
            vertices.add((ringRadius * cos(azimuth)).toFloat())
        }
    }
    val triColors = ArrayList<String>((ringVertexRows - 1) * SKY_DOME_SPOKE_CELLS * 2)
    for (ring in 0 until ringVertexRows - 1) {
        for (spoke in 0 until SKY_DOME_SPOKE_CELLS) {
            val corner = ring * perRow + spoke
            triColors.add(centroidColorOf(vertices, corner, corner + 1, corner + perRow, radius, daylight))
            triColors.add(centroidColorOf(vertices, corner + 1, corner + perRow + 1, corner + perRow, radius, daylight))
        }
    }
    return Scene3dMesh(
        vertices = List(vertices.size) { if (it % 3 == 1) vertices[it] + groundDatum else vertices[it] },
        triColors = triColors,
        gridCellsX = SKY_DOME_SPOKE_CELLS,
        gridCellsZ = ringVertexRows - 1,
    )
}

private fun ringBlendAt(ringVertex: Int): Float =
    (ringVertex - SKY_SKIRT_RING_CELLS).toFloat() / SKY_DOME_RING_CELLS

private fun ringHeightAt(ringVertex: Int, radius: Float): Float {
    if (ringVertex <= SKY_SKIRT_RING_CELLS) {
        return -SKY_SKIRT_DROP_SHARE * radius * (1f - ringVertex.toFloat() / SKY_SKIRT_RING_CELLS)
    }
    val blend = ringBlendAt(ringVertex)
    return radius * blend * blend
}

private fun ringRadiusAt(ringVertex: Int, radius: Float): Float {
    if (ringVertex <= SKY_SKIRT_RING_CELLS) return radius
    val blend = ringBlendAt(ringVertex)
    return radius * sqrt((1f - blend.pow(4)).coerceAtLeast(0f))
}

private fun centroidColorOf(
    vertices: List<Float>,
    first: Int,
    second: Int,
    third: Int,
    radius: Float,
    daylight: Daylight,
): String {
    val east = (vertices[first * 3] + vertices[second * 3] + vertices[third * 3]) / 3f
    val up = (vertices[first * 3 + 1] + vertices[second * 3 + 1] + vertices[third * 3 + 1]) / 3f
    val north = (vertices[first * 3 + 2] + vertices[second * 3 + 2] + vertices[third * 3 + 2]) / 3f
    return hexOf(skyColorToward(east, up, north, radius, daylight))
}

private fun skyColorToward(east: Float, up: Float, north: Float, radius: Float, daylight: Daylight): Rgb {
    val elevation = (up / radius).coerceIn(0f, 1f)
    val base = blend(daylight.horizonTint, daylight.zenithTint, sqrt(elevation))
    val length = sqrt(east * east + up * up + north * north).coerceAtLeast(1e-6f)
    val towardSun = (east * daylight.sunDirection.east + up * daylight.sunDirection.up + north * daylight.sunDirection.north) / length
    val glowFadedIntoSkirtBottomWhichBackgroundMatches = (1f + up / (SKY_SKIRT_DROP_SHARE * radius)).coerceIn(0f, 1f)
    val glow = towardSun.coerceAtLeast(0f).pow(SUN_GLOW_TIGHTNESS) * glowFadedIntoSkirtBottomWhichBackgroundMatches
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
