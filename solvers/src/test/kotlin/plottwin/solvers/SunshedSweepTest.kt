package plottwin.solvers

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Hardness
import plottwin.worldstate.Meters
import plottwin.worldstate.RawElevation
import plottwin.worldstate.RuleRow
import plottwin.worldstate.SiteRow
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

private const val YARD_CELLS = 200
private const val YARD_CELL_METERS = 0.1
private val summerDay: LocalDate = LocalDate.of(2026, 6, 21)
private val yardSite = SiteRow(42.6006, -84.6547, "America/Detroit")

private val bedFootprint = rectangle(westEast = 9.0, eastEast = 11.0, southNorth = 11.0, northNorth = 13.0)

private fun rectangle(westEast: Double, eastEast: Double, southNorth: Double, northNorth: Double): List<GroundPoint> =
    listOf(
        GroundPoint(Meters(westEast), Meters(southNorth)),
        GroundPoint(Meters(eastEast), Meters(southNorth)),
        GroundPoint(Meters(eastEast), Meters(northNorth)),
        GroundPoint(Meters(westEast), Meters(northNorth)),
    )

private fun flatYardState(vararg standing: EntityRow): CurrentState = WorldLog.openInMemory().use { log ->
    val flatGround = RawElevation(YARD_CELLS, YARD_CELLS, Meters(YARD_CELL_METERS), FloatArray(YARD_CELLS * YARD_CELLS))
    log.append(GriddedElevationOperator.compileBaseTerrain(flatGround), WriterRole.CAPTURE)
    log.append(EntityRow("seed bed", bedFootprint, Meters(0.0)), WriterRole.CAPTURE)
    standing.forEach { log.append(it, WriterRole.CAPTURE) }
    log.append(RuleRow("bed-sun", Hardness.SOFT, 1.0, "the seed bed wants six hours of direct sun"), WriterRole.LLM)
    log.append(yardSite, WriterRole.CAPTURE)
    log.currentState()
}

private fun southWall(heightMeters: Double) = EntityRow(
    entityName = "south wall",
    footprint = rectangle(westEast = 6.0, eastEast = 14.0, southNorth = 10.0, northNorth = 10.4),
    height = Meters(heightMeters),
)

private fun bedSunHours(state: CurrentState): Double {
    val hours = requireNotNull(UNCACHED_SUNSHED_FIELD.directSunHoursOf(state, summerDay))
    val grid = state.terrain!!.grid
    return (0 until grid.cellCount)
        .filter { cell -> plottwin.geometry.isInsidePolygon(grid.centerOf(cell), bedFootprint) }
        .minOf { cell -> hours[cell].toDouble() }
}

class SunshedSweepTest {

    @Test
    fun an_open_bed_on_flat_ground_gets_nearly_the_whole_solstice_day() {
        val daylightHours = daylightRaysOn(yardSite, summerDay).size * SUN_SAMPLE_MINUTES / 60.0
        val bedHours = bedSunHours(flatYardState())
        assertTrue(
            bedHours > daylightHours - 0.5,
            "open flat ground got $bedHours h of $daylightHours h of daylight",
        )
    }

    @Test
    fun a_wall_south_of_the_bed_cuts_its_direct_sun_hours() {
        val openHours = bedSunHours(flatYardState())
        val walledHours = bedSunHours(flatYardState(southWall(heightMeters = 3.0)))
        assertTrue(walledHours < openHours, "a 3 m wall to the south changed nothing: $walledHours vs $openHours")
    }

    @Test
    fun a_taller_south_wall_cuts_more_hours_than_a_shorter_one() {
        val lowWallHours = bedSunHours(flatYardState(southWall(heightMeters = 1.0)))
        val highWallHours = bedSunHours(flatYardState(southWall(heightMeters = 6.0)))
        assertTrue(highWallHours < lowWallHours, "6 m wall left $highWallHours h, 1 m wall left $lowWallHours h")
    }

    @Test
    fun the_solver_reports_the_shortfall_in_hours_against_the_rule() {
        val state = flatYardState(southWall(heightMeters = 6.0))
        val world = SolverWorld(state, summerDay)
        val constraint = SunHoursConstraint("bed-sun", "seed bed", minimumDirectHours = 14.0)
        val violations = runSolvers(world, listOf(constraint))
        val sunshed = violations.single { it.ruleName == "bed-sun" }
        assertEquals(14.0 - bedSunHours(state), sunshed.magnitude, 1e-3)
        assertTrue(plottwin.geometry.isInsidePolygon(sunshed.location, bedFootprint), "the violation should land in the bed")
    }

    @Test
    fun a_bed_that_makes_its_hours_raises_no_violation() {
        val world = SolverWorld(flatYardState(), summerDay)
        val constraint = SunHoursConstraint("bed-sun", "seed bed", minimumDirectHours = 6.0)
        assertTrue(runSolvers(world, listOf(constraint)).none { it.ruleName == "bed-sun" })
    }

    @Test
    fun the_cache_answers_a_repeat_date_without_sweeping_again() {
        val state = flatYardState()
        var sweeps = 0
        val cache = SunshedCache { cachedState, date ->
            sweeps++
            UNCACHED_SUNSHED_FIELD.directSunHoursOf(cachedState, date)
        }
        cache.directSunHoursOf(state, summerDay)
        cache.directSunHoursOf(state, summerDay)
        assertEquals(1, sweeps)
    }
}
