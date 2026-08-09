package plottwin.capture

import plottwin.worldstate.GriddedElevationOperator
import plottwin.worldstate.WorldLog
import plottwin.worldstate.WriterRole

data class RealParcelSeqs(val baseTerrainSeq: Long, val siteSeq: Long)

// Nothing lives outside the log: the compiled parcel enters as a base-terrain row plus a
// site row, both under the CAPTURE writer (D-011, D-013, D-017).
fun appendRealParcel(log: WorldLog, parcel: CompiledParcel): RealParcelSeqs {
    val baseTerrainSeq = log.append(GriddedElevationOperator.compileBaseTerrain(rawElevationOf(parcel)), WriterRole.CAPTURE)
    val siteSeq = log.append(siteRowOf(parcel), WriterRole.CAPTURE)
    return RealParcelSeqs(baseTerrainSeq, siteSeq)
}
