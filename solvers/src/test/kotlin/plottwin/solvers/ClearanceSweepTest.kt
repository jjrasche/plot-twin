package plottwin.solvers

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.TerrainGrid

class ClearanceSweepTest {

    @Test
    fun known_pinch_point_yields_exact_shortfall() {
        val world = worldWith(straightPath(), planterBox(nearEdgeNorth = 0.75))
        val fourFootRule = ClearanceConstraint("path-clearance", PATH_NAME, Meters(1.25))

        val violations = ClearanceSweep.findViolations(world, fourFootRule)

        assertEquals(1, violations.size)
        val pinch = violations.single()
        assertEquals("path-clearance", pinch.ruleName)
        assertEquals(0.5, pinch.magnitude)
        assertEquals(GroundPoint(Meters(5.0), Meters(0.0)), pinch.location)
    }

    @Test
    fun obstacle_beyond_the_bound_raises_no_violation() {
        val world = worldWith(straightPath(), planterBox(nearEdgeNorth = 2.0))
        val fourFootRule = ClearanceConstraint("path-clearance", PATH_NAME, Meters(1.25))

        assertTrue(ClearanceSweep.findViolations(world, fourFootRule).isEmpty())
    }

    @Test
    fun path_crossing_an_obstacle_pinches_at_zero_distance() {
        val world = worldWith(straightPath(), planterBox(nearEdgeNorth = -0.5))
        val fourFootRule = ClearanceConstraint("path-clearance", PATH_NAME, Meters(1.25))

        val pinch = ClearanceSweep.findViolations(world, fourFootRule).single()
        assertEquals(1.25, pinch.magnitude)
    }

    @Test
    fun foreign_constraint_kind_is_ignored() {
        val world = worldWith(straightPath(), planterBox(nearEdgeNorth = 0.75))
        val drainageRule = WaterlogConstraint("drainage", "planter", maxUpslopeSquareMeters = 1.0)

        assertTrue(ClearanceSweep.findViolations(world, drainageRule).isEmpty())
    }

    private fun worldWith(path: PlacedEntity, obstacle: PlacedEntity): SolverWorld {
        val state = CurrentState.EMPTY.copy(entities = mapOf(PATH_NAME to path, "planter" to obstacle))
        return SolverWorld(state, LocalDate.of(2026, 8, 5))
    }

    private fun straightPath(): PlacedEntity = PlacedEntity(
        footprint = listOf(GroundPoint(Meters(0.0), Meters(0.0)), GroundPoint(Meters(10.0), Meters(0.0))),
        height = Meters(0.0),
    )

    private fun planterBox(nearEdgeNorth: Double): PlacedEntity = PlacedEntity(
        footprint = listOf(
            GroundPoint(Meters(5.0), Meters(nearEdgeNorth)),
            GroundPoint(Meters(6.0), Meters(nearEdgeNorth)),
            GroundPoint(Meters(6.0), Meters(nearEdgeNorth + 1.0)),
            GroundPoint(Meters(5.0), Meters(nearEdgeNorth + 1.0)),
        ),
        height = Meters(0.5),
    )

    companion object {
        private const val PATH_NAME = "garden path"
    }
}

internal fun flatTerrain(columns: Int, rows: Int, height: Float = 0.0f): TerrainGrid =
    TerrainGrid(columns, rows, Meters(0.1), FloatArray(columns * rows) { height })
