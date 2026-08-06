package plottwin.solvers

import plottwin.worldstate.ProjectedTerrain

fun interface UpslopeFieldSource {
    fun upslopeCellCountsOf(terrain: ProjectedTerrain): IntArray
}

val UNCACHED_UPSLOPE_FIELD: UpslopeFieldSource =
    UpslopeFieldSource { terrain -> computeUpslopeCellCounts(computeFlowTargets(terrain.grid)) }

// keyed on the terrain row seq: one field per logged terrain version
class FlowFieldCache(
    private val source: UpslopeFieldSource = UNCACHED_UPSLOPE_FIELD,
) : UpslopeFieldSource {
    private val upslopeCountsByVersion = mutableMapOf<Long, IntArray>()

    override fun upslopeCellCountsOf(terrain: ProjectedTerrain): IntArray =
        upslopeCountsByVersion.getOrPut(terrain.versionSeq) { source.upslopeCellCountsOf(terrain) }
}
