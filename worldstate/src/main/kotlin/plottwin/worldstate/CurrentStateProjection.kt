package plottwin.worldstate

data class PlacedEntity(
    val footprint: List<GroundPoint>,
    val height: Meters,
)

data class ProjectedTerrain(
    val versionSeq: Long,
    val grid: TerrainGrid,
)

data class CurrentState(
    val entities: Map<String, PlacedEntity>,
    val rules: Map<String, RuleRow>,
    val locks: Map<String, LockKind>,
    val pendingOpsBySeq: Map<Long, OpRow>,
    val rejections: List<RejectionRow>,
    val terrain: ProjectedTerrain? = null,
) {
    val pendingOps: List<OpRow> get() = pendingOpsBySeq.values.toList()

    companion object {
        val EMPTY = CurrentState(
            entities = emptyMap(),
            rules = emptyMap(),
            locks = emptyMap(),
            pendingOpsBySeq = emptyMap(),
            rejections = emptyList(),
        )
    }
}

fun projectCurrentState(log: List<LoggedRow>): CurrentState =
    log.fold(CurrentState.EMPTY) { state, logged -> applyRow(state, logged) }

private fun applyRow(state: CurrentState, logged: LoggedRow): CurrentState = when (val row = logged.row) {
    is EntityRow -> placeEntity(state, row.entityName, row.footprint, row.height)
    is PositionDiffRow -> placeEntity(state, row.entityName, row.footprint, row.height)
    is RuleRow -> state.copy(rules = state.rules + (row.ruleName to row))
    is LockRow -> state.copy(locks = state.locks + (row.targetName to row.kind))
    is OpRow -> state.copy(pendingOpsBySeq = state.pendingOpsBySeq + (logged.seq to row))
    is OpStatusRow -> consumeOp(state, logged)
    is RejectionRow -> state.copy(rejections = state.rejections + row)
    is BaseTerrainRow -> state.copy(terrain = ProjectedTerrain(logged.seq, terrainGridOf(row)))
    is TerrainDiffRow -> patchTerrain(state, logged.seq, row)
}

private fun patchTerrain(state: CurrentState, diffSeq: Long, diff: TerrainDiffRow): CurrentState {
    val baseTerrain = state.terrain ?: return state
    val baseGrid = baseTerrain.grid
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
    val patchedGrid = TerrainGrid(baseGrid.columns, baseGrid.rows, baseGrid.cellSize, patchedHeights)
    return state.copy(terrain = ProjectedTerrain(diffSeq, patchedGrid))
}

private fun consumeOp(state: CurrentState, statusLogged: LoggedRow): CurrentState {
    val consumedOpSeq = causeOpSeqOf(statusLogged) ?: return state
    return state.copy(pendingOpsBySeq = state.pendingOpsBySeq - consumedOpSeq)
}

private fun placeEntity(state: CurrentState, name: String, footprint: List<GroundPoint>, height: Meters): CurrentState =
    state.copy(entities = state.entities + (name to PlacedEntity(footprint, height)))
