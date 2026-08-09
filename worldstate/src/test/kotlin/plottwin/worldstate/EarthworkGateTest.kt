package plottwin.worldstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EarthworkGateTest {

    @Test
    fun a_hand_built_unbalanced_earthwork_never_enters_the_log() {
        WorldLog.openInMemory().use { log ->
            val vanishingSpoil = balancedPondEarthwork().copy(haulOffCubicMeters = 0.0)
            val rejected = assertFailsWith<EarthworkNotConserved> { log.append(vanishingSpoil, WriterRole.OPTIMIZER) }
            assertTrue("13.0" in rejected.message!!, "the violation names the imbalance: ${rejected.message}")
            assertTrue(log.readAll().isEmpty())
        }
    }

    @Test
    fun the_balanced_earthwork_row_appends() {
        WorldLog.openInMemory().use { log ->
            log.append(balancedPondEarthwork(), WriterRole.OPTIMIZER)
            assertEquals(1, log.currentState().earthworks.size)
        }
    }

    @Test
    fun haul_off_is_the_named_escape_that_closes_an_otherwise_unplaceable_dig() {
        assertEquals(0.0, conservationImbalanceOf(balancedPondEarthwork()), 1e-9)
        assertTrue(!isConserved(balancedPondEarthwork().copy(haulOffCubicMeters = 0.0)))
    }

    @Test
    fun on_site_reuse_counts_at_its_bank_equivalent() {
        val padCutReusedAsFill = EarthworkRow(
            surfaceName = "regrade-9",
            bankCutCubicMeters = 10.0,
            compactedFillCubicMeters = 8.5,
            looseSpoilPlacedCubicMeters = 0.0,
            haulOffCubicMeters = 0.0,
            topsoilStrippedCubicMeters = 0.0,
            topsoilRespreadCubicMeters = 0.0,
            swellFactor = 0.30,
            shrinkFactor = 0.15,
            factorProvenance = FactorProvenance.ASSUMED,
        )
        assertEquals(0.0, conservationImbalanceOf(padCutReusedAsFill), 1e-9)
    }

    @Test
    fun the_ledger_projects_per_op_and_per_plot_totals() {
        WorldLog.openInMemory().use { log ->
            val digOpSeq = log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "pond region")), WriterRole.LLM)
            log.append(balancedPondEarthwork(), WriterRole.OPTIMIZER, listOf(RowRef(RefRole.OP, digOpSeq)))
            val padOpSeq = log.append(OpRow(OpVerb.REGRADE, mapOf(OpSlot.SUBJECT to "shed pad")), WriterRole.LLM)
            log.append(
                balancedPondEarthwork().copy(surfaceName = "regrade-$padOpSeq", bankCutCubicMeters = 5.0, haulOffCubicMeters = 6.5),
                WriterRole.OPTIMIZER,
                listOf(RowRef(RefRole.OP, padOpSeq)),
            )

            val ledger = projectEarthworkLedger(log.readAll())

            assertEquals(10.0, ledger.perOp.getValue(digOpSeq).bankCutCubicMeters)
            assertEquals(5.0, ledger.perOp.getValue(padOpSeq).bankCutCubicMeters)
            assertEquals(15.0, ledger.plot.bankCutCubicMeters)
            assertEquals(19.5, ledger.plot.haulOffCubicMeters)
        }
    }

    private fun balancedPondEarthwork() = EarthworkRow(
        surfaceName = "regrade-1",
        bankCutCubicMeters = 10.0,
        compactedFillCubicMeters = 0.0,
        looseSpoilPlacedCubicMeters = 0.0,
        haulOffCubicMeters = 13.0,
        topsoilStrippedCubicMeters = 0.0,
        topsoilRespreadCubicMeters = 0.0,
        swellFactor = 0.30,
        shrinkFactor = 0.15,
        factorProvenance = FactorProvenance.ASSUMED,
    )
}
