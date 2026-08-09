package plottwin.eyes

import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.solvers.ToyPlotFixture

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
