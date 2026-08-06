package plottwin.worldstate

data class PlacedEntity(
    val footprint: List<GroundPoint>,
    val height: Meters,
)

data class CurrentState(
    val entities: Map<String, PlacedEntity>,
    val rules: Map<String, RuleRow>,
    val locks: Map<String, LockKind>,
    val pendingOps: List<OpRow>,
) {
    companion object {
        val EMPTY = CurrentState(entities = emptyMap(), rules = emptyMap(), locks = emptyMap(), pendingOps = emptyList())
    }
}

fun projectCurrentState(log: List<LoggedRow>): CurrentState =
    log.fold(CurrentState.EMPTY) { state, logged -> applyRow(state, logged.row) }

private fun applyRow(state: CurrentState, row: WorldRow): CurrentState = when (row) {
    is EntityRow -> placeEntity(state, row.entityName, row.footprint, row.height)
    is PositionDiffRow -> placeEntity(state, row.entityName, row.footprint, row.height)
    is RuleRow -> state.copy(rules = state.rules + (row.ruleName to row))
    is LockRow -> state.copy(locks = state.locks + (row.targetName to row.kind))
    is OpRow -> state.copy(pendingOps = state.pendingOps + row)
}

private fun placeEntity(state: CurrentState, name: String, footprint: List<GroundPoint>, height: Meters): CurrentState =
    state.copy(entities = state.entities + (name to PlacedEntity(footprint, height)))
