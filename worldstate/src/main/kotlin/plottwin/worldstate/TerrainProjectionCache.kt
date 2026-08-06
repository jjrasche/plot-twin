package plottwin.worldstate

// The folded-through seq is the whole cache key; a second projection parameter makes it a map, not a field.
class TerrainProjectionCache(private val readRowsAfter: (Long) -> List<LoggedRow>) {

    var foldedThroughSeq: Long = WorldLog.BEFORE_FIRST_SEQ
        private set

    private var foldedTerrain: ProjectedTerrain? = null

    fun terrain(): ProjectedTerrain? {
        foldArrivedRows()
        return foldedTerrain
    }

    private fun foldArrivedRows() {
        val arrivedRows = readRowsAfter(foldedThroughSeq)
        arrivedRows.forEach { logged -> foldedTerrain = foldTerrain(foldedTerrain, logged) }
        foldedThroughSeq = arrivedRows.lastOrNull()?.seq ?: foldedThroughSeq
    }

    companion object {
        fun over(log: WorldLog): TerrainProjectionCache = TerrainProjectionCache(log::readRowsAfter)
    }
}
