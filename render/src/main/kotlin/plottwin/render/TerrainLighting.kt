package plottwin.render

import kotlin.math.sqrt
import plottwin.solvers.occluderSurfaceOf
import plottwin.solvers.skyOpennessOf
import plottwin.solvers.sunlitFractionsAt
import plottwin.worldstate.CurrentState

class TerrainShading(
    val sunlitFractions: FloatArray,
    val skyOpenness: FloatArray,
    val daylight: Daylight,
)

// The renderer sweeps at the solver's resolution and averages down, so a render cell
// straddling a shadow edge carries the fraction of it that is lit.
fun terrainShadingFor(state: CurrentState, daylight: Daylight, downsampleFactor: Int): TerrainShading {
    val surface = requireNotNull(occluderSurfaceOf(state)) { "shading needs a base-terrain row" }
    return TerrainShading(
        sunlitFractions = averageDown(sunlitFractionsAt(surface, daylight.sun), surface.columns, surface.rows, downsampleFactor),
        skyOpenness = averageDown(skyOpennessOf(surface), surface.columns, surface.rows, downsampleFactor),
        daylight = daylight,
    )
}

fun averageDown(field: FloatArray, columns: Int, rows: Int, factor: Int): FloatArray {
    if (factor == 1) return field
    val coarseColumns = (columns + factor - 1) / factor
    val coarseRows = (rows + factor - 1) / factor
    val averaged = FloatArray(coarseColumns * coarseRows)
    for (coarseRow in 0 until coarseRows) {
        for (coarseColumn in 0 until coarseColumns) {
            var total = 0f
            var counted = 0
            for (row in coarseRow * factor until minOf((coarseRow + 1) * factor, rows)) {
                for (column in coarseColumn * factor until minOf((coarseColumn + 1) * factor, columns)) {
                    total += field[row * columns + column]
                    counted++
                }
            }
            averaged[coarseRow * coarseColumns + coarseColumn] = if (counted == 0) 0f else total / counted
        }
    }
    return averaged
}

// outdoor daylight never reads true black: haze and multiple bounce keep a floor under
// every surface, so the darkest woods interior stays legible instead of clipping to void
const val BLACK_POINT_LIFT = 0.025f

fun litColor(albedo: Rgb, normal: SceneDirection, sunlitFraction: Float, skyOpenness: Float, daylight: Daylight): Rgb {
    val lambert = lambertOf(normal, daylight.sunDirection)
    val received = daylight.sunlight * (lambert * sunlitFraction) + daylight.skylight * skyOpenness
    return Rgb(
        red = liftedChannel(albedo.red * received.red),
        green = liftedChannel(albedo.green * received.green),
        blue = liftedChannel(albedo.blue * received.blue),
    )
}

private fun liftedChannel(channel: Float): Float =
    BLACK_POINT_LIFT + channel.coerceIn(0f, 1f) * (1f - BLACK_POINT_LIFT)

fun lambertOf(normal: SceneDirection, sunDirection: SceneDirection): Float =
    (normal.east * sunDirection.east + normal.up * sunDirection.up + normal.north * sunDirection.north)
        .coerceAtLeast(0f)

fun upwardTriangleNormalOf(
    aEast: Float, aUp: Float, aNorth: Float,
    bEast: Float, bUp: Float, bNorth: Float,
    cEast: Float, cUp: Float, cNorth: Float,
): SceneDirection {
    val firstEast = bEast - aEast
    val firstUp = bUp - aUp
    val firstNorth = bNorth - aNorth
    val secondEast = cEast - aEast
    val secondUp = cUp - aUp
    val secondNorth = cNorth - aNorth
    val east = firstUp * secondNorth - firstNorth * secondUp
    val up = firstNorth * secondEast - firstEast * secondNorth
    val north = firstEast * secondUp - firstUp * secondEast
    val length = sqrt(east * east + up * up + north * north)
    if (length < 1e-9f) return SceneDirection(0f, 1f, 0f)
    val upward = if (up < 0f) -1f else 1f
    return SceneDirection(east / length * upward, up / length * upward, north / length * upward)
}

fun hexOf(color: Rgb): String =
    channelHex((color.red * 255f).toInt()) + channelHex((color.green * 255f).toInt()) + channelHex((color.blue * 255f).toInt())

fun rgbOfHex(hex: String): Rgb = Rgb(
    red = hex.substring(0, 2).toInt(16) / 255f,
    green = hex.substring(2, 4).toInt(16) / 255f,
    blue = hex.substring(4, 6).toInt(16) / 255f,
)
