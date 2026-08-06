package plottwin.worldstate

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

const val METERS_PER_FOOT = 0.3048
const val METERS_PER_INCH = 0.0254
const val INCHES_PER_FOOT = 12

@Serializable
@JvmInline
value class Meters(val value: Double)

fun metersOf(feet: Int, inches: Double = 0.0): Meters =
    Meters(feet * METERS_PER_FOOT + inches * METERS_PER_INCH)

fun Meters.toFeetInchesText(): String {
    val wholeInches = (value / METERS_PER_INCH).roundToInt()
    val feet = wholeInches / INCHES_PER_FOOT
    val inches = wholeInches % INCHES_PER_FOOT
    return "$feet' $inches\""
}
