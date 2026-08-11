package plottwin.render

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.TerrainGrid

// D-007: a tree is a trunk cylinder + canopy ellipsoid. The entity row carries the crown
// circle footprint and full height; the split ratios below are the render-side shape.
const val TRUNK_HEIGHT_SHARE = 0.35f
const val TRUNK_RADIUS_SHARE = 0.12
const val TRUNK_SIDES = 8
const val CANOPY_STACKS = 4
const val CANOPY_SLICES = 8
const val WATER_LIFT_METERS = 0.1f
const val ROAD_LIFT_METERS = 0.15f
const val ROAD_STRIP_METERS = 3.0

val TRUNK_ALBEDO = rgbOfHex("5b4632")
val FALLBACK_CANOPY_ALBEDO = rgbOfHex("4e7a3a")
val WATER_ALBEDO = rgbOfHex("3a6d8c")
val ROAD_ALBEDO = rgbOfHex("57524b")

private class MeshBuilder {
    val vertices = ArrayList<Float>()
    val triangles = ArrayList<Int>()
    val triColors = ArrayList<String>()

    fun vertex(x: Float, y: Float, z: Float): Int {
        vertices.add(x)
        vertices.add(y)
        vertices.add(z)
        return vertices.size / 3 - 1
    }

    fun triangle(a: Int, b: Int, c: Int, color: String) {
        triangles.addAll(listOf(a, b, c))
        triColors.add(color)
    }

    fun mesh(): Scene3dMesh = Scene3dMesh(vertices = vertices, triangles = triangles, triColors = triColors)
}

fun crownRadiusOf(footprint: List<GroundPoint>): Double {
    val centroid = GroundPoint(
        east = Meters(footprint.sumOf { it.east.value } / footprint.size),
        north = Meters(footprint.sumOf { it.north.value } / footprint.size),
    )
    return footprint
        .sumOf { hypot(it.east.value - centroid.east.value, it.north.value - centroid.north.value) } / footprint.size
}

fun treeMeshOf(
    placed: PlacedEntity,
    terrain: TerrainGrid,
    frame: SceneFrame,
    daylight: Daylight,
    canopyAlbedo: Rgb?,
): Scene3dMesh {
    val centroid = GroundPoint(
        east = Meters(placed.footprint.sumOf { it.east.value } / placed.footprint.size),
        north = Meters(placed.footprint.sumOf { it.north.value } / placed.footprint.size),
    )
    val crownRadius = crownRadiusOf(placed.footprint).toFloat()
    val ground = groundHeightAt(terrain, centroid)
    val treeHeight = placed.height.value.toFloat()
    val trunkTop = ground + treeHeight * TRUNK_HEIGHT_SHARE
    val centerX = frame.sceneX(centroid.east.value)
    val centerZ = frame.sceneZ(centroid.north.value)
    val builder = MeshBuilder()
    buildTrunk(builder, centerX, centerZ, ground, trunkTop, (crownRadius * TRUNK_RADIUS_SHARE).toFloat().coerceIn(0.08f, 0.45f), daylight)
    buildCanopy(
        builder,
        centerX = centerX,
        centerZ = centerZ,
        centerY = ground + treeHeight * (TRUNK_HEIGHT_SHARE + 1f) / 2f,
        semiHorizontal = crownRadius,
        semiVertical = treeHeight * (1f - TRUNK_HEIGHT_SHARE) / 2f,
        albedo = liftedForVisibility(foliageTintOf(canopyAlbedo)),
        daylight = daylight,
    )
    return builder.mesh()
}

// the NAIP pixel keeps its identity but leans toward foliage green: an airphoto pixel
// under a crown is part shadow, and a pure shadow-blue tree reads as plastic
private fun foliageTintOf(canopyAlbedo: Rgb?): Rgb {
    val naip = canopyAlbedo ?: return FALLBACK_CANOPY_ALBEDO
    return Rgb(
        red = naip.red * 0.7f + FALLBACK_CANOPY_ALBEDO.red * 0.3f,
        green = naip.green * 0.7f + FALLBACK_CANOPY_ALBEDO.green * 0.3f,
        blue = naip.blue * 0.7f + FALLBACK_CANOPY_ALBEDO.blue * 0.3f,
    )
}

// a NAIP pixel under deep canopy shadow would render a near-black tree; lift the floor
private fun liftedForVisibility(albedo: Rgb): Rgb {
    val brightest = maxOf(albedo.red, albedo.green, albedo.blue)
    if (brightest >= 0.25f) return albedo
    if (brightest <= 0f) return FALLBACK_CANOPY_ALBEDO
    val scale = 0.25f / brightest
    return Rgb(albedo.red * scale, albedo.green * scale, albedo.blue * scale)
}

private fun buildTrunk(
    builder: MeshBuilder,
    centerX: Float,
    centerZ: Float,
    bottom: Float,
    top: Float,
    radius: Float,
    daylight: Daylight,
) {
    val bottomRing = IntArray(TRUNK_SIDES)
    val topRing = IntArray(TRUNK_SIDES)
    for (side in 0 until TRUNK_SIDES) {
        val angle = 2.0 * PI * side / TRUNK_SIDES
        val x = centerX + radius * cos(angle).toFloat()
        val z = centerZ + radius * sin(angle).toFloat()
        bottomRing[side] = builder.vertex(x, bottom, z)
        topRing[side] = builder.vertex(x, top, z)
    }
    for (side in 0 until TRUNK_SIDES) {
        val next = (side + 1) % TRUNK_SIDES
        val midAngle = 2.0 * PI * (side + 0.5) / TRUNK_SIDES
        val normal = SceneDirection(cos(midAngle).toFloat(), 0f, sin(midAngle).toFloat())
        val face = hexOf(litColor(TRUNK_ALBEDO, normal, sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight))
        builder.triangle(bottomRing[side], bottomRing[next], topRing[next], face)
        builder.triangle(bottomRing[side], topRing[next], topRing[side], face)
    }
}

