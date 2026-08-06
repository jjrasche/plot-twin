package plottwin.worldstate

fun foldTerrain(terrain: ProjectedTerrain?, logged: LoggedRow): ProjectedTerrain? = when (val row = logged.row) {
    is BaseTerrainRow -> ProjectedTerrain(logged.seq, terrainGridOf(row))
    is TerrainDiffRow -> terrain?.let { ProjectedTerrain(logged.seq, patchedGrid(it.grid, row)) }
    else -> terrain
}

private fun patchedGrid(baseGrid: TerrainGrid, diff: TerrainDiffRow): TerrainGrid {
    require(diff.firstColumn >= 0 && diff.firstColumn + diff.columns <= baseGrid.columns) { "diff columns outside the base grid" }
    require(diff.firstRow >= 0 && diff.firstRow + diff.rows <= baseGrid.rows) { "diff rows outside the base grid" }
    val patchedHeights = baseGrid.surfaceHeights.copyOf()
    val regionHeights = decodeHeightsBase64(diff.heightsBase64, diff.columns * diff.rows)
    for (regionRow in 0 until diff.rows) {
        for (regionColumn in 0 until diff.columns) {
            val cell = baseGrid.indexOf(diff.firstColumn + regionColumn, diff.firstRow + regionRow)
            patchedHeights[cell] = regionHeights[regionRow * diff.columns + regionColumn]
        }
    }
    return TerrainGrid(baseGrid.columns, baseGrid.rows, baseGrid.cellSize, patchedHeights)
}
