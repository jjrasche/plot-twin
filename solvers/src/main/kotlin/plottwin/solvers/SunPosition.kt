package plottwin.solvers

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import net.e175.klaus.solarpositioning.DeltaT
import net.e175.klaus.solarpositioning.Grena3
import plottwin.worldstate.SiteRow

const val SUN_SAMPLE_MINUTES = 15L
const val STANDARD_PRESSURE_HPA = 1013.0
const val STANDARD_TEMPERATURE_CELSIUS = 11.0

// azimuth is degrees clockwise from true north, altitude degrees above the true horizon
data class SunRay(val azimuthDegrees: Double, val altitudeDegrees: Double) {
    val isAboveHorizon: Boolean get() = altitudeDegrees > 0.0
}

fun sunRayAt(site: SiteRow, moment: ZonedDateTime): SunRay {
    val position = Grena3.calculateSolarPosition(
        moment,
        site.latitudeDegrees,
        site.longitudeDegrees,
        DeltaT.estimate(moment.toLocalDate()),
        STANDARD_PRESSURE_HPA,
        STANDARD_TEMPERATURE_CELSIUS,
    )
    return SunRay(azimuthDegrees = position.azimuth(), altitudeDegrees = 90.0 - position.zenithAngle())
}

fun zoneOf(site: SiteRow): ZoneId = ZoneId.of(site.timeZoneId)

fun daylightRaysOn(site: SiteRow, date: LocalDate, sampleMinutes: Long = SUN_SAMPLE_MINUTES): List<SunRay> =
    sampleMomentsOn(site, date, sampleMinutes).map { moment -> sunRayAt(site, moment) }.filter { it.isAboveHorizon }

fun solarNoonOn(site: SiteRow, date: LocalDate): ZonedDateTime =
    sampleMomentsOn(site, date, 1L).maxBy { moment -> sunRayAt(site, moment).altitudeDegrees }

private fun sampleMomentsOn(site: SiteRow, date: LocalDate, sampleMinutes: Long): List<ZonedDateTime> {
    val dayStart = date.atStartOfDay(zoneOf(site))
    val sampleCount = (24 * 60 / sampleMinutes).toInt()
    return (0 until sampleCount).map { sample -> dayStart.plusMinutes(sample * sampleMinutes) }
}
