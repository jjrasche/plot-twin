package plottwin.solvers

import java.time.LocalDate
import java.time.ZonedDateTime
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Hardness
import plottwin.worldstate.Meters
import plottwin.worldstate.RawElevation
import plottwin.worldstate.RuleRow
import plottwin.worldstate.SiteRow
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole
import plottwin.worldstate.metersOf

// 900x900 cells at 10cm = 90m x 90m = 8100 m^2 ~ 2.0 acres
object ToyPlotFixture {
    const val CELLS_PER_SIDE = 900
    const val CELL_SIZE_METERS = 0.1
    const val SOUTHWARD_DROP_PER_CELL = 0.01f
    const val SWALE_DEPTH_METERS = 0.3f
    const val SWALE_FIRST_COLUMN = 295
    const val SWALE_LAST_COLUMN = 304
    const val WATERLOG_THRESHOLD_SQUARE_METERS = 5.0

    val toyDate: LocalDate = LocalDate.of(2026, 8, 5)

    // the home 2 acres: Delta Township, Eaton County, Michigan
    val toySite = SiteRow(latitudeDegrees = 42.6006, longitudeDegrees = -84.6547, timeZoneId = "America/Detroit")

    val toyMorning: ZonedDateTime = toyDate.atTime(9, 0).atZone(zoneOf(toySite))
    val toyMidday: ZonedDateTime = toyDate.atTime(13, 0).atZone(zoneOf(toySite))
    val toyEvening: ZonedDateTime = toyDate.atTime(18, 30).atZone(zoneOf(toySite))

    val pathClearanceBound: Meters = metersOf(feet = 4)

    fun toyWorld(): SolverWorld = SolverWorld(toyState(), toyDate, Surface.Measured)

    fun toyConstraints(): List<Constraint> = listOf(
        ClearanceConstraint("path-clearance", "garden path", pathClearanceBound),
        WaterlogConstraint("pergola-drainage", "pergola", WATERLOG_THRESHOLD_SQUARE_METERS),
        WaterlogConstraint("greenhouse-drainage", "greenhouse", WATERLOG_THRESHOLD_SQUARE_METERS),
    )

    fun rawSwaleElevation(): RawElevation = RawElevation(
        columns = CELLS_PER_SIDE,
        rows = CELLS_PER_SIDE,
        cellSize = Meters(CELL_SIZE_METERS),
        surfaceHeights = swaleHeights(),
    )

    fun terrainWithSwale(): TerrainGrid =
        TerrainGrid(CELLS_PER_SIDE, CELLS_PER_SIDE, Meters(CELL_SIZE_METERS), swaleHeights())

    fun toyState(): CurrentState = WorldLog.openInMemory().use { log ->
        appendToyPlot(log)
        log.currentState()
    }

    fun appendToyPlot(log: WorldLog) {
        log.append(GriddedElevationOperator.compileBaseTerrain(rawSwaleElevation()), WriterRole.CAPTURE)
        log.append(greenhouseRow(), WriterRole.CAPTURE)
        log.append(pergolaRow(), WriterRole.CAPTURE)
        log.append(gardenPathRow(), WriterRole.CAPTURE)
        log.append(RuleRow("path-clearance", Hardness.HARD, 1.0, "paths keep 4' of walking clearance"), WriterRole.LLM)
        log.append(RuleRow("pergola-drainage", Hardness.SOFT, 1.0, "pergola floor stays out of runoff paths"), WriterRole.LLM)
        log.append(RuleRow("greenhouse-drainage", Hardness.HARD, 1.0, "greenhouse floor stays out of runoff paths"), WriterRole.LLM)
        log.append(toySite, WriterRole.CAPTURE)
    }

    private fun swaleHeights(): FloatArray {
        val surfaceHeights = FloatArray(CELLS_PER_SIDE * CELLS_PER_SIDE)
        for (row in 0 until CELLS_PER_SIDE) {
            for (column in 0 until CELLS_PER_SIDE) {
                surfaceHeights[row * CELLS_PER_SIDE + column] = planeHeightAt(row) - swaleCarveAt(column)
            }
        }
        return surfaceHeights
    }

    private fun planeHeightAt(row: Int): Float = row * SOUTHWARD_DROP_PER_CELL

    private fun swaleCarveAt(column: Int): Float =
        if (column in SWALE_FIRST_COLUMN..SWALE_LAST_COLUMN) SWALE_DEPTH_METERS else 0.0f

    private fun greenhouseRow() = EntityRow(
        entityName = "greenhouse",
        footprint = rectangleRing(westEast = 50.0, eastEast = 56.0, southNorth = 70.0, northNorth = 76.0),
        height = metersOf(feet = 9),
    )

    // straddles the swale so accumulated flow crosses its floor
    private fun pergolaRow() = EntityRow(
        entityName = "pergola",
        footprint = rectangleRing(westEast = 29.0, eastEast = 31.0, southNorth = 10.0, northNorth = 13.0),
        height = metersOf(feet = 8),
    )

    private fun gardenPathRow() = EntityRow(
        entityName = "garden path",
        footprint = listOf(GroundPoint(Meters(20.0), Meters(75.0)), GroundPoint(Meters(49.25), Meters(75.0))),
        height = Meters(0.0),
    )

    private fun rectangleRing(westEast: Double, eastEast: Double, southNorth: Double, northNorth: Double): List<GroundPoint> =
        listOf(
            GroundPoint(Meters(westEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(northNorth)),
            GroundPoint(Meters(westEast), Meters(northNorth)),
        )
}
