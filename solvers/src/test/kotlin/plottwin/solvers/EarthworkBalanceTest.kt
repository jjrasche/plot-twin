package plottwin.solvers

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import plottwin.worldstate.CurrentState
import plottwin.worldstate.EarthworkRow
import plottwin.worldstate.FactorProvenance
import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.OpRow
import plottwin.worldstate.OpSlot
import plottwin.worldstate.OpVerb
import plottwin.worldstate.RawElevation
import plottwin.worldstate.RefRole
import plottwin.worldstate.RowRef
import plottwin.worldstate.Meters
import plottwin.worldstate.Surface
import plottwin.worldstate.TerrainDiffRow
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole
import plottwin.worldstate.encodeHeightsBase64

private val anyDay: LocalDate = LocalDate.of(2026, 8, 8)

class EarthworkBalanceTest {

    @Test
    fun hauling_more_than_the_rule_allows_is_a_violation_at_the_dug_cells() {
        val world = SolverWorld(stateWithHaul(haulOffCubicMeters = 13.0), anyDay, Surface.Measured)
        val nearBalance = EarthworkBalanceConstraint("earthwork-balance", maxHaulOffCubicMeters = 5.0)

        val violation = runSolvers(world, listOf(nearBalance)).single()

        assertEquals("earthwork-balance", violation.ruleName)
        assertEquals(8.0, violation.magnitude, 1e-9)
        assertEquals(0.20, violation.location.east.value, 1e-9)
        assertEquals(0.15, violation.location.north.value, 1e-9)
    }

    @Test
    fun hauling_within_the_allowance_raises_no_violation() {
        val world = SolverWorld(stateWithHaul(haulOffCubicMeters = 13.0), anyDay, Surface.Measured)
        val generousAllowance = EarthworkBalanceConstraint("earthwork-balance", maxHaulOffCubicMeters = 20.0)

        assertTrue(runSolvers(world, listOf(generousAllowance)).isEmpty())
    }

    private fun stateWithHaul(haulOffCubicMeters: Double): CurrentState = WorldLog.openInMemory().use { log ->
        val flatGround = RawElevation(4, 3, Meters(0.1), FloatArray(12))
        val baseSeq = log.append(GriddedElevationOperator.compileBaseTerrain(flatGround), WriterRole.CAPTURE)
        val opSeq = log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "pit")), WriterRole.LLM)
        val causedBy = listOf(RowRef(RefRole.OP, opSeq))
        log.append(pitDiff(baseSeq, opSeq), WriterRole.OPTIMIZER, causedBy)
        log.append(pitEarthwork(opSeq, haulOffCubicMeters), WriterRole.OPTIMIZER, causedBy)
        log.currentState()
    }

    private fun pitDiff(baseSeq: Long, opSeq: Long) = TerrainDiffRow(
        firstColumn = 1,
        firstRow = 1,
        columns = 2,
        rows = 1,
        heightsBase64 = encodeHeightsBase64(floatArrayOf(-1.0f, -1.0f)),
        surface = Surface.Proposed("regrade-$opSeq"),
        branchedFromSeq = baseSeq,
    )

    private fun pitEarthwork(opSeq: Long, haulOffCubicMeters: Double) = EarthworkRow(
        surfaceName = "regrade-$opSeq",
        bankCutCubicMeters = haulOffCubicMeters / 1.3,
        compactedFillCubicMeters = 0.0,
        looseSpoilPlacedCubicMeters = 0.0,
        haulOffCubicMeters = haulOffCubicMeters,
        topsoilStrippedCubicMeters = 0.0,
        topsoilRespreadCubicMeters = 0.0,
        swellFactor = 0.30,
        shrinkFactor = 0.15,
        factorProvenance = FactorProvenance.ASSUMED,
    )
}
