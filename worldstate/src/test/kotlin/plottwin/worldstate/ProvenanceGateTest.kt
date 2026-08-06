package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProvenanceGateTest {

    @Test
    fun appended_refs_survive_the_log_roundtrip() {
        WorldLog.openInMemory().use { log ->
            val opSeq = log.append(addGreenhouseOp(), WriterRole.LLM)
            log.append(movedGreenhouseDiff(), WriterRole.OPTIMIZER, listOf(RowRef(RefRole.OP, opSeq)))

            assertEquals(listOf(RowRef(RefRole.OP, opSeq)), log.readAll().last().refs)
        }
    }

    @Test
    fun op_status_row_consumes_the_pending_op() {
        WorldLog.openInMemory().use { log ->
            val opSeq = log.append(addGreenhouseOp(), WriterRole.LLM)
            val diffSeq = log.append(movedGreenhouseDiff(), WriterRole.OPTIMIZER, listOf(RowRef(RefRole.OP, opSeq)))
            log.append(
                OpStatusRow(OpStatus.RESOLVED),
                WriterRole.OPTIMIZER,
                listOf(RowRef(RefRole.OP, opSeq), RowRef(RefRole.RESOLUTION, diffSeq)),
            )

            assertTrue(log.currentState().pendingOps.isEmpty())
        }
    }

    @Test
    fun op_without_a_status_row_stays_pending() {
        WorldLog.openInMemory().use { log ->
            log.append(addGreenhouseOp(), WriterRole.LLM)

            assertEquals(listOf(addGreenhouseOp()), log.currentState().pendingOps)
        }
    }

    @Test
    fun status_row_for_one_op_leaves_the_other_pending() {
        WorldLog.openInMemory().use { log ->
            val firstSeq = log.append(addGreenhouseOp(), WriterRole.LLM)
            val second = OpRow(OpVerb.MOVE, mapOf(OpSlot.SUBJECT to "pergola"))
            log.append(second, WriterRole.LLM)
            val diffSeq = log.append(movedGreenhouseDiff(), WriterRole.OPTIMIZER, listOf(RowRef(RefRole.OP, firstSeq)))
            log.append(
                OpStatusRow(OpStatus.RESOLVED),
                WriterRole.OPTIMIZER,
                listOf(RowRef(RefRole.OP, firstSeq), RowRef(RefRole.RESOLUTION, diffSeq)),
            )

            assertEquals(listOf(second), log.currentState().pendingOps)
        }
    }

    @Test
    fun intent_to_position_chain_is_queryable_from_the_log_alone() {
        val history = WorldLog.openInMemory().use { log ->
            val opSeq = log.append(addGreenhouseOp(), WriterRole.LLM)
            val diffSeq = log.append(movedGreenhouseDiff(), WriterRole.OPTIMIZER, listOf(RowRef(RefRole.OP, opSeq)))
            log.append(
                OpStatusRow(OpStatus.RESOLVED),
                WriterRole.OPTIMIZER,
                listOf(RowRef(RefRole.OP, opSeq), RowRef(RefRole.RESOLUTION, diffSeq)),
            )
            log.readAll()
        }

        val opSeq = history.first { it.row is OpRow }.seq
        val resolution = resolutionOf(history, opSeq)
        assertTrue(resolution != null)
        assertTrue(resolution.row is PositionDiffRow)
        assertEquals(opSeq, causeOpSeqOf(resolution))
    }

    private fun addGreenhouseOp() = OpRow(
        verb = OpVerb.ADD_ROOM,
        slots = mapOf(OpSlot.ROOM_KIND to "greenhouse", OpSlot.EXTENT_TEXT to "4x4"),
    )

    private fun movedGreenhouseDiff() = PositionDiffRow(
        entityName = "greenhouse",
        footprint = listOf(
            GroundPoint(Meters(0.0), Meters(0.0)),
            GroundPoint(Meters(4.0), Meters(0.0)),
            GroundPoint(Meters(4.0), Meters(4.0)),
            GroundPoint(Meters(0.0), Meters(4.0)),
        ),
        height = metersOf(feet = 9),
    )
}
