package plottwin.worldstate

fun foldTerrain(terrain: ProjectedTerrain?, logged: LoggedRow): ProjectedTerrain? = when (val row = logged.row) {
    is BaseTerrainRow -> ProjectedTerrain(logged.seq, terrainGridOf(row))
    is TerrainDiffRow ->
        if (row.surface == Surface.Measured) terrain?.let { ProjectedTerrain(logged.seq, patchedGrid(it.grid, row)) }
        else terrain
    else -> terrain
}

fun foldProposalTerrain(
    proposed: Map<String, ProjectedTerrain>,
    measured: ProjectedTerrain?,
    logged: LoggedRow,
    diff: TerrainDiffRow,
    surfaceName: String,
): Map<String, ProjectedTerrain> {
    val branchBase = proposed[surfaceName] ?: branchOffMeasured(measured, diff)
    return proposed + (surfaceName to ProjectedTerrain(logged.seq, patchedGrid(branchBase.grid, diff)))
}

private fun branchOffMeasured(measured: ProjectedTerrain?, diff: TerrainDiffRow): ProjectedTerrain {
    requireNotNull(measured) { "a proposal branches off measured ground; no base-terrain row logged yet" }
    require(diff.branchedFromSeq == measured.versionSeq) {
        "proposal claims measured baseline seq ${diff.branchedFromSeq} but measured ground is at seq ${measured.versionSeq}"
    }
    return measured
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
