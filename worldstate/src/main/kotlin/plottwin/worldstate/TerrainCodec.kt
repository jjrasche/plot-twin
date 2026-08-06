package plottwin.worldstate

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

fun encodeHeightsBase64(heights: FloatArray): String {
    val packed = ByteBuffer.allocate(heights.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    heights.forEach { height -> packed.putFloat(height) }
    return Base64.getEncoder().encodeToString(packed.array())
}

fun decodeHeightsBase64(encoded: String, expectedCount: Int): FloatArray {
    val packed = ByteBuffer.wrap(Base64.getDecoder().decode(encoded)).order(ByteOrder.LITTLE_ENDIAN)
    require(packed.remaining() == expectedCount * Float.SIZE_BYTES) {
        "expected $expectedCount heights, got ${packed.remaining() / Float.SIZE_BYTES}"
    }
    return FloatArray(expectedCount) { packed.getFloat() }
}

fun terrainGridOf(base: BaseTerrainRow): TerrainGrid = TerrainGrid(
    columns = base.columns,
    rows = base.rows,
    cellSize = base.cellSize,
    surfaceHeights = decodeHeightsBase64(base.heightsBase64, base.columns * base.rows),
)
