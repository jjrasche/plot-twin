package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import plottwin.worldstate.GridExtent
import plottwin.worldstate.Meters
import plottwin.worldstate.WorldLog
import plottwin.worldstate.gridExtentOf

private const val SQUARE_METERS_PER_ACRE = 4046.8564224
private const val MASK_AREA_AGREEMENT_SHARE = 0.01
private const val TEN_CENTIMETRE_CELL = 0.1

class CompiledParcelCacheGateTest {

    @Test
    fun the_ten_centimetre_cut_is_the_property_lines_bounding_box_and_its_mask_measures_the_acreage() {
        val parcel = readCompiledParcel(CaptureCache.compiledParcel())
        WorldLog.openInMemory().use { log ->
            appendParcelBoundary(log, RealParcelFixture.boundary())
            appendRealParcel(log, parcel)
            val state = log.currentState()
            val boundary = assertNotNull(state.parcelBoundary)
            val mask = assertNotNull(state.parcelMask)
            val statedSquareMeters = boundary.acresStated * SQUARE_METERS_PER_ACRE
            val disagreement = kotlin.math.abs(mask.insideAreaSquareMeters / statedSquareMeters - 1.0)
            println(
                "[10cm] %d x %d = %d cells, %d inside the line = %.1f m2 vs %.1f m2 county-stated, %+.3f%%".format(
                    parcel.columns,
                    parcel.rows,
                    parcel.columns * parcel.rows,
                    mask.insideCellCount,
                    mask.insideAreaSquareMeters,
                    statedSquareMeters,
                    100.0 * (mask.insideAreaSquareMeters / statedSquareMeters - 1.0),
                )
            )
            assertEquals(TEN_CENTIMETRE_CELL, parcel.cellSizeMeters)
            assertEquals(gridExtentOf(boundary, Meters(parcel.cellSizeMeters)), GridExtent(parcel.columns, parcel.rows))
            assertTrue(disagreement < MASK_AREA_AGREEMENT_SHARE, "the 10cm mask measures ${100.0 * disagreement}% off the county's acreage")
        }
    }
}
