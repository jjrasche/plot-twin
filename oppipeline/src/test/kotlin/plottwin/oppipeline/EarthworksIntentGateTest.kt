package plottwin.oppipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.solvers.ToyPlotFixture
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EntityRow
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.LoggedRow
import plottwin.worldstate.Meters
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.OpStatus
import plottwin.worldstate.OpStatusRow
import plottwin.worldstate.OpVerb
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainDiffRow
import plottwin.worldstate.TerrainGrid
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole
import plottwin.worldstate.causeOpSeqOf
import plottwin.worldstate.projectCurrentState
import plottwin.worldstate.projectEarthworkLedger

class EarthworksIntentGateTest {

    @Test
    fun dig_here_carves_a_pond_proposal_and_hauls_the_spoil_off_by_default() {
        WorldLog.openInMemory().use { log ->
            stagePlotWithRegions(log)
            attachPipeline(log)

            val opSeq = log.append(digPondOp(), WriterRole.LLM)

            val state = log.currentState()
            assertResolved(log, opSeq)
            val proposal = Surface.Proposed(proposalSurfaceNameOf(opSeq))
            val measuredGrid = state.terrainOn(Surface.Measured)!!.grid
            val proposedGrid = state.terrainOn(proposal)!!.grid
            val pondCells = (0 until measuredGrid.cellCount).filter {
                proposedGrid.surfaceHeights[it] < measuredGrid.surfaceHeights[it]
            }
            assertTrue(measuredGrid.indexOf(120, 220) in pondCells, "the pond region's middle is dug")
            val bottomHeights = pondCells.map { proposedGrid.surfaceHeights[it] }.distinct()
            assertEquals(1, bottomHeights.size, "an excavated pond has one flat bottom, got $bottomHeights")
            val shallowestDugCell = pondCells.minOf { measuredGrid.surfaceHeights[it] }
            assertEquals(shallowestDugCell - POND_DEPTH_METERS.toFloat(), bottomHeights.single(), 1e-4f)

            val ledgerEntry = projectEarthworkLedger(log.readAll()).perOp.getValue(opSeq)
            assertTrue(ledgerEntry.bankCutCubicMeters > 0.0)
            assertEquals(0.0, ledgerEntry.looseSpoilPlacedCubicMeters)
            assertEquals(ledgerEntry.bankCutCubicMeters * (1.0 + ASSUMED_SWELL_FACTOR), ledgerEntry.haulOffCubicMeters, 1e-6)
            assertEquals(ledgerEntry.bankCutCubicMeters, dugVolumeOf(measuredGrid, proposedGrid), 1e-6)
            println("[intent-1 dig here] bankCut=${ledgerEntry.bankCutCubicMeters} loosePlaced=${ledgerEntry.looseSpoilPlacedCubicMeters} haulOff=${ledgerEntry.haulOffCubicMeters}")
        }
    }

    @Test
    fun foundation_this_footprint_cuts_the_greenhouse_pad_to_undisturbed_low_ground() {
        WorldLog.openInMemory().use { log ->
            stagePlotWithRegions(log)
            attachPipeline(log)

            val opSeq = log.append(
                OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "greenhouse", OpSlot.GROUND_FORM to "pad")),
                WriterRole.LLM,
            )

            val state = log.currentState()
            assertResolved(log, opSeq)
            val measuredGrid = state.terrainOn(Surface.Measured)!!.grid
            val proposedGrid = state.terrainOn(Surface.Proposed(proposalSurfaceNameOf(opSeq)))!!.grid
            val padCells = (0 until measuredGrid.cellCount).filter {
                measuredGrid.surfaceHeights[it] != proposedGrid.surfaceHeights[it]
            }
            assertTrue(padCells.isNotEmpty())
            val padHeights = padCells.map { proposedGrid.surfaceHeights[it] }.distinct()
            assertEquals(1, padHeights.size, "a pad is one level surface, got $padHeights")

