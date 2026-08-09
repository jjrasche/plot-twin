package plottwin.oppipeline

import java.time.LocalDate
import plottwin.solvers.Constraint
import plottwin.solvers.FlowFieldCache
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.LockKind
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.OpVerb
import plottwin.worldstate.RejectedViolation
import plottwin.worldstate.RejectionRow
import plottwin.worldstate.Surface
import plottwin.worldstate.metersOf

val DEFAULT_ROOM_HEIGHT: Meters = metersOf(feet = 9)

data class PlacementWorld(
    val projection: CurrentState,
    val date: LocalDate,
    val surface: Surface,
    val constraints: List<Constraint>,
    val candidateSpacing: Meters,
    val flowFields: FlowFieldCache = FlowFieldCache(),
)

fun resolvePlacement(world: PlacementWorld, opSeq: Long, op: OpRow): PlacementVerdict = when (op.verb) {
    OpVerb.ADD_ROOM -> resolveAddRoom(world, op)
    OpVerb.MOVE -> resolveMove(world, op)
    OpVerb.REGRADE -> resolveRegrade(world, opSeq, op)
    OpVerb.RESIZE, OpVerb.REROUTE, OpVerb.RELAX, OpVerb.LOCK -> slotRejection(op, "verb-unsupported")
}

internal fun parseExtentMeters(extentText: String?): RoomExtent? {
    val sides = extentText?.split("x") ?: return null
    if (sides.size != 2) return null
    val widthEast = sides[0].trim().toDoubleOrNull() ?: return null
    val depthNorth = sides[1].trim().toDoubleOrNull() ?: return null
    if (widthEast <= 0.0 || depthNorth <= 0.0) return null
    return RoomExtent(Meters(widthEast), Meters(depthNorth))
}

private fun resolveAddRoom(world: PlacementWorld, op: OpRow): PlacementVerdict {
    val roomName = op.slots[OpSlot.SUBJECT] ?: op.slots[OpSlot.ROOM_KIND] ?: return slotRejection(op, "room-name-missing")
    val extent = parseExtentMeters(op.slots[OpSlot.EXTENT_TEXT]) ?: return slotRejection(op, "extent-unreadable")
    val regionRing = destinationRegionOf(world.projection, op) ?: return slotRejection(op, "destination-region-missing")
    return placeInsideRegion(world, op, roomName, extent, DEFAULT_ROOM_HEIGHT, regionRing)
}

private fun resolveMove(world: PlacementWorld, op: OpRow): PlacementVerdict {
    val subjectName = op.slots[OpSlot.SUBJECT] ?: return slotRejection(op, "subject-missing")
    if (world.projection.locks[subjectName] == LockKind.LOCK) return lockedRejection(world.projection, op, subjectName)
    val subject = world.projection.entities[subjectName] ?: return slotRejection(op, "subject-unknown")
    val regionRing = destinationRegionOf(world.projection, op) ?: return slotRejection(op, "destination-region-missing")
    return placeInsideRegion(world, op, subjectName, boundingExtentOf(subject.footprint), subject.height, regionRing)
}

private fun destinationRegionOf(projection: CurrentState, op: OpRow): List<GroundPoint>? {
    val destinationName = op.slots[OpSlot.DESTINATION] ?: return null
    return projection.entities[destinationName]?.footprint?.takeIf { it.size >= 3 }
}

private fun lockedRejection(projection: CurrentState, op: OpRow, subjectName: String): Rejection {
    val subjectFootprint = projection.entities[subjectName]?.footprint.orEmpty()
    val lockLocation = if (subjectFootprint.isEmpty()) GROUND_ORIGIN else centroidOf(subjectFootprint)
    return Rejection(RejectionRow(op, listOf(RejectedViolation("entity-locked", lockLocation, 0.0))))
}

internal fun slotRejection(op: OpRow, ruleName: String): Rejection =
    Rejection(RejectionRow(op, listOf(RejectedViolation(ruleName, GROUND_ORIGIN, 0.0))))

private val GROUND_ORIGIN = GroundPoint(Meters(0.0), Meters(0.0))
