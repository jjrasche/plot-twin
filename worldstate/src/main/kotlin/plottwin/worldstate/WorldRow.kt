package plottwin.worldstate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class WriterRole { OWNER, LLM, OPTIMIZER }

enum class Hardness { HARD, SOFT }

enum class LockKind { LOCK, MASK }

enum class OpVerb { ADD_ROOM, MOVE, RESIZE, REROUTE, RELAX, LOCK }

enum class OpSlot { SUBJECT, ROOM_KIND, DESTINATION, RELATION, RULE_NAME, EXTENT_TEXT }

@Serializable
data class GroundPoint(val east: Meters, val north: Meters)

@Serializable
sealed interface WorldRow

@Serializable
@SerialName("entity")
data class EntityRow(
    val entityName: String,
    val footprint: List<GroundPoint>,
    val height: Meters,
) : WorldRow

@Serializable
@SerialName("rule")
data class RuleRow(
    val ruleName: String,
    val hardness: Hardness,
    val weight: Double,
    val statement: String,
) : WorldRow

@Serializable
@SerialName("lock")
data class LockRow(
    val targetName: String,
    val kind: LockKind,
) : WorldRow

@Serializable
@SerialName("op")
data class OpRow(
    val verb: OpVerb,
    val slots: Map<OpSlot, String>,
) : WorldRow

@Serializable
@SerialName("position_diff")
data class PositionDiffRow(
    val entityName: String,
    val footprint: List<GroundPoint>,
    val height: Meters,
) : WorldRow

fun carriesGeometry(row: WorldRow): Boolean = when (row) {
    is PositionDiffRow -> true
    is EntityRow -> row.footprint.isNotEmpty()
    is RuleRow, is LockRow, is OpRow -> false
}
