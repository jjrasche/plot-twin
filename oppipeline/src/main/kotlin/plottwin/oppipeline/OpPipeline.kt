package plottwin.oppipeline

import java.time.LocalDate
import plottwin.solvers.Constraint
import plottwin.solvers.TerrainGrid
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

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
        val world = PlacementWorld(log.currentState(), terrain, date, constraints, candidateSpacing)
        appendVerdict(resolvePlacement(world, op))
    }

    private fun appendVerdict(verdict: PlacementVerdict) {
        when (verdict) {
            is Placement -> log.append(verdict.diff, WriterRole.OPTIMIZER)
            is Rejection -> log.append(verdict.row, WriterRole.OPTIMIZER)
        }
    }
}
