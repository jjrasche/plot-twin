package plottwin.oppipeline

import java.time.LocalDate
import plottwin.solvers.Constraint
import plottwin.solvers.FlowFieldCache
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpStatus
import plottwin.worldstate.OpStatusRow
import plottwin.worldstate.RefRole
import plottwin.worldstate.RowRef
import plottwin.worldstate.Surface
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

class OpPipeline(
    private val log: WorldLog,
    private val date: LocalDate,
    private val surface: Surface,
    private val constraints: List<Constraint>,
    private val candidateSpacing: Meters = Meters(1.0),
) {
    private val flowFields = FlowFieldCache()

    fun attach() {
        log.onOpAppended { opSeq, op -> resolveOp(opSeq, op) }
    }

    private fun resolveOp(opSeq: Long, op: OpRow) {
        val world = PlacementWorld(log.currentState(), date, surface, constraints, candidateSpacing, flowFields)
        val verdict = resolvePlacement(world, opSeq, op)
        val resolutionSeq = appendVerdict(verdict, opSeq)
        log.append(
            OpStatusRow(statusOf(verdict)),
            WriterRole.OPTIMIZER,
            listOf(RowRef(RefRole.OP, opSeq), RowRef(RefRole.RESOLUTION, resolutionSeq)),
        )
    }

    private fun appendVerdict(verdict: PlacementVerdict, opSeq: Long): Long {
        val causedBy = listOf(RowRef(RefRole.OP, opSeq))
        return when (verdict) {
            is Placement -> log.append(verdict.diff, WriterRole.OPTIMIZER, causedBy)
            is Regrade -> appendRegrade(verdict, causedBy)
            is Rejection -> log.append(verdict.row, WriterRole.OPTIMIZER, causedBy)
        }
    }

    private fun appendRegrade(verdict: Regrade, causedBy: List<RowRef>): Long {
        verdict.diffs.forEach { diff -> log.append(diff, WriterRole.OPTIMIZER, causedBy) }
        return log.append(verdict.earthwork, WriterRole.OPTIMIZER, causedBy)
    }

    private fun statusOf(verdict: PlacementVerdict): OpStatus = when (verdict) {
        is Placement, is Regrade -> OpStatus.RESOLVED
        is Rejection -> OpStatus.REJECTED
    }
}
