package plottwin.worldstate

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class StageGateTest {

    @Test
    fun a_stage_groups_its_ops_names_its_predecessor_and_survives_replay() {
        val dbPath = createTempDirectory("stage-gate").resolve("world.db")
        val liveProjection = WorldLog.open(dbPath).use { log ->
            val digSeq = log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "pond region", OpSlot.GROUND_FORM to "pond")), WriterRole.LLM)
            val bermSeq = log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "spoil berm", OpSlot.GROUND_FORM to "grade")), WriterRole.LLM)
            log.append(StageRow("dig the pond", listOf(digSeq), scheduledStart = "2027-04-01"), WriterRole.LLM)
            log.append(StageRow("berm the spoil", listOf(bermSeq), predecessorStageNames = listOf("dig the pond")), WriterRole.LLM)
            log.currentState()
        }

        assertEquals(setOf("dig the pond", "berm the spoil"), liveProjection.stages.keys)
        assertEquals(listOf("dig the pond"), liveProjection.stages.getValue("berm the spoil").predecessorStageNames)
        assertEquals("2027-04-01", liveProjection.stages.getValue("dig the pond").scheduledStart)

        val replayedProjection = WorldLog.open(dbPath).use { reopened -> projectCurrentState(reopened.readAll()) }
        assertEquals(liveProjection, replayedProjection)
    }
}
