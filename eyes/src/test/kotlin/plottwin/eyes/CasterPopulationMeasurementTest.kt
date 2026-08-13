package plottwin.eyes

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.capture.RealParcelFixture
import plottwin.solvers.ToyPlotFixture

class CasterPopulationMeasurementTest {

    @Test
    fun the_caster_population_is_measured_on_both_arms_before_any_threshold_is_chosen() {
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

        println("[population] toy arm assumed-shares: ${toyShares.map { "%.3f".format(it) }}")
        println("[population] real arm assumed-shares: ${(realShares + fullResShares).map { "%.3f".format(it) }}")
        assertTrue(toyShares.isNotEmpty() && realShares.isNotEmpty(), "both arms must produce measurements")
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
        return plotViewpoints(scene.state).mapNotNull { viewpoint ->
            val population = populationAt(scene, viewer, viewpoint, assumed, shadowedGround) ?: return@mapNotNull null
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

    private fun populationAt(
        scene: PlotScene,
        viewer: PlotViewer,
        viewpoint: Viewpoint,
        assumed: String?,
        shadowedGround: List<ShadowedGroundPoint>,
    ): CasterPopulation? {
        val projector = viewer.projectorFor(viewpoint.pose)
        val casterHeight = assumed?.let { scene.state.entities.getValue(it).height.value } ?: 0.0
        val groundPoint = groundSampleOf(scene.state, viewpoint)
        val anchor = projector.project(groundPoint)
        val shadow = projectShadow(
            projector,
            groundPoint,
            scene.daylight.sun.azimuthDegrees,
            castShadowMetersOf(casterHeight, scene.daylight.sun.altitudeDegrees),
        ) ?: return null
        if (!anchor.visible) return null
        val (innerRadius, outerRadius) = shadowAnnulusOf(shadow.lengthPx)
        val image = viewer.capture(viewpoint.pose)
        return casterPopulationInView(
            shadowedGround,
            assumed,
            projector,
            image,
            anchor.x.toDouble(),
            anchor.y.toDouble(),
            innerRadius,
            outerRadius,
            assumed?.takeIf { scene.spec.meshesByEntity.containsKey(it) }
                ?.let { renderedEntityMaskOf(image, viewer.captureWithout(it, viewpoint.pose)) },
            skyPixelOf(skyClassifierOf(scene.spec, scene.daylight)),
        )
    }
}