private fun buildCanopy(
    builder: MeshBuilder,
    centerX: Float,
    centerZ: Float,
    centerY: Float,
    semiHorizontal: Float,
    semiVertical: Float,
    albedo: Rgb,
    daylight: Daylight,
) {
    val rings = ArrayList<IntArray>()
    for (stack in 0..CANOPY_STACKS) {
        val polar = PI * stack / CANOPY_STACKS
        val ringY = centerY + semiVertical * cos(polar).toFloat()
        val ringRadius = semiHorizontal * sin(polar).toFloat()
        val ring = IntArray(CANOPY_SLICES)
        for (slice in 0 until CANOPY_SLICES) {
            val angle = 2.0 * PI * slice / CANOPY_SLICES
            ring[slice] = builder.vertex(
                centerX + ringRadius * cos(angle).toFloat(),
                ringY,
                centerZ + ringRadius * sin(angle).toFloat(),
            )
        }
        rings.add(ring)
    }
    for (stack in 0 until CANOPY_STACKS) {
        val upper = rings[stack]
        val lower = rings[stack + 1]
        for (slice in 0 until CANOPY_SLICES) {
            val next = (slice + 1) % CANOPY_SLICES
            val face = canopyFaceColor(builder, upper[slice], lower[slice], lower[next], centerX, centerY, centerZ, albedo, daylight)
            if (stack > 0) builder.triangle(upper[slice], upper[next], lower[next], face)
            if (stack < CANOPY_STACKS - 1) builder.triangle(upper[slice], lower[next], lower[slice], face)
        }
    }
}

private fun canopyFaceColor(
    builder: MeshBuilder,
    a: Int,
    b: Int,
    c: Int,
    centerX: Float,
    centerY: Float,
    centerZ: Float,
    albedo: Rgb,
    daylight: Daylight,
): String {
    val x = (builder.vertices[a * 3] + builder.vertices[b * 3] + builder.vertices[c * 3]) / 3f - centerX
    val y = (builder.vertices[a * 3 + 1] + builder.vertices[b * 3 + 1] + builder.vertices[c * 3 + 1]) / 3f - centerY
    val z = (builder.vertices[a * 3 + 2] + builder.vertices[b * 3 + 2] + builder.vertices[c * 3 + 2]) / 3f - centerZ
    val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
    val normal = SceneDirection(x / length, y / length, z / length)
    return hexOf(litColor(albedo, normal, sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight))
}

fun waterMeshOf(placed: PlacedEntity, terrain: TerrainGrid, frame: SceneFrame, daylight: Daylight): Scene3dMesh {
    val surfaceY = placed.footprint.minOf { groundHeightAt(terrain, it) } + WATER_LIFT_METERS
    val builder = MeshBuilder()
    val corners = placed.footprint.map { builder.vertex(frame.sceneX(it.east.value), surfaceY, frame.sceneZ(it.north.value)) }
    val face = hexOf(litColor(WATER_ALBEDO, SceneDirection(0f, 1f, 0f), sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight))
    for (fan in 1 until corners.size - 1) {
        builder.triangle(corners[0], corners[fan], corners[fan + 1], face)
    }
    return builder.mesh()
}

// draped strip: the road band follows the ground it crosses instead of one flat quad
fun roadMeshOf(placed: PlacedEntity, terrain: TerrainGrid, frame: SceneFrame, daylight: Daylight): Scene3dMesh {
    val easts = placed.footprint.map { it.east.value }
    val norths = placed.footprint.map { it.north.value }
    val south = norths.min()
    val north = norths.max()
    val west = easts.min()
    val east = easts.max()
    val steps = ceil((east - west) / ROAD_STRIP_METERS).toInt().coerceAtLeast(1)
    val builder = MeshBuilder()
    val face = hexOf(litColor(ROAD_ALBEDO, SceneDirection(0f, 1f, 0f), sunlitFraction = 1f, skyOpenness = 1f, daylight = daylight))
    var previousSouth = roadVertex(builder, west, south, terrain, frame)
    var previousNorth = roadVertex(builder, west, north, terrain, frame)
    for (step in 1..steps) {
        val stripEast = west + (east - west) * step / steps
        val nextSouth = roadVertex(builder, stripEast, south, terrain, frame)
        val nextNorth = roadVertex(builder, stripEast, north, terrain, frame)
        builder.triangle(previousSouth, nextSouth, nextNorth, face)
        builder.triangle(previousSouth, nextNorth, previousNorth, face)
        previousSouth = nextSouth
        previousNorth = nextNorth
    }
    return builder.mesh()
}

// the drawn terrain is a downsampled average of this grid, so the drape rides the highest
// nearby ground rather than one sample the coarser mesh may sit above
private fun roadVertex(builder: MeshBuilder, east: Double, north: Double, terrain: TerrainGrid, frame: SceneFrame): Int {
    val reach = terrain.cellSize.value * RENDER_DOWNSAMPLE_FACTOR
    val nearbyGround = listOf(-reach, 0.0, reach).flatMap { alongEast ->
        listOf(-reach, 0.0, reach).map { alongNorth ->
            groundHeightAt(terrain, GroundPoint(Meters(east + alongEast), Meters(north + alongNorth)))
        }
    }.max()
    return builder.vertex(frame.sceneX(east), nearbyGround + ROAD_LIFT_METERS, frame.sceneZ(north))
}
