package plottwin.worldstate

import kotlin.math.abs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class FactorProvenance { SITE_TEST, ASSUMED }

@Serializable
@SerialName("earthwork")
data class EarthworkRow(
    val surfaceName: String,
    val bankCutCubicMeters: Double,
    val compactedFillCubicMeters: Double,
    val looseSpoilPlacedCubicMeters: Double,
    val haulOffCubicMeters: Double,
    val topsoilStrippedCubicMeters: Double,
    val topsoilRespreadCubicMeters: Double,
    val swellFactor: Double,
    val shrinkFactor: Double,
    val factorProvenance: FactorProvenance,
) : WorldRow

const val CONSERVATION_TOLERANCE_CUBIC_METERS = 1e-6

// Q-005 hard invariant in loose measure; on-site reuse counts at its bank equivalent compacted/(1-shrink), Peurifoy eqs. 2-4..2-9 (KSU CE417 ch. 2)
fun conservationImbalanceOf(earthwork: EarthworkRow): Double {
    val looseFromCut = earthwork.bankCutCubicMeters * (1.0 + earthwork.swellFactor)
    val bankReusedAsFill = earthwork.compactedFillCubicMeters / (1.0 - earthwork.shrinkFactor)
    val looseEquivalentOfFill = bankReusedAsFill * (1.0 + earthwork.swellFactor)
    return looseFromCut - looseEquivalentOfFill - earthwork.looseSpoilPlacedCubicMeters - earthwork.haulOffCubicMeters
}

fun isConserved(earthwork: EarthworkRow): Boolean =
    abs(conservationImbalanceOf(earthwork)) <= CONSERVATION_TOLERANCE_CUBIC_METERS

class EarthworkNotConserved(earthwork: EarthworkRow) :
    IllegalArgumentException(
        "earthwork on ${earthwork.surfaceName} does not conserve spoil: " +
            "imbalance ${conservationImbalanceOf(earthwork)} m^3 loose"
    )
