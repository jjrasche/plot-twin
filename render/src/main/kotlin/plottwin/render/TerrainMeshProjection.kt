package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.min
import plottwin.solvers.TerrainGrid

fun terrainMeshOf(terrain: TerrainGrid, frame: SceneFrame): Scene3dMesh {
    val vertexCountX = terrain.columns + 1
    val vertexCountZ = terrain.rows + 1
    val cellMeters = terrain.cellSize.value
    val lowest = terrain.surfaceHeights.min()
    val highest = terrain.surfaceHeights.max()
    val vertices = ArrayList<Float>(vertexCountX * vertexCountZ * 3)
    for (vertexZ in 0 until vertexCountZ) {
        for (vertexX in 0 until vertexCountX) {
            vertices.add(frame.sceneX(vertexX * cellMeters))
            vertices.add(vertexHeightAt(terrain, vertexX, vertexZ))
            vertices.add(frame.sceneZ(vertexZ * cellMeters))
        }
    }
    val triColors = ArrayList<String>(terrain.columns * terrain.rows * 2)
    for (row in 0 until terrain.rows) {
        for (column in 0 until terrain.columns) {
            val tone = elevationTone(terrain.surfaceHeights[terrain.indexOf(column, row)], lowest, highest)
            triColors.add(grassColor(tone, shade = 1.0f))
            triColors.add(grassColor(tone, shade = 0.92f))
        }
    }
    return Scene3dMesh(
        vertices = vertices,
        triColors = triColors,
        gridCellsX = terrain.columns,
        gridCellsZ = terrain.rows,
    )
}

private fun vertexHeightAt(terrain: TerrainGrid, vertexX: Int, vertexZ: Int): Float {
    val column = min(vertexX, terrain.columns - 1)
    val row = min(vertexZ, terrain.rows - 1)
    return terrain.surfaceHeights[terrain.indexOf(column, row)]
}

private fun elevationTone(height: Float, lowest: Float, highest: Float): Float {
    val span = highest - lowest
    if (span <= 0f) return 0.5f
    return ((height - lowest) / span).coerceIn(0f, 1f)
}

private fun grassColor(tone: Float, shade: Float): String {
    val red = ((58f + tone * 88f) * shade).toInt()
    val green = ((92f + tone * 108f) * shade).toInt()
    val blue = (54f * shade).toInt()
    return channelHex(red) + channelHex(green) + channelHex(blue)
}

internal fun channelHex(channel: Int): String = channel.coerceIn(0, 255).toString(16).padStart(2, '0')
