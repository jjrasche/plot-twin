package plottwin.oppipeline

import java.time.LocalDate
import plottwin.solvers.Constraint
import plottwin.solvers.TerrainGrid
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.WorldLog

class OpPipeline(
    private val log: WorldLog,
    private val terrain: TerrainGrid,
    private val date: LocalDate,
    private val constraints: List<Constraint>,
    private val candidateSpacing: Meters = Meters(1.0),
) {
    fun attach() {
        log.onOpAppended { op -> resolveOp(op) }
    }

    private fun resolveOp(op: OpRow) {
        TODO("optimizer v0")
    }
}
