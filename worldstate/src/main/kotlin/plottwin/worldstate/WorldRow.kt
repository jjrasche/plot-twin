package plottwin.worldstate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class WriterRole { OWNER, LLM, OPTIMIZER, CAPTURE }

enum class Hardness { HARD, SOFT }

enum class LockKind { LOCK, MASK }

enum class OpVerb { ADD_ROOM, MOVE, RESIZE, REROUTE, RELAX, LOCK, REGRADE }

enum class OpSlot { SUBJECT, ROOM_KIND, DESTINATION, RELATION, RULE_NAME, EXTENT_TEXT, GROUND_FORM, SPOIL_DESTINATION }

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

// The frame every GroundPoint in the log is measured in: plot-local metres east and north of
// this projected origin. Without it the log's ground coordinates cannot be put back on Earth.
@Serializable
data class GroundFrame(
    val crs: String,
    val originEasting: Meters,
    val originNorthing: Meters,
)

@Serializable
data class BoundaryProvenance(
    val source: String,
    val pulledAtUtc: String,
    val observedAt: String? = null,
    val observedAtAbsentReason: String? = null,
    val sha256: String,
    val contract: String,
) {
    init {
        require((observedAt == null) == (observedAtAbsentReason != null)) {
            "a boundary carries the source's own currency date, or the reason it has none - never both, never neither"
        }
    }
}

@Serializable
@SerialName("parcel_boundary")
data class ParcelBoundaryRow(
    val parcelId: String,
    val ring: List<GroundPoint>,
    val frame: GroundFrame,
    val acresStated: Double,
    val provenance: BoundaryProvenance,
) : WorldRow {
    init {
        require(ring.size >= 3) { "a parcel boundary needs at least three vertices, got ${ring.size}" }
        require(ring.first() != ring.last()) { "the ring is stored open: the closing vertex is the wrap" }
    }
}

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
    val surface: Surface = Surface.Measured,
    val branchedFromSeq: Long? = null,
) : WorldRow {
    init {
        require((surface is Surface.Proposed) == (branchedFromSeq != null)) {
            "a proposed diff carries the measured baseline seq it branched from; a measured diff carries none"
        }
    }
}

@Serializable
@SerialName("surface_realized")
data class SurfaceRealizedRow(
    val surfaceName: String,
    val confirmedBySeq: Long,
) : WorldRow

// Q-007: IfcTask-shaped staging — member ops, FINISH_START predecessors, optional planned dates (ISO-8601)
@Serializable
@SerialName("stage")
data class StageRow(
    val stageName: String,
    val memberOpSeqs: List<Long>,
    val predecessorStageNames: List<String> = emptyList(),
    val scheduledStart: String? = null,
    val scheduledFinish: String? = null,
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

// D-013 as amended: the writer role derives from the surface — measured ground is CAPTURE's,
// proposed ground is the OPTIMIZER's, and realization is capture confirming a proposal.
fun geometryWriterFor(row: WorldRow): WriterRole? = when {
    row is PositionDiffRow -> WriterRole.OPTIMIZER
    row is EntityRow && row.footprint.isNotEmpty() -> WriterRole.CAPTURE
    row is TerrainDiffRow -> if (row.surface is Surface.Proposed) WriterRole.OPTIMIZER else WriterRole.CAPTURE
    row is BaseTerrainRow -> WriterRole.CAPTURE
    row is SurfaceRealizedRow -> WriterRole.CAPTURE
    row is SiteRow -> WriterRole.CAPTURE
    row is ParcelBoundaryRow -> WriterRole.CAPTURE
    else -> null
}
