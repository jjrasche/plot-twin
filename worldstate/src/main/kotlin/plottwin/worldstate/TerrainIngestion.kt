package plottwin.worldstate

// Genesis adapter/operator shape: an adapter daemon pulls raw elevation; an operator compiles
// raw into log rows. Only the operator seam exists here — LiDAR pull is the adapter's job.
class RawElevation(
    val columns: Int,
    val rows: Int,
    val cellSize: Meters,
    val surfaceHeights: FloatArray,
)

fun interface TerrainOperator {
    fun compileBaseTerrain(raw: RawElevation): BaseTerrainRow
}

val GriddedElevationOperator: TerrainOperator = TerrainOperator { raw ->
    BaseTerrainRow(
        columns = raw.columns,
        rows = raw.rows,
        cellSize = raw.cellSize,
        heightsBase64 = encodeHeightsBase64(raw.surfaceHeights),
    )
}
