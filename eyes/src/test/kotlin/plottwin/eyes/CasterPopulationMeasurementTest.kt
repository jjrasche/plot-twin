package plottwin.eyes

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.capture.RealParcelFixture
import plottwin.solvers.ToyPlotFixture

class CasterPopulationMeasurementTest {

    @Test
    fun the_frozen_floor_still_separates_the_toy_plots_one_caster_from_the_woodlots_ninety_seven() {
        val toyMoments = listOf(
            "toy-morning" to ToyPlotFixture.toyMorning,
            "toy-midday" to ToyPlotFixture.toyMidday,
            "toy-evening" to ToyPlotFixture.toyEvening,
        )
        val toyShares = toyMoments.flatMap { (label, moment) -> report(label, toyPlotScene(moment)) }
        val realShares = report("real-parcel-fixture", realParcelScene(RealParcelFixture.parcel(), RealParcelFixture.features()))
        val compiled = Path.of(System.getProperty("user.dir"), "..", "capture", "data", "compiled", "parcel.json").normalize()
        val fullResShares =
            if (Files.exists(compiled)) report("real-parcel-full-res", realParcelSceneFromFile(compiled)) else emptyList()

        val realArm = realShares + fullResShares
        println("[population] toy arm assumed-shares: ${toyShares.map { "%.3f".format(it) }}")
        println("[population] real arm assumed-shares: ${realArm.map { "%.3f".format(it) }}")
        assertTrue(toyShares.isNotEmpty() && realArm.isNotEmpty(), "both arms must produce measurements")

        val toyWithShade = toyShares.filter { it > 0.0 }
        assertTrue(
            toyWithShade.min() >= PRINCIPAL_CASTER_SHARE_FLOOR,
            "the toy plot fell below the frozen floor: ${toyWithShade.min()} < $PRINCIPAL_CASTER_SHARE_FLOOR",
        )
        assertTrue(
            realArm.max() < PRINCIPAL_CASTER_SHARE_FLOOR,
            "the woodlot rose above the frozen floor: ${realArm.max()} >= $PRINCIPAL_CASTER_SHARE_FLOOR",
        )
    }

    private fun report(label: String, scene: PlotScene): List<Double> {
        val viewer = PlotViewer(scene.spec)
        val assumed = principalShadowCasterOf(scene.state)
        val shadowedGround = shadowedGroundOf(scene.state, scene.daylight.sun)
        println(
            "[population] $label sun az %.1f alt %.1f, assumed caster %s, shaded ground samples %d".format(
                scene.daylight.sun.azimuthDegrees,
                scene.daylight.sun.altitudeDegrees,
                assumed,
                shadowedGround.size,
            ),
        )
        val classifier = skyClassifierOf(scene.spec, scene.daylight)
        return plotViewpoints(scene.state).map { viewpoint ->
            val image = viewer.capture(viewpoint.pose)
            val population = shadowReadingAt(scene, viewer, viewpoint, image, classifier, shadowedGround)
                ?.population ?: CasterPopulation.NONE
            println(
                "[population] $label/${viewpoint.name} assumed-share %.3f casters %d shaded %d top %s".format(
                    population.assumedShare,
                    population.casterCount,
                    population.shadowSamples,
                    population.shares.take(3).joinToString { "${it.caster}=%.3f".format(it.share) },
                ),
            )
            population.assumedShare
        }
    }
}
