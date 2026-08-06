package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpResolutionGateTest {

    @Test
    fun op_append_fires_the_op_trigger() {
        WorldLog.openInMemory().use { log ->
            val triggeredOps = mutableListOf<OpRow>()
            log.onOpAppended { _, op -> triggeredOps.add(op) }

            val addGreenhouse = OpRow(OpVerb.ADD_ROOM, mapOf(OpSlot.ROOM_KIND to "greenhouse"))
            log.append(addGreenhouse, WriterRole.LLM)

            assertEquals(listOf(addGreenhouse), triggeredOps)
        }
    }

    @Test
    fun rule_append_does_not_fire_the_op_trigger() {
        WorldLog.openInMemory().use { log ->
            val triggeredOps = mutableListOf<OpRow>()
            log.onOpAppended { _, op -> triggeredOps.add(op) }

            log.append(RuleRow("path-clearance", Hardness.HARD, 1.0, "paths keep 4' clear"), WriterRole.LLM)

            assertTrue(triggeredOps.isEmpty())
        }
    }

    @Test
    fun rejection_row_roundtrips_through_the_log() {
        WorldLog.openInMemory().use { log ->
            val rejection = swaleRejection()
            log.append(rejection, WriterRole.OPTIMIZER)

            assertEquals(rejection, log.readAll().single().row)
        }
    }

    @Test
    fun rejection_row_is_collected_in_projection() {
        WorldLog.openInMemory().use { log ->
            val rejection = swaleRejection()
            log.append(rejection, WriterRole.OPTIMIZER)

            assertEquals(listOf(rejection), log.currentState().rejections)
        }
    }

    private fun swaleRejection() = RejectionRow(
        op = OpRow(
            verb = OpVerb.ADD_ROOM,
            slots = mapOf(OpSlot.ROOM_KIND to "greenhouse", OpSlot.DESTINATION to "swale terrace"),
        ),
        violations = listOf(
            RejectedViolation("greenhouse-drainage", GroundPoint(Meters(30.0), Meters(68.0)), 12.4),
        ),
    )
}
