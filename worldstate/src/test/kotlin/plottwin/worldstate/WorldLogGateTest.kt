package plottwin.worldstate

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorldLogGateTest {

    @Test
    fun appended_op_row_appears_in_current_state_projection() {
        WorldLog.openInMemory().use { log ->
            val addGreenhouse = OpRow(
                verb = OpVerb.ADD_ROOM,
                slots = mapOf(OpSlot.ROOM_KIND to "greenhouse", OpSlot.RELATION to "south of the shop"),
            )
            log.append(addGreenhouse, WriterRole.LLM)

            assertEquals(listOf(addGreenhouse), log.currentState().pendingOps)
        }
    }

    @Test
    fun replaying_the_log_from_zero_yields_an_identical_projection() {
        val dbPath = createTempDirectory("worldstate-gate").resolve("world.db")
        val liveProjection = WorldLog.open(dbPath).use { log ->
            appendToyPlotHistory(log)
            log.currentState()
        }

        val replayedProjection = WorldLog.open(dbPath).use { reopened ->
            projectCurrentState(reopened.readAll())
        }

        assertEquals(liveProjection, replayedProjection)
    }

    @Test
    fun rule_append_fires_the_resolve_trigger() {
        WorldLog.openInMemory().use { log ->
            val triggeredRules = mutableListOf<RuleRow>()
            log.onRuleAppended { rule -> triggeredRules.add(rule) }

            val setback = RuleRow(
                ruleName = "greenhouse-setback",
                hardness = Hardness.HARD,
                weight = 1.0,
                statement = "greenhouse at least 10' from the property line",
            )
            log.append(setback, WriterRole.LLM)

            assertEquals(listOf(setback), triggeredRules)
        }
    }

    @Test
    fun entity_footprint_from_llm_is_rejected() {
        WorldLog.openInMemory().use { log ->
            assertFailsWith<GeometryWriteRejected> {
                log.append(greenhouseEntity(), WriterRole.LLM)
            }
            assertTrue(log.readAll().isEmpty())
        }
    }

    @Test
    fun position_diff_from_owner_is_rejected() {
        WorldLog.openInMemory().use { log ->
            log.append(greenhouseEntity(), WriterRole.OPTIMIZER)

            assertFailsWith<GeometryWriteRejected> {
                log.append(greenhouseMovedEast(), WriterRole.OWNER)
            }
        }
    }

    @Test
    fun position_diff_from_optimizer_moves_the_entity_in_projection() {
        WorldLog.openInMemory().use { log ->
            log.append(greenhouseEntity(), WriterRole.OPTIMIZER)
            val moved = greenhouseMovedEast()
            log.append(moved, WriterRole.OPTIMIZER)

            val placed = log.currentState().entities.getValue("greenhouse")
            assertEquals(moved.footprint, placed.footprint)
        }
    }

    @Test
    fun named_landmark_without_footprint_is_appendable_by_llm() {
        WorldLog.openInMemory().use { log ->
            val maple = EntityRow(entityName = "the maple", footprint = emptyList(), height = metersOf(feet = 80))
            log.append(maple, WriterRole.LLM)

            assertTrue(log.currentState().entities.containsKey("the maple"))
        }
    }

    @Test
    fun latest_rule_append_wins_in_projection() {
        WorldLog.openInMemory().use { log ->
            val strict = RuleRow("path-clearance", Hardness.HARD, 1.0, "paths at least 4' wide")
            val relaxed = RuleRow("path-clearance", Hardness.SOFT, 0.5, "paths at least 3' wide")
            log.append(strict, WriterRole.LLM)
            log.append(relaxed, WriterRole.LLM)

            assertEquals(relaxed, log.currentState().rules.getValue("path-clearance"))
        }
    }

    @Test
    fun lock_row_marks_the_entity_locked_in_projection() {
        WorldLog.openInMemory().use { log ->
            log.append(greenhouseEntity(), WriterRole.OPTIMIZER)
            log.append(LockRow(targetName = "greenhouse", kind = LockKind.LOCK), WriterRole.OWNER)

            assertEquals(LockKind.LOCK, log.currentState().locks["greenhouse"])
        }
    }

    private fun appendToyPlotHistory(log: WorldLog) {
        log.append(greenhouseEntity(), WriterRole.OPTIMIZER)
        log.append(RuleRow("greenhouse-setback", Hardness.HARD, 1.0, "10' from the line"), WriterRole.LLM)
        log.append(OpRow(OpVerb.MOVE, mapOf(OpSlot.SUBJECT to "greenhouse", OpSlot.RELATION to "closer to the well")), WriterRole.OWNER)
        log.append(greenhouseMovedEast(), WriterRole.OPTIMIZER)
        log.append(LockRow("greenhouse", LockKind.LOCK), WriterRole.OWNER)
    }

    private fun greenhouseEntity() = EntityRow(
        entityName = "greenhouse",
        footprint = rectangleFootprint(originEast = Meters(10.0), originNorth = Meters(20.0)),
        height = metersOf(feet = 9),
    )

    private fun greenhouseMovedEast() = PositionDiffRow(
        entityName = "greenhouse",
        footprint = rectangleFootprint(originEast = Meters(14.0), originNorth = Meters(20.0)),
        height = metersOf(feet = 9),
    )

    private fun rectangleFootprint(originEast: Meters, originNorth: Meters): List<GroundPoint> {
        val east = originEast.value
        val north = originNorth.value
        return listOf(
            GroundPoint(Meters(east), Meters(north)),
            GroundPoint(Meters(east + 3.0), Meters(north)),
            GroundPoint(Meters(east + 3.0), Meters(north + 6.0)),
            GroundPoint(Meters(east), Meters(north + 6.0)),
        )
    }
}
