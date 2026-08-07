package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.min
import plottwin.worldstate.TerrainGrid

// Albedo varies little on purpose: the eyes shadow-direction check reads the darkest bearing
// around a point, and a strong elevation tint would out-shout the light it is meant to measure.
private val DRY_GRASS = Rgb(0.42f, 0.55f, 0.29f)
private val LUSH_GRASS = Rgb(0.34f, 0.52f, 0.26f)

fun terrainMeshOf(terrain: TerrainGrid, frame: SceneFrame, shading: TerrainShading): Scene3dMesh {
    val vertexCountX = terrain.columns + 1
    val vertexCountZ = terrain.rows + 1
    val cellMeters = terrain.cellSize.value
    val vertices = ArrayList<Float>(vertexCountX * vertexCountZ * 3)
    for (vertexZ in 0 until vertexCountZ) {
        for (vertexX in 0 until vertexCountX) {
            vertices.add(frame.sceneX(vertexX * cellMeters))
            vertices.add(vertexHeightAt(terrain, vertexX, vertexZ))
            vertices.add(frame.sceneZ(vertexZ * cellMeters))
        }
    }
    val albedo = grassAlbedoOf(terrain)
    val triColors = ArrayList<String>(terrain.columns * terrain.rows * 2)
    for (row in 0 until terrain.rows) {
        for (column in 0 until terrain.columns) {
            val cell = terrain.indexOf(column, row)
            val corner = row * vertexCountX + column
            triColors.add(litCellColor(vertices, corner, corner + 1, corner + vertexCountX + 1, cell, albedo, shading))
            triColors.add(litCellColor(vertices, corner, corner + vertexCountX + 1, corner + vertexCountX, cell, albedo, shading))
        }
    }
    return Scene3dMesh(
        vertices = vertices,
        triColors = triColors,
        gridCellsX = terrain.columns,
        gridCellsZ = terrain.rows,
    )
}

private fun litCellColor(
    vertices: List<Float>,
    first: Int,
    second: Int,
    third: Int,
    cell: Int,
    albedo: List<Rgb>,
    shading: TerrainShading,
): String = hexOf(
    litColor(
        albedo = albedo[cell],
        normal = upwardTriangleNormalOf(
            vertices[first * 3], vertices[first * 3 + 1], vertices[first * 3 + 2],
            vertices[second * 3], vertices[second * 3 + 1], vertices[second * 3 + 2],
            vertices[third * 3], vertices[third * 3 + 1], vertices[third * 3 + 2],
        ),
        sunlitFraction = shading.sunlitFractions[cell],
        skyOpenness = shading.skyOpenness[cell],
        daylight = shading.daylight,
    ),
)

private fun vertexHeightAt(terrain: TerrainGrid, vertexX: Int, vertexZ: Int): Float {
    val column = min(vertexX, terrain.columns - 1)
    val row = min(vertexZ, terrain.rows - 1)
    return terrain.surfaceHeights[terrain.indexOf(column, row)]
}

// water collects low, so the low ground grows the lusher grass
private fun grassAlbedoOf(terrain: TerrainGrid): List<Rgb> {
    val lowest = terrain.surfaceHeights.min()
    val span = terrain.surfaceHeights.max() - lowest
    return terrain.surfaceHeights.map { height ->
        val dryness = if (span <= 0f) 0.5f else ((height - lowest) / span).coerceIn(0f, 1f)
        Rgb(
            red = LUSH_GRASS.red + (DRY_GRASS.red - LUSH_GRASS.red) * dryness,
            green = LUSH_GRASS.green + (DRY_GRASS.green - LUSH_GRASS.green) * dryness,
            blue = LUSH_GRASS.blue + (DRY_GRASS.blue - LUSH_GRASS.blue) * dryness,
        )
    }
}

internal fun channelHex(channel: Int): String = channel.coerceIn(0, 255).toString(16).padStart(2, '0')
