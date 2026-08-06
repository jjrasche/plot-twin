package plottwin.oppipeline

import plottwin.solvers.SolverWorld
import plottwin.solvers.Violation
import plottwin.solvers.runSolvers
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Hardness
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.PositionDiffRow
import plottwin.worldstate.RejectedViolation
import plottwin.worldstate.RejectionRow

data class EvaluatedCandidate(
    val anchor: GroundPoint,
    val footprint: List<GroundPoint>,
    val hardViolations: List<Violation>,
    val softViolations: List<Violation>,
    val softScore: Double,
    val pathDistance: Double,
)

private val winningPlacementFirst: Comparator<EvaluatedCandidate> =
    compareBy<EvaluatedCandidate> { it.softScore }
        .thenBy { it.pathDistance }
        .thenBy { it.anchor.east.value }
        .thenBy { it.anchor.north.value }

private val leastViolatingFirst: Comparator<EvaluatedCandidate> =
    compareBy<EvaluatedCandidate> { it.hardViolations.size }
        .thenBy { it.softScore }
        .thenBy { it.anchor.east.value }
        .thenBy { it.anchor.north.value }

fun placeInsideRegion(
    world: PlacementWorld,
    op: OpRow,
    entityName: String,
    extent: RoomExtent,
    height: Meters,
    regionRing: List<GroundPoint>,
): PlacementVerdict {
    val candidateFootprints = scanCandidateFootprints(regionRing, extent, world.candidateSpacing)
    if (candidateFootprints.isEmpty()) return regionTooSmallRejection(op, regionRing)
    val baseline = runConstraintSolvers(world, world.projection)
    val evaluated = candidateFootprints.map { footprint -> evaluateCandidate(world, entityName, footprint, height, baseline) }
    val admissible = evaluated.filter { it.hardViolations.isEmpty() }
    if (admissible.isEmpty()) return fullViolationRejection(op, evaluated)
    val winner = admissible.minWith(winningPlacementFirst)
    return Placement(PositionDiffRow(entityName, winner.footprint, height))
}

private fun evaluateCandidate(
    world: PlacementWorld,
    entityName: String,
    footprint: List<GroundPoint>,
    height: Meters,
    baseline: List<Violation>,
): EvaluatedCandidate {
    val withCandidate = world.projection.copy(
        entities = world.projection.entities + (entityName to PlacedEntity(footprint, height)),
    )
    val introduced = runConstraintSolvers(world, withCandidate) - baseline.toSet()
    val (hardViolations, softViolations) = introduced.partition { isHardRule(world.projection, it.ruleName) }
    return EvaluatedCandidate(
        anchor = footprint.first(),
        footprint = footprint,
        hardViolations = hardViolations,
        softViolations = softViolations,
        softScore = softViolations.sumOf { it.severity },
        pathDistance = distanceToNearestPath(world.projection, centroidOf(footprint)),
    )
}

private fun runConstraintSolvers(world: PlacementWorld, projection: CurrentState): List<Violation> =
    runSolvers(SolverWorld(projection, world.terrain, world.date), world.constraints)

private fun isHardRule(projection: CurrentState, ruleName: String): Boolean =
    (projection.rules[ruleName]?.hardness ?: Hardness.HARD) == Hardness.HARD

private fun distanceToNearestPath(projection: CurrentState, candidateCenter: GroundPoint): Double {
    val pathPolylines = projection.entities.values.map { it.footprint }.filter { it.size == 2 }
    if (pathPolylines.isEmpty()) return 0.0
    return pathPolylines.minOf { polyline -> distanceToSegment(candidateCenter, polyline[0], polyline[1]) }
}

private fun fullViolationRejection(op: OpRow, evaluated: List<EvaluatedCandidate>): Rejection {
    val leastViolating = evaluated.minWith(leastViolatingFirst)
    val rejectedViolations = (leastViolating.hardViolations + leastViolating.softViolations)
        .map { violation -> RejectedViolation(violation.ruleName, violation.location, violation.magnitude) }
    return Rejection(RejectionRow(op, rejectedViolations))
}

private fun regionTooSmallRejection(op: OpRow, regionRing: List<GroundPoint>): Rejection =
    Rejection(RejectionRow(op, listOf(RejectedViolation("region-too-small", centroidOf(regionRing), 0.0))))
