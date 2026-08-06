package plottwin.solvers

fun interface UpslopeFieldSource {
    fun upslopeCellCountsOf(terrain: TerrainGrid): IntArray
}

val UNCACHED_UPSLOPE_FIELD: UpslopeFieldSource =
    UpslopeFieldSource { terrain -> computeUpslopeCellCounts(computeFlowTargets(terrain)) }

// TerrainGrid keeps reference equality, so the map keys by terrain identity: one field per terrain version
class FlowFieldCache(
    private val source: UpslopeFieldSource = UNCACHED_UPSLOPE_FIELD,
) : UpslopeFieldSource {
    private val upslopeCountsByTerrain = mutableMapOf<TerrainGrid, IntArray>()

    override fun upslopeCellCountsOf(terrain: TerrainGrid): IntArray =
        upslopeCountsByTerrain.getOrPut(terrain) { source.upslopeCellCountsOf(terrain) }
}
