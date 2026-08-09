package plottwin.eyes

import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import plottwin.capture.CompiledParcel
import plottwin.capture.albedoTriplesOf
import plottwin.capture.appendRealParcel
import plottwin.capture.readCompiledParcel
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene
import plottwin.worldstate.WorldLog

val REAL_PARCEL_VIEW_DATE: LocalDate = LocalDate.of(2026, 8, 8)

fun realParcelMidday(parcel: CompiledParcel): ZonedDateTime =
    REAL_PARCEL_VIEW_DATE.atTime(13, 0).atZone(ZoneId.of(parcel.site.timeZoneId))

fun realParcelScene(parcel: CompiledParcel, moment: ZonedDateTime = realParcelMidday(parcel)): PlotScene {
    val state = WorldLog.openInMemory().use { log ->
        appendRealParcel(log, parcel)
        log.currentState()
    }
    val daylight = daylightOverPlot(state, moment)
    return PlotScene(state, projectWalkableScene(state, emptyList(), daylight, albedoTriplesOf(parcel)), daylight)
}

fun realParcelSceneFromFile(parcelPath: Path, moment: ZonedDateTime? = null): PlotScene {
    val parcel = readCompiledParcel(parcelPath)
    return realParcelScene(parcel, moment ?: realParcelMidday(parcel))
}