            val ledgerEntry = projectEarthworkLedger(log.readAll()).perOp.getValue(opSeq)
            assertEquals(0.0, ledgerEntry.compactedFillCubicMeters)
            assertEquals(ledgerEntry.bankCutCubicMeters, dugVolumeOf(measuredGrid, proposedGrid), 1e-6)
            assertEquals(ledgerEntry.bankCutCubicMeters * (1.0 + ASSUMED_SWELL_FACTOR), ledgerEntry.haulOffCubicMeters, 1e-6)
            println("[intent-2 foundation] bankCut=${ledgerEntry.bankCutCubicMeters} compactedFill=${ledgerEntry.compactedFillCubicMeters} haulOff=${ledgerEntry.haulOffCubicMeters}")
        }
    }

    @Test
    fun berm_the_spoil_adjacent_places_the_loose_volume_and_hauls_nothing() {
        WorldLog.openInMemory().use { log ->
            stagePlotWithRegions(log)
            attachPipeline(log)

            val opSeq = log.append(digPondOp(spoilDestination = "spoil berm"), WriterRole.LLM)

            val state = log.currentState()
            assertResolved(log, opSeq)
            val measuredGrid = state.terrainOn(Surface.Measured)!!.grid
            val proposedGrid = state.terrainOn(Surface.Proposed(proposalSurfaceNameOf(opSeq)))!!.grid
            val bermCell = measuredGrid.indexOf(170, 220)
            assertTrue(proposedGrid.surfaceHeights[bermCell] > measuredGrid.surfaceHeights[bermCell], "the berm rises")

            val ledgerEntry = projectEarthworkLedger(log.readAll()).perOp.getValue(opSeq)
            assertEquals(0.0, ledgerEntry.haulOffCubicMeters)
            assertEquals(ledgerEntry.bankCutCubicMeters * (1.0 + ASSUMED_SWELL_FACTOR), ledgerEntry.looseSpoilPlacedCubicMeters, 1e-6)
            assertEquals(ledgerEntry.looseSpoilPlacedCubicMeters, bermVolumeOf(measuredGrid, proposedGrid), 0.05)
            println("[intent-3 berm adjacent] bankCut=${ledgerEntry.bankCutCubicMeters} loosePlaced=${ledgerEntry.looseSpoilPlacedCubicMeters} haulOff=${ledgerEntry.haulOffCubicMeters}")
        }
    }

    @Test
    fun proposal_differs_from_measured_only_where_the_diffs_say() {
        WorldLog.openInMemory().use { log ->
            stagePlotWithRegions(log)
            attachPipeline(log)

            val opSeq = log.append(digPondOp(spoilDestination = "spoil berm"), WriterRole.LLM)

            val state = log.currentState()
            val measuredGrid = state.terrainOn(Surface.Measured)!!.grid
            val proposedGrid = state.terrainOn(Surface.Proposed(proposalSurfaceNameOf(opSeq)))!!.grid
            val diffCells = diffCoveredCellsOf(log.readAll(), measuredGrid)
            for (cell in 0 until measuredGrid.cellCount) {
                if (cell in diffCells) continue
                assertEquals(
                    measuredGrid.surfaceHeights[cell],
                    proposedGrid.surfaceHeights[cell],
                    "cell $cell changed outside every diff region",
                )
            }
        }
    }

    @Test
    fun identical_log_replay_yields_identical_earthworks() {
        assertEquals(resolveBermedPondLog(), resolveBermedPondLog())
    }

    @Test
    fun replayed_projection_equals_the_live_projection() {
        WorldLog.openInMemory().use { log ->
            stagePlotWithRegions(log)
            attachPipeline(log)
            log.append(digPondOp(spoilDestination = "spoil berm"), WriterRole.LLM)
            assertEquals(log.currentState(), projectCurrentState(log.readAll()))
        }
    }

    private fun resolveBermedPondLog(): List<LoggedRow> = WorldLog.openInMemory().use { log ->
        stagePlotWithRegions(log)
        attachPipeline(log)
        log.append(digPondOp(spoilDestination = "spoil berm"), WriterRole.LLM)
        log.readAll()
    }

    private fun assertResolved(log: WorldLog, opSeq: Long) {
        val status = log.readAll().last { it.row is OpStatusRow && causeOpSeqOf(it) == opSeq }
        assertEquals(OpStatusRow(OpStatus.RESOLVED), status.row)
        assertTrue(log.currentState().pendingOps.isEmpty())
    }

    private fun stagePlotWithRegions(log: WorldLog) {
        ToyPlotFixture.appendToyPlot(log)
        log.append(EntityRow("pond region", rectangleRing(10.0, 14.0, 20.0, 24.0), Meters(0.0)), WriterRole.CAPTURE)
        log.append(EntityRow("spoil berm", rectangleRing(16.0, 20.0, 20.0, 24.0), Meters(0.0)), WriterRole.CAPTURE)
    }

    private fun attachPipeline(log: WorldLog) {
        OpPipeline(log = log, date = ToyPlotFixture.toyDate, surface = Surface.Measured, constraints = emptyList()).attach()
    }

    private fun digPondOp(spoilDestination: String? = null): OpRow {
        val namedSlots = mutableMapOf(OpSlot.SUBJECT to "pond region", OpSlot.GROUND_FORM to "pond")
        if (spoilDestination != null) namedSlots[OpSlot.SPOIL_DESTINATION] = spoilDestination
        return OpRow(OpVerb.REGRADE, namedSlots)
    }

    private fun dugVolumeOf(measured: TerrainGrid, proposed: TerrainGrid): Double =
        movedVolumeOf(measured, proposed) { before, after -> before - after }

    private fun bermVolumeOf(measured: TerrainGrid, proposed: TerrainGrid): Double =
        movedVolumeOf(measured, proposed) { before, after -> after - before }

    private fun movedVolumeOf(measured: TerrainGrid, proposed: TerrainGrid, moved: (Double, Double) -> Double): Double {
        val cellArea = measured.cellSize.value * measured.cellSize.value
        var volume = 0.0
        for (cell in 0 until measured.cellCount) {
            val movedDepth = moved(measured.surfaceHeights[cell].toDouble(), proposed.surfaceHeights[cell].toDouble())
            if (movedDepth > 0.0) volume += movedDepth * cellArea
        }
        return volume
    }

    private fun diffCoveredCellsOf(history: List<LoggedRow>, grid: TerrainGrid): Set<Int> =
        history.map { it.row }.filterIsInstance<TerrainDiffRow>().flatMap { diff ->
            (0 until diff.rows).flatMap { row ->
                (0 until diff.columns).map { column -> grid.indexOf(diff.firstColumn + column, diff.firstRow + row) }
            }
        }.toSet()

    private fun rectangleRing(westEast: Double, eastEast: Double, southNorth: Double, northNorth: Double): List<GroundPoint> =
        listOf(
            GroundPoint(Meters(westEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(southNorth)),
            GroundPoint(Meters(eastEast), Meters(northNorth)),
            GroundPoint(Meters(westEast), Meters(northNorth)),
        )
}
