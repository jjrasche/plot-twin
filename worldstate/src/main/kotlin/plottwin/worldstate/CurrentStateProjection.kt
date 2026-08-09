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
    val proposedTerrain: Map<String, ProjectedTerrain> = emptyMap(),
    val realizedSurfaces: Map<String, Long> = emptyMap(),
    val earthworks: List<LoggedEarthwork> = emptyList(),
    val stages: Map<String, StageRow> = emptyMap(),
    val site: SiteRow? = null,
) {
    val pendingOps: List<OpRow> get() = pendingOpsBySeq.values.toList()

    fun terrainOn(surface: Surface): ProjectedTerrain? = when (surface) {
        Surface.Measured -> terrain
        is Surface.Proposed -> proposedTerrain[surface.name]
    }

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
    is SiteRow -> state.copy(site = row)
    is RuleRow -> state.copy(rules = state.rules + (row.ruleName to row))
    is LockRow -> state.copy(locks = state.locks + (row.targetName to row.kind))
    is OpRow -> state.copy(pendingOpsBySeq = state.pendingOpsBySeq + (logged.seq to row))
    is OpStatusRow -> consumeOp(state, logged)
    is RejectionRow -> state.copy(rejections = state.rejections + row)
    is BaseTerrainRow -> state.copy(terrain = foldTerrain(state.terrain, logged))
    is TerrainDiffRow -> foldTerrainDiff(state, logged, row)
    is SurfaceRealizedRow -> state.copy(realizedSurfaces = state.realizedSurfaces + (row.surfaceName to row.confirmedBySeq))
    is EarthworkRow -> state.copy(earthworks = state.earthworks + LoggedEarthwork(logged.seq, causeOpSeqOf(logged), row))
    is StageRow -> state.copy(stages = state.stages + (row.stageName to row))
}

private fun foldTerrainDiff(state: CurrentState, logged: LoggedRow, diff: TerrainDiffRow): CurrentState =
    when (val surface = diff.surface) {
        Surface.Measured -> state.copy(terrain = foldTerrain(state.terrain, logged))
        is Surface.Proposed -> state.copy(
            proposedTerrain = foldProposalTerrain(state.proposedTerrain, state.terrain, logged, diff, surface.name),
        )
    }

private fun consumeOp(state: CurrentState, statusLogged: LoggedRow): CurrentState {
    val consumedOpSeq = causeOpSeqOf(statusLogged) ?: return state
    return state.copy(pendingOpsBySeq = state.pendingOpsBySeq - consumedOpSeq)
}

private fun placeEntity(state: CurrentState, name: String, footprint: List<GroundPoint>, height: Meters): CurrentState =
    state.copy(entities = state.entities + (name to PlacedEntity(footprint, height)))
