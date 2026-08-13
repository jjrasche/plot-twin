package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.TerrainGrid

const val SURROUND_ENTITY_ID = "surround"
const val SURROUND_SPOKE_CELLS = 96
const val SURROUND_RING_CELLS = 28
const val SURROUND_GROUND_BLEND_RINGS = 3
val SURROUND_ALBEDO = Rgb(0.34f, 0.35f, 0.32f)

// The neighbours' land drawn as what it is TO THIS PLOT: flat, untextured, unshadowed ground
// that carries a horizon, so the parcel stops reading as a cut-out slab hanging in sky. It is
// not state - no row describes it - and its inner boundary IS the property line, so it never
// overlaps the parcel, can never be mistaken for it, and is never measured as part of it. Its
// rim stands on the sky dome's own radius at the same ground datum, so the ground ends exactly
// where the sky's horizon begins and there is no seam to see.
fun neutralSurroundMeshOf(
    ring: List<GroundPoint>,
    terrain: TerrainGrid,
    frame: SceneFrame,
    daylight: Daylight,
): Scene3dMesh {
    val centre = plotCentreOf(terrain)
    val datum = groundDatumOf(terrain)
    val spokes = List(SURROUND_SPOKE_CELLS + 1) { spokeVertex ->
        surroundSpokeOf(ring, centre, terrain, 2.0 * Math.PI * spokeVertex / SURROUND_SPOKE_CELLS)
    }
    val rim = skyDomeRadiusOf(terrain).toDouble()
    val vertices = ArrayList<Float>((SURROUND_RING_CELLS + 1) * spokes.size * 3)
    for (ringVertex in 0..SURROUND_RING_CELLS) {
        for (spoke in spokes) {
            val point = spoke.pointAt(centre, reachAt(spoke.innerReach, rim, ringVertex))
            vertices.add(frame.sceneX(point.east.value))
            vertices.add(surroundHeightAt(spoke.innerHeight, datum, ringVertex))
            vertices.add(frame.sceneZ(point.north.value))
        }
    }
    return Scene3dMesh(
        vertices = vertices,
        triColors = surroundColorsOf(daylight),
        gridCellsX = SURROUND_SPOKE_CELLS,
        gridCellsZ = SURROUND_RING_CELLS,
    )
}

// One datum for the ground the sky sits on: the dome's horizon and the surround's rim share it,
// or the pale band of the horizon lands hundreds of metres under the plot.
fun groundDatumOf(terrain: TerrainGrid): Float = terrain.surfaceHeights.average().toFloat()

private class SurroundSpoke(
    val east: Double,
    val north: Double,
    val innerReach: Double,
    val innerHeight: Float,
) {
    fun pointAt(centre: GroundPoint, reach: Double): GroundPoint = GroundPoint(
        east = Meters(centre.east.value + east * reach),
        north = Meters(centre.north.value + north * reach),
    )
}

private fun surroundSpokeOf(
    ring: List<GroundPoint>,
    centre: GroundPoint,
    terrain: TerrainGrid,
    azimuth: Double,
): SurroundSpoke {
    val east = sin(azimuth)
    val north = cos(azimuth)
    val reach = ringExitReachOf(ring, centre, east, north)
    val exit = GroundPoint(
        east = Meters(centre.east.value + east * reach),
        north = Meters(centre.north.value + north * reach),
    )
    return SurroundSpoke(east, north, reach, groundHeightAt(terrain, exit))
}

// One exit per ray is the same assumption the clipped ground mesh makes: true of every convex
// parcel, and a ring that breaks it says so here instead of leaving a wedge of the plot bare.
fun ringExitReachOf(ring: List<GroundPoint>, centre: GroundPoint, east: Double, north: Double): Double {
    val reaches = ArrayList<Double>(1)
    for (vertex in ring.indices) {
        val from = ring[vertex]
        val to = ring[(vertex + 1) % ring.size]
        val edgeEast = to.east.value - from.east.value
        val edgeNorth = to.north.value - from.north.value
        val denominator = east * edgeNorth - north * edgeEast
        if (kotlin.math.abs(denominator) < 1e-12) continue
        val towardEast = from.east.value - centre.east.value
        val towardNorth = from.north.value - centre.north.value
        val alongEdge = (towardEast * north - towardNorth * east) / denominator
        val alongRay = (towardEast * edgeNorth - towardNorth * edgeEast) / denominator
        if (alongEdge < 0.0 || alongEdge >= 1.0 || alongRay <= 0.0) continue
        reaches.add(alongRay)
    }
    require(reaches.size == 1) {
        "the surround needs one property-line exit per spoke; this ray leaves the ring ${reaches.size} times"
    }
    return reaches.single()
}

private fun plotCentreOf(terrain: TerrainGrid): GroundPoint = GroundPoint(
    east = Meters(terrain.columns * terrain.cellSize.value / 2.0),
    north = Meters(terrain.rows * terrain.cellSize.value / 2.0),
)

// rings step outward geometrically, so the cells nearest the line stay small enough to read as
// ground while the rim still reaches the dome
private fun reachAt(innerReach: Double, rim: Double, ringVertex: Int): Double =
    innerReach * (rim / innerReach).pow(ringVertex.toDouble() / SURROUND_RING_CELLS)

// the surround leaves the line at the parcel's own edge height and flattens to the datum, so the
// boundary carries no step for the eye to read as a plinth
private fun surroundHeightAt(innerHeight: Float, datum: Float, ringVertex: Int): Float {
    val towardDatum = (ringVertex.toFloat() / SURROUND_GROUND_BLEND_RINGS).coerceAtMost(1f)
    return innerHeight + (datum - innerHeight) * towardDatum
}

// Haze, baked radially: every pose looks at the plot, so distance from the plot is distance from
// the eye. The rim resolves to exactly the horizon tint, which is also the scene background, so
// the ground dissolves into the sky rather than ending at a visible edge.
private fun surroundColorsOf(daylight: Daylight): List<String> {
    val lit = litColor(SURROUND_ALBEDO, SceneDirection(0f, 1f, 0f), sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight)
    val ringColors = List(SURROUND_RING_CELLS) { ring ->
        hexOf(hazedToward(lit, daylight.horizonTint, (ring + 1).toFloat() / SURROUND_RING_CELLS))
    }
    return ringColors.flatMap { color -> List(SURROUND_SPOKE_CELLS * 2) { color } }
}

private fun hazedToward(near: Rgb, horizon: Rgb, haze: Float): Rgb = Rgb(
    red = near.red + (horizon.red - near.red) * haze,
    green = near.green + (horizon.green - near.green) * haze,
    blue = near.blue + (horizon.blue - near.blue) * haze,
)
