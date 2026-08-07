package plottwin.eyes

import java.awt.image.BufferedImage
import java.io.File
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertTrue
import plottwin.render.skyDomeRadiusOf
import plottwin.render.withSkyDome
import plottwin.solvers.ToyPlotFixture

private const val SKY_BAND_ROWS = 40
private const val LUMINANCE_SEPARATION = 6.0
private val sheetMoments = listOf(
    "09:00" to ToyPlotFixture.toyMorning,
    "13:00" to ToyPlotFixture.toyMidday,
    "18:30" to ToyPlotFixture.toyEvening,
)

private class SkyTile(val label: String, val moment: ZonedDateTime, val image: BufferedImage, val skyLuminance: Double)

private fun skyLitScene(moment: ZonedDateTime): PlotScene {
    val plain = toyPlotScene(moment)
    val terrain = plain.state.terrain!!.grid
    return plain.copy(spec = withSkyDome(plain.spec, terrain, plain.daylight))
}

private fun meanSkyBandLuminance(image: BufferedImage): Double {
    var total = 0.0
    var counted = 0
    for (row in 0 until SKY_BAND_ROWS) {
        for (column in 0 until image.width step 2) {
            total += luminanceAt(image, column, row)
            counted++
        }
    }
    return total / counted
}

private fun shadowBearingAtTheGreenhouseOf(moment: ZonedDateTime): Double {
    val scene = toyPlotScene(moment)
    val viewer = PlotViewer(scene.spec)
    val walkHeight = plotViewpoints(scene.state).first { it.subject == "greenhouse" }
    val projector = viewer.projectorFor(walkHeight.pose)
    val caster = principalShadowCasterOf(scene.state)!!
    val groundPoint = groundSampleOf(scene.state, walkHeight)
    val anchor = projector.project(groundPoint)
    val shadow = projectShadow(
        projector,
        groundPoint,
        scene.daylight.sun.azimuthDegrees,
        castShadowMetersOf(scene.state.entities.getValue(caster).height.value, scene.daylight.sun.altitudeDegrees),
    )!!
    val (innerRadius, outerRadius) = shadowAnnulusOf(shadow.lengthPx)
    val image = viewer.capture(walkHeight.pose)
    return estimateShadowDirection(
        image,
        anchor.x.toDouble(),
        anchor.y.toDouble(),
        innerRadius,
        outerRadius,
        renderedEntityMaskOf(image, viewer.captureWithout(caster, walkHeight.pose)),
    ).screenRadians
}

private fun tilesAcrossTheDay(): List<SkyTile> = sheetMoments.flatMap { (label, moment) ->
    val scene = skyLitScene(moment)
    val viewer = PlotViewer(scene.spec)
    val viewpoints = plotViewpoints(scene.state)
    val chosen = listOf(
        viewpoints.first { it.name == "overhead" },
        viewpoints.first { it.subject == "greenhouse" },
        viewpoints.first { it.name.startsWith("orbit-1") },
    )
    chosen.map { viewpoint ->
        val image = viewer.capture(viewpoint.pose)
        SkyTile("$label ${viewpoint.name}", moment, image, meanSkyBandLuminance(image))
    }
}

class LightAndSkyContactSheetTest {

    @Test
    fun the_plot_reads_differently_at_three_times_of_day_under_a_sky_dome() {
        val tiles = tilesAcrossTheDay()
        val sheet = writeContactSheet(
            tiles.map { tile ->
                ViewpointInspection(
                    viewpoint = Viewpoint(tile.label, plotViewpoints(toyPlotScene().state).first().pose, null),
                    image = tile.image,
                    findings = listOf(
                        EyeFinding("sky-band-luminance", tile.label, tile.skyLuminance, 0.0, true, "top $SKY_BAND_ROWS rows"),
                    ),
                )
            },
            File(System.getProperty("user.dir"), "build/light_sky_contact_sheet.png"),
        )
        println("[light-sky] wrote ${sheet.absolutePath}")
        tiles.forEach { println("[light-sky] ${it.label} sky luminance %.1f".format(it.skyLuminance)) }

        val walkTiles = tiles.filter { it.label.contains("walk-height") }.sortedBy { it.moment }
        assertTrue(
            walkTiles[1].skyLuminance > walkTiles[0].skyLuminance + LUMINANCE_SEPARATION &&
                walkTiles[1].skyLuminance > walkTiles[2].skyLuminance + LUMINANCE_SEPARATION,
            "midday sky should outshine morning and evening: ${walkTiles.map { it.skyLuminance }}",
        )
    }

    @Test
    fun the_rendered_shadow_swings_across_the_day() {
        val bearings = sheetMoments.map { (label, moment) -> label to shadowBearingAtTheGreenhouseOf(moment) }
        bearings.forEach { (label, bearing) ->
            println("[light-sky] $label shadow bearing %.1f deg".format(Math.toDegrees(bearing)))
        }

        val separations = bearings.flatMap { left ->
            bearings.filter { it !== left }.map { right -> angleErrorDegrees(left.second, right.second) }
        }
        assertTrue(
            separations.all { it > 20.0 },
            "morning, midday and evening shadows point the same way: ${bearings.map { it.second }}",
        )
    }

    @Test
    fun the_sky_dome_paints_above_the_horizon_and_the_ground_still_paints_over_it() {
        val scene = skyLitScene(ToyPlotFixture.toyMidday)
        val viewer = PlotViewer(scene.spec)
        val walkPose = plotViewpoints(scene.state).first { it.subject == "greenhouse" }.pose
        val withSky = viewer.capture(walkPose)
        val withoutSky = PlotViewer(toyPlotScene(ToyPlotFixture.toyMidday).spec).capture(walkPose)

        val skyPixels = (0 until withSky.width step 4).count { column -> withSky.getRGB(column, 4) != BACKGROUND_ARGB }
        assertTrue(skyPixels > withSky.width / 8, "the dome left the top of the frame as bare background")

        val groundRow = withSky.height - 20
        val groundUnchanged = (0 until withSky.width step 4).count { column ->
            withSky.getRGB(column, groundRow) == withoutSky.getRGB(column, groundRow)
        }
        assertTrue(
            groundUnchanged > withSky.width / 8,
            "the dome painted over the ground instead of behind it",
        )
    }

    @Test
    fun the_dome_stands_far_enough_out_that_the_plot_sits_at_its_centre() {
        val terrain = toyPlotScene().state.terrain!!.grid
        val plotSpan = maxOf(terrain.columns, terrain.rows) * terrain.cellSize.value
        assertTrue(skyDomeRadiusOf(terrain) > plotSpan * 4, "the dome is close enough for parallax to bend the sky")
    }
}
