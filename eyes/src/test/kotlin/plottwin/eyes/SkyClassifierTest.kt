package plottwin.eyes

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.solvers.ToyPlotFixture

private const val CLASSIFIER_ACCURACY_BOUND = 0.99
private const val SKYLINE_BAND_ROWS = 12

private class GroundTruth(
    val domeDrawnPixels: List<Int>,
    val terrainBandPixels: List<Int>,
    val withDome: BufferedImage,
)

private fun walkHeightGroundTruthAt(moment: java.time.ZonedDateTime): GroundTruth {
    val scene = toyPlotScene(moment)
    val pose = plotViewpoints(scene.state).first { it.subject == "greenhouse" }.pose
    val withDome = PlotViewer(scene.spec).capture(pose)
    val withoutDome = PlotViewer(scene.spec.copy(
        world = scene.spec.world.copy(
            entities = scene.spec.world.entities.filterNot { it.id == plottwin.render.SKY_ENTITY_ID },
            background = plottwin.render.SCENE_BACKGROUND,
        ),
        meshesByEntity = terrainAndEntityMeshesOf(scene.spec),
    )).capture(pose)
    val domeDrawn = ArrayList<Int>()
    val terrainBand = ArrayList<Int>()
    val domelessSkyline = observedSkylineOf(withoutDome)
    for (column in 0 until withDome.width) {
        for (row in 0 until withDome.height) {
            val pixel = row * withDome.width + column
            if (withDome.getRGB(column, row) != withoutDome.getRGB(column, row)) domeDrawn.add(pixel)
        }
        val skylineRow = domelessSkyline[column]
        if (skylineRow == NOTHING_DRAWN) continue
        for (row in skylineRow until minOf(skylineRow + SKYLINE_BAND_ROWS, withDome.height)) {
            terrainBand.add(row * withDome.width + column)
        }
    }
    return GroundTruth(domeDrawn, terrainBand, withDome)
}

class SkyClassifierTest {

    @Test
    fun pixels_the_dome_drew_classify_as_sky_and_the_skyline_band_classifies_as_terrain() {
        val scene = toyPlotScene(ToyPlotFixture.toyMidday)
        val classifier = requireNotNull(skyClassifierOf(scene.spec, scene.daylight))
        val truth = walkHeightGroundTruthAt(ToyPlotFixture.toyMidday)

        val skyHits = truth.domeDrawnPixels.count {
            classifier.isSky(truth.withDome.getRGB(it % truth.withDome.width, it / truth.withDome.width))
        }
        val terrainHits = truth.terrainBandPixels.count {
            !classifier.isSky(truth.withDome.getRGB(it % truth.withDome.width, it / truth.withDome.width))
        }
        val skyAccuracy = skyHits.toDouble() / truth.domeDrawnPixels.size
        val terrainAccuracy = terrainHits.toDouble() / truth.terrainBandPixels.size
        println("[sky-classifier] sky accuracy %.4f over ${truth.domeDrawnPixels.size} dome pixels".format(skyAccuracy))
        println("[sky-classifier] terrain accuracy %.4f over ${truth.terrainBandPixels.size} skyline-band pixels".format(terrainAccuracy))

        assertTrue(skyAccuracy >= CLASSIFIER_ACCURACY_BOUND, "dome pixels misread as terrain: $skyAccuracy")
        assertTrue(terrainAccuracy >= CLASSIFIER_ACCURACY_BOUND, "terrain pixels misread as sky: $terrainAccuracy")
    }

    @Test
    fun grass_entity_and_marker_colours_stay_outside_the_sky_palette() {
        val scene = toyPlotScene(ToyPlotFixture.toyMidday)
        val classifier = requireNotNull(skyClassifierOf(scene.spec, scene.daylight))
        val solidColors = terrainAndEntityMeshesOf(scene.spec).values.flatMap { it.triColors }.toHashSet()
        val misread = solidColors.filter { classifier.isSky(argbOfHex(it)) }
        assertTrue(misread.isEmpty(), "solid-geometry colours inside the sky palette: $misread")
    }

    @Test
    fun a_spec_without_a_sky_dome_yields_no_classifier() {
        val scene = toyPlotScene(ToyPlotFixture.toyMidday)
        val domeless = scene.spec.copy(meshesByEntity = terrainAndEntityMeshesOf(scene.spec))
        assertTrue(skyClassifierOf(domeless, scene.daylight) == null)
    }
}
