package plottwin.eyes

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.pow
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import plottwin.capture.RealParcelFixture
import plottwin.render.LooksTaste
import plottwin.render.SKY_ENTITY_ID
import plottwin.render.SURROUND_ENTITY_ID
import plottwin.render.SURROUND_RING_CELLS
import plottwin.render.SURROUND_SPOKE_CELLS
import plottwin.render.daylightOverPlot

class TasteMeasurementTest {

    private fun compiledParcelPath(): Path =
        Path.of(System.getProperty("user.dir"), "..", "capture", "data", "compiled", "parcel.json").normalize()

    @Test
    fun surround_haze_candidates_are_measured_against_what_the_surround_is_for() {
        assumeTrue(Files.exists(compiledParcelPath()), "capture cache absent")
        for (haze in listOf(0.00f, 0.10f, 0.20f, 0.30f, 0.45f, 0.60f)) {
            val scene = realParcelSceneFromFile(compiledParcelPath(), taste = LooksTaste(surroundBaseHaze = haze))
            val surround = scene.spec.meshesByEntity.getValue(SURROUND_ENTITY_ID)
            val surroundColors = surround.triColors.toSet().map(::argbOfHex)
            val parcelColors = (scene.spec.meshesByEntity - SURROUND_ENTITY_ID - SKY_ENTITY_ID)
                .values.flatMap { it.triColors }.toSet().map(::argbOfHex)
            val closest = surroundColors.minOf { backdrop -> parcelColors.minOf { chebyshevBetween(backdrop, it) } }
            var worstRingStep = 0.0
            for (ring in 0 until SURROUND_RING_CELLS - 1) {
                for (spoke in 0 until SURROUND_SPOKE_CELLS) {
                    val here = argbOfHex(surround.triColors[(ring * SURROUND_SPOKE_CELLS + spoke) * 2])
                    val next = argbOfHex(surround.triColors[((ring + 1) * SURROUND_SPOKE_CELLS + spoke) * 2])
                    worstRingStep = maxOf(worstRingStep, kotlin.math.abs(lumaOf(here) - lumaOf(next)))
                }
            }
            val nearest = argbOfHex(surround.triColors.first())
            println(
                "[taste] haze %.2f: palette gap %d, worst ring luma step %.1f, nearest-surround rgb %06x luma %.1f, span %s"
                    .format(haze, closest, worstRingStep, nearest and 0xFFFFFF, lumaOf(nearest), lumaSpanOf(surroundColors)),
            )
        }
    }

    @Test
    fun sun_altitude_through_the_evening_is_measured_so_the_disk_question_has_a_moment() {
        val parcel = RealParcelFixture.parcel()
        val zone = ZoneId.of(parcel.site.timeZoneId)
        val state = realParcelScene(parcel, boundary = RealParcelFixture.boundary()).state
        for (hour in listOf(13, 17, 18, 19, 20)) {
            for (minute in listOf(0, 30)) {
                val sun = daylightOverPlot(state, REAL_PARCEL_VIEW_DATE.atTime(hour, minute).atZone(zone)).sun
                println("[taste] %02d:%02d altitude %.1f azimuth %.1f".format(hour, minute, sun.altitudeDegrees, sun.azimuthDegrees))
            }
        }
    }

    @Test
    fun glow_tightness_candidates_are_measured_as_the_angle_they_subtend() {
        for (tightness in listOf(8.0, 32.0, 128.0, 400.0, 1200.0)) {
            val halfWidth = Math.toDegrees(acos(0.5.pow(1.0 / tightness)))
            println("[taste] tightness %.0f: glow half-max at %.2f degrees off the sun, full width %.2f degrees".format(tightness, halfWidth, 2 * halfWidth))
        }
        println("[taste] the dome samples 288 spokes, so one triangle spans 1.25 degrees of azimuth")
    }
}

fun lumaOf(argb: Int): Double =
    0.299 * ((argb shr 16) and 0xFF) + 0.587 * ((argb shr 8) and 0xFF) + 0.114 * (argb and 0xFF)
