package plottwin.worldstate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class WriterRole { OWNER, LLM, OPTIMIZER, CAPTURE }

enum class Hardness { HARD, SOFT }

enum class LockKind { LOCK, MASK }

enum class OpVerb { ADD_ROOM, MOVE, RESIZE, REROUTE, RELAX, LOCK }

enum class OpSlot { SUBJECT, ROOM_KIND, DESTINATION, RELATION, RULE_NAME, EXTENT_TEXT }

enum class OpStatus { RESOLVED, REJECTED }

enum class RefRole { OP, RESOLUTION }

@Serializable
data class RowRef(val role: RefRole, val seq: Long)

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
@SerialName("site")
data class SiteRow(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val timeZoneId: String,
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

@Serializable
@SerialName("base_terrain")
data class BaseTerrainRow(
    val columns: Int,
    val rows: Int,
    val cellSize: Meters,
    val heightsBase64: String,
) : WorldRow

@Serializable
@SerialName("terrain_diff")
data class TerrainDiffRow(
    val firstColumn: Int,
    val firstRow: Int,
    val columns: Int,
    val rows: Int,
    val heightsBase64: String,
) : WorldRow

@Serializable
@SerialName("op_status")
data class OpStatusRow(
    val status: OpStatus,
) : WorldRow

@Serializable
data class RejectedViolation(
    val ruleName: String,
    val location: GroundPoint,
    val magnitude: Double,
)

@Serializable
@SerialName("rejection")
data class RejectionRow(
    val op: OpRow,
    val violations: List<RejectedViolation>,
) : WorldRow

// D-013: capture measures (footprints, ground), the optimizer places (position diffs)
fun geometryWriterFor(row: WorldRow): WriterRole? = when {
    row is PositionDiffRow -> WriterRole.OPTIMIZER
    row is EntityRow && row.footprint.isNotEmpty() -> WriterRole.CAPTURE
    row is BaseTerrainRow || row is TerrainDiffRow -> WriterRole.CAPTURE
    row is SiteRow -> WriterRole.CAPTURE
    else -> null
}
