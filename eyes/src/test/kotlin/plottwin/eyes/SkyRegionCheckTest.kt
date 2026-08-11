package plottwin.eyes

import ai.factoredui.compose.scene3d.Scene3dMesh
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.render.Daylight
import plottwin.render.Rgb
import plottwin.render.SKY_ENTITY_ID
import plottwin.render.SUN_GLOW_TIGHTNESS
import plottwin.render.hexOf
import plottwin.solvers.ToyPlotFixture

private const val OLD_DOME_CELLS = 96
private const val OLD_RIM_HEIGHT_SHARE = 0.14f
private const val OLD_GLOW_TIGHTNESS = 24.0f

// The square-lattice dome with one colour per cell pair — the construction whose concentric
// fan banding this check exists to catch.
private fun quantizedSquareLatticeDome(radius: Float, daylight: Daylight): Scene3dMesh {
    val cellSpan = 2f * radius / OLD_DOME_CELLS
    val vertices = ArrayList<Float>((OLD_DOME_CELLS + 1) * (OLD_DOME_CELLS + 1) * 3)
    for (vertexZ in 0..OLD_DOME_CELLS) {
        for (vertexX in 0..OLD_DOME_CELLS) {
            val east = -radius + vertexX * cellSpan
            val north = -radius + vertexZ * cellSpan
            vertices.add(east)
            vertices.add(oldDomeHeightAt(east, north, radius))
            vertices.add(north)
        }
    }
    val triColors = ArrayList<String>(OLD_DOME_CELLS * OLD_DOME_CELLS * 2)
    for (row in 0 until OLD_DOME_CELLS) {
        for (column in 0 until OLD_DOME_CELLS) {
            val east = -radius + (column + 0.5f) * cellSpan
            val north = -radius + (row + 0.5f) * cellSpan
            val face = hexOf(oldSkyColorToward(east, oldDomeHeightAt(east, north, radius), north, radius, daylight))
            triColors.add(face)
            triColors.add(face)
        }
    }
    return Scene3dMesh(vertices = vertices, triColors = triColors, gridCellsX = OLD_DOME_CELLS, gridCellsZ = OLD_DOME_CELLS)
}

private fun oldDomeHeightAt(east: Float, north: Float, radius: Float): Float {
    val fromZenith = east * east + north * north
    val onSphere = sqrt((radius * radius - fromZenith).coerceAtLeast(0f))
    return maxOf(onSphere, radius * OLD_RIM_HEIGHT_SHARE)
}

private fun oldSkyColorToward(east: Float, up: Float, north: Float, radius: Float, daylight: Daylight): Rgb {
    val elevation = (up / radius).coerceIn(0f, 1f)
    val towardZenith = sqrt(elevation)
    val length = sqrt(east * east + up * up + north * north).coerceAtLeast(1e-6f)
    val towardSun =
        (east * daylight.sunDirection.east + up * daylight.sunDirection.up + north * daylight.sunDirection.north) / length
    val glow = towardSun.coerceAtLeast(0f).pow(OLD_GLOW_TIGHTNESS)
    return Rgb(
        red = (mixChannel(daylight.horizonTint.red, daylight.zenithTint.red, towardZenith) + daylight.sunGlow.red * glow).coerceIn(0f, 1f),
        green = (mixChannel(daylight.horizonTint.green, daylight.zenithTint.green, towardZenith) + daylight.sunGlow.green * glow).coerceIn(0f, 1f),
        blue = (mixChannel(daylight.horizonTint.blue, daylight.zenithTint.blue, towardZenith) + daylight.sunGlow.blue * glow).coerceIn(0f, 1f),
    )
}

private fun mixChannel(low: Float, high: Float, towardHigh: Float): Float = low + (high - low) * towardHigh

class SkyRegionCheckTest {

    private fun walkHeightReadingAt(moment: java.time.ZonedDateTime): SkyRegionReading {
        val scene = toyPlotScene(moment)
        val viewer = PlotViewer(scene.spec)
        val pose = plotViewpoints(scene.state).first { it.subject == "greenhouse" }.pose
        val classifier = requireNotNull(skyClassifierOf(scene.spec, scene.daylight))
        val reading = skyRegionReadingOf(
            viewer.capture(pose),
            classifier,
            predictedSkylineOf(terrainAndEntityMeshesOf(scene.spec).values, viewer.projectorFor(pose)),
        )
        println(
            "[sky-region] $moment coverage %.4f banding %.1f over ${reading.inspectedAboveSkyline} px".format(
                reading.coverageAboveSkyline,
                reading.maxAdjacentLuminanceJump,
            ),
        )
        return reading
    }

    @Test
    fun everything_above_the_predicted_skyline_reads_as_sky_not_void() {
        val reading = walkHeightReadingAt(ToyPlotFixture.toyMidday)
        assertTrue(
            reading.coverageAboveSkyline >= SKY_COVERAGE_BOUND,
            "a band above the horizon is neither dome nor horizon tint: coverage ${reading.coverageAboveSkyline}",
        )
    }

    @Test
    fun the_sky_gradient_carries_no_banding_steps() {
        val reading = walkHeightReadingAt(ToyPlotFixture.toyMidday)
        assertTrue(
            reading.maxAdjacentLuminanceJump <= SKY_BANDING_LUMINANCE_BOUND,
            "dome triangulation reads through the gradient: worst step ${reading.maxAdjacentLuminanceJump}",
        )
    }

    @Test
    fun the_banding_check_catches_the_quantized_square_lattice_dome() {
        val scene = toyPlotScene(ToyPlotFixture.toyMidday)
        val terrain = scene.state.terrain!!.grid
        val bandedSpec = scene.spec.copy(
            meshesByEntity = scene.spec.meshesByEntity +
                (SKY_ENTITY_ID to quantizedSquareLatticeDome(plottwin.render.skyDomeRadiusOf(terrain), scene.daylight)),
        )
        val viewer = PlotViewer(bandedSpec)
        val pose = plotViewpoints(scene.state).first { it.subject == "greenhouse" }.pose
        val classifier = requireNotNull(skyClassifierOf(bandedSpec, scene.daylight))
        val reading = skyRegionReadingOf(
            viewer.capture(pose),
            classifier,
            predictedSkylineOf(terrainAndEntityMeshesOf(bandedSpec).values, viewer.projectorFor(pose)),
        )
        println("[sky-region] quantized-dome banding %.1f".format(reading.maxAdjacentLuminanceJump))
        assertTrue(
            reading.maxAdjacentLuminanceJump > SKY_BANDING_LUMINANCE_BOUND,
            "the banding check no longer catches the defect it was built for: ${reading.maxAdjacentLuminanceJump}",
        )
    }

    @Test
    fun the_orbit_frame_is_surrounded_by_sky_not_void() {
        val scene = toyPlotScene(ToyPlotFixture.toyMidday)
        val viewer = PlotViewer(scene.spec)
        val pose = plotViewpoints(scene.state).first { it.name.startsWith("orbit-1") }.pose
        val classifier = requireNotNull(skyClassifierOf(scene.spec, scene.daylight))
        val reading = skyRegionReadingOf(
            viewer.capture(pose),
            classifier,
            predictedSkylineOf(terrainAndEntityMeshesOf(scene.spec).values, viewer.projectorFor(pose)),
        )
        println("[sky-region] orbit coverage %.4f".format(reading.coverageAboveSkyline))
        assertTrue(
            reading.coverageAboveSkyline >= SKY_COVERAGE_BOUND,
            "the orbit view floats in unpainted void: coverage ${reading.coverageAboveSkyline}",
        )
    }
}
