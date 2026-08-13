package plottwin.eyes

import ai.factoredui.compose.math.Vec3
import java.awt.image.BufferedImage
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan
import plottwin.geometry.isInsidePolygon
import plottwin.render.SceneFrame
import plottwin.render.groundHeightAt
import plottwin.render.sceneFrameOf
import plottwin.solvers.SunRay
import plottwin.worldstate.CurrentState
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.TerrainGrid

const val SHADOW_GROUND_SAMPLE_METERS = 0.5
const val SHADOW_RAY_MAXIMUM_METERS = 200.0
const val SHADOW_RAY_MINIMUM_ALTITUDE_DEGREES = 1.0

data class ShadowedGroundPoint(val caster: String, val scenePoint: Vec3)

// Which occluder owns each patch of shade, read off the state and the sun alone: the first
// body the ray meets on its way to the sun is the body casting that patch. Pose-independent,
// so a scene is attributed once and then looked at from every viewpoint.
fun shadowedGroundOf(
    state: CurrentState,
    sun: SunRay,
    sampleMeters: Double = SHADOW_GROUND_SAMPLE_METERS,
): List<ShadowedGroundPoint> {
    val terrain = state.terrain?.grid ?: return emptyList()
    val casters = castersOf(state)
    if (casters.isEmpty()) return emptyList()
    val frame = sceneFrameOf(terrain)
    val towardSunEast = sin(Math.toRadians(sun.azimuthDegrees))
    val towardSunNorth = cos(Math.toRadians(sun.azimuthDegrees))
    val rise = tan(Math.toRadians(maxOf(sun.altitudeDegrees, SHADOW_RAY_MINIMUM_ALTITUDE_DEGREES)))
    val reach = minOf(casters.maxOf { it.heightMeters } / rise, SHADOW_RAY_MAXIMUM_METERS)
    val shadowed = ArrayList<ShadowedGroundPoint>()
    forEachGroundSample(terrain, sampleMeters) { standing ->
        val caster = firstCasterAlongRay(terrain, casters, standing, towardSunEast, towardSunNorth, rise, reach, sampleMeters)
        if (caster != null) shadowed.add(ShadowedGroundPoint(caster, scenePointOf(terrain, frame, standing)))
    }
    return shadowed
}

data class CasterShare(val caster: String, val share: Double)

data class CasterPopulation(
    val assumedCaster: String?,
    val assumedShare: Double,
    val casterCount: Int,
    val shadowSamples: Int,
    val shares: List<CasterShare>,
) {
    fun stated(): String =
        "%d casters shade this annulus, %s holds %.3f of %d shaded samples".format(
            casterCount,
            assumedCaster ?: "no caster",
            assumedShare,
            shadowSamples,
        )

    companion object {
        val NONE = CasterPopulation(null, 0.0, 0, 0, emptyList())
    }
}

// The check reads one screen annulus, so the population is counted where the check looks:
// shaded ground that projects into that ring, past the same sky and caster masks.
fun casterPopulationInView(
    shadowedGround: List<ShadowedGroundPoint>,
    assumedCaster: String?,
    projector: ScreenProjector,
    image: BufferedImage,
    centerX: Double,
    centerY: Double,
    innerRadius: Double,
    outerRadius: Double,
    casterMask: BooleanArray? = null,
    skyPixel: ((Int) -> Boolean)? = null,
): CasterPopulation {
    val samplesByCaster = HashMap<String, Int>()
    for (point in shadowedGround) {
        val projected = projector.project(point.scenePoint)
        if (!projected.visible) continue
        val column = projected.x.toInt()
        val row = projected.y.toInt()
        if (column < 0 || row < 0 || column >= image.width || row >= image.height) continue
        val reach = hypot(column - centerX, row - centerY)
        if (reach < innerRadius || reach > outerRadius) continue
        val argb = image.getRGB(column, row)
        if (argb == BACKGROUND_ARGB) continue
        if (skyPixel != null && skyPixel(argb)) continue
        if (casterMask != null && casterMask[row * image.width + column]) continue
        samplesByCaster[point.caster] = (samplesByCaster[point.caster] ?: 0) + 1
    }
    val shadowSamples = samplesByCaster.values.sum()
    if (shadowSamples == 0) return CasterPopulation.NONE
    val shares = samplesByCaster.entries
        .map { (caster, samples) -> CasterShare(caster, samples.toDouble() / shadowSamples) }
        .sortedWith(compareByDescending<CasterShare> { it.share }.thenBy { it.caster })
    return CasterPopulation(
        assumedCaster = assumedCaster,
        assumedShare = shares.firstOrNull { it.caster == assumedCaster }?.share ?: 0.0,
        casterCount = shares.size,
        shadowSamples = shadowSamples,
        shares = shares,
    )
}

private class ShadowCaster(val name: String, val footprint: List<GroundPoint>, val heightMeters: Double) {
    val westEdge = footprint.minOf { it.east.value }
    val eastEdge = footprint.maxOf { it.east.value }
    val southEdge = footprint.minOf { it.north.value }
    val northEdge = footprint.maxOf { it.north.value }

    fun mayContain(point: GroundPoint): Boolean =
        point.east.value >= westEdge && point.east.value <= eastEdge &&
            point.north.value >= southEdge && point.north.value <= northEdge
}

private fun castersOf(state: CurrentState): List<ShadowCaster> =
    state.entities.entries
        .filter { (_, placed) -> isShadowCaster(placed) }
        .sortedBy { (name, _) -> name }
        .map { (name, placed) -> ShadowCaster(name, placed.footprint, placed.height.value) }

private fun isShadowCaster(placed: PlacedEntity): Boolean =
    placed.footprint.size >= 3 && placed.height.value > 0.0

private fun firstCasterAlongRay(
    terrain: TerrainGrid,
    casters: List<ShadowCaster>,
    standing: GroundPoint,
    towardSunEast: Double,
    towardSunNorth: Double,
    rise: Double,
    reach: Double,
    stepMeters: Double,
): String? {
    val standingHeight = groundHeightAt(terrain, standing)
    var along = stepMeters
    while (along <= reach) {
        val probe = GroundPoint(
            east = Meters(standing.east.value + along * towardSunEast),
            north = Meters(standing.north.value + along * towardSunNorth),
        )
        val rayHeight = standingHeight + along * rise
        for (caster in casters) {
            if (!caster.mayContain(probe)) continue
            if (!isInsidePolygon(probe, caster.footprint)) continue
            if (groundHeightAt(terrain, probe) + caster.heightMeters > rayHeight) return caster.name
        }
        along += stepMeters
    }
    return null
}

private fun forEachGroundSample(terrain: TerrainGrid, sampleMeters: Double, visit: (GroundPoint) -> Unit) {
    val eastSpan = terrain.columns * terrain.cellSize.value
    val northSpan = terrain.rows * terrain.cellSize.value
    val eastSamples = floor(eastSpan / sampleMeters).toInt()
    val northSamples = floor(northSpan / sampleMeters).toInt()
    for (northStep in 0 until northSamples) {
        for (eastStep in 0 until eastSamples) {
            visit(
                GroundPoint(
                    east = Meters((eastStep + 0.5) * sampleMeters),
                    north = Meters((northStep + 0.5) * sampleMeters),
                ),
            )
        }
    }
}

private fun scenePointOf(terrain: TerrainGrid, frame: SceneFrame, standing: GroundPoint): Vec3 = Vec3(
    frame.sceneX(standing.east.value),
    groundHeightAt(terrain, standing),
    frame.sceneZ(standing.north.value),
)
