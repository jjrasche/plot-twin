package plottwin.render

import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import plottwin.solvers.SunRay
import plottwin.solvers.sunRayAt
import plottwin.worldstate.CurrentState
import plottwin.worldstate.SiteRow

const val PEAK_SUN_STRENGTH = 0.75f
const val SKY_STRENGTH = 0.38f
const val AIR_MASS_EXPONENT = 0.25f
const val HIGH_SUN_DEGREES = 55.0

data class Rgb(val red: Float, val green: Float, val blue: Float) {
    operator fun times(scale: Float): Rgb = Rgb(red * scale, green * scale, blue * scale)

    operator fun plus(other: Rgb): Rgb = Rgb(red + other.red, green + other.green, blue + other.blue)
}

private val LOW_SUN_TINT = Rgb(1.00f, 0.55f, 0.28f)
private val HIGH_SUN_TINT = Rgb(1.00f, 0.96f, 0.90f)
private val LOW_SKY_TINT = Rgb(0.34f, 0.31f, 0.36f)
private val HIGH_SKY_TINT = Rgb(0.48f, 0.60f, 0.86f)
private val LOW_HAZE_TINT = Rgb(0.62f, 0.42f, 0.32f)
private val HIGH_HAZE_TINT = Rgb(0.72f, 0.80f, 0.90f)
private val LOW_ZENITH_TINT = Rgb(0.14f, 0.15f, 0.28f)
private val HIGH_ZENITH_TINT = Rgb(0.24f, 0.42f, 0.78f)

// v1 sky: a sun-altitude gradient, not Hosek-Wilkie. See DECISIONS.md D-015 for why.
class Daylight(val moment: ZonedDateTime, val sun: SunRay) {
    private val highness: Float = (sun.altitudeDegrees / HIGH_SUN_DEGREES).coerceIn(0.0, 1.0).toFloat()
    private val airMass: Float =
        if (sun.isAboveHorizon) sin(Math.toRadians(sun.altitudeDegrees)).toFloat().pow(AIR_MASS_EXPONENT) else 0f

    val sunlight: Rgb = mix(LOW_SUN_TINT, HIGH_SUN_TINT, highness) * (PEAK_SUN_STRENGTH * airMass)
    val skylight: Rgb = mix(LOW_SKY_TINT, HIGH_SKY_TINT, highness) * SKY_STRENGTH
    val horizonTint: Rgb = mix(LOW_HAZE_TINT, HIGH_HAZE_TINT, highness)
    val zenithTint: Rgb = mix(LOW_ZENITH_TINT, HIGH_ZENITH_TINT, highness)
    val sunGlow: Rgb = mix(LOW_SUN_TINT, HIGH_SUN_TINT, highness) * (0.35f + 0.30f * airMass)

    val sunDirection: SceneDirection = sceneDirectionOf(sun)
}

data class SceneDirection(val east: Float, val up: Float, val north: Float)

fun daylightAt(site: SiteRow, moment: ZonedDateTime): Daylight = Daylight(moment, sunRayAt(site, moment))

fun daylightOverPlot(state: CurrentState, moment: ZonedDateTime): Daylight =
    daylightAt(requireNotNull(state.site) { "lighting a plot needs a site row" }, moment)

private fun sceneDirectionOf(sun: SunRay): SceneDirection {
    val azimuth = Math.toRadians(sun.azimuthDegrees)
    val altitude = Math.toRadians(sun.altitudeDegrees)
    return SceneDirection(
        east = (sin(azimuth) * cos(altitude)).toFloat(),
        up = sin(altitude).toFloat(),
        north = (cos(azimuth) * cos(altitude)).toFloat(),
    )
}

private fun mix(low: Rgb, high: Rgb, towardHigh: Float): Rgb = Rgb(
    red = low.red + (high.red - low.red) * towardHigh,
    green = low.green + (high.green - low.green) * towardHigh,
    blue = low.blue + (high.blue - low.blue) * towardHigh,
)
