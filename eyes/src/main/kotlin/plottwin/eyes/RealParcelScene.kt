package plottwin.eyes

import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import plottwin.capture.CaptureCache
import plottwin.capture.CapturedBoundary
import plottwin.capture.CompiledParcel
import plottwin.capture.ParcelFeatures
import plottwin.capture.albedoTriplesOf
import plottwin.capture.appendParcelBoundary
import plottwin.capture.appendParcelFeatures
import plottwin.capture.appendRealParcel
import plottwin.capture.readCapturedBoundary
import plottwin.capture.readCompiledParcel
import plottwin.capture.presentOrLoud
import plottwin.capture.readParcelFeatures
import plottwin.render.LooksTaste
import plottwin.render.daylightOverPlot
import plottwin.render.projectWalkableScene
import plottwin.render.withSkyDome
import plottwin.worldstate.WorldLog

val REAL_PARCEL_VIEW_DATE: LocalDate = LocalDate.of(2026, 8, 8)

fun realParcelMidday(parcel: CompiledParcel): ZonedDateTime =
    REAL_PARCEL_VIEW_DATE.atTime(13, 0).atZone(ZoneId.of(parcel.site.timeZoneId))

fun realParcelScene(
    parcel: CompiledParcel,
    features: ParcelFeatures? = null,
    boundary: CapturedBoundary? = null,
    moment: ZonedDateTime = realParcelMidday(parcel),
    taste: LooksTaste = LooksTaste(),
): PlotScene {
    val state = WorldLog.openInMemory().use { log ->
        appendRealParcel(log, parcel)
        boundary?.let { appendParcelBoundary(log, it) }
        features?.let { appendParcelFeatures(log, it) }
        log.currentState()
    }
    val daylight = daylightOverPlot(state, moment)
    val terrain = requireNotNull(state.terrain) { "the real parcel scene needs a base-terrain row" }.grid
    val spec = withSkyDome(projectWalkableScene(state, emptyList(), daylight, albedoTriplesOf(parcel), taste), terrain, daylight, taste)
    return PlotScene(state, spec, daylight)
}

fun realParcelSceneFromFile(
    parcelPath: Path,
    moment: ZonedDateTime? = null,
    taste: LooksTaste = LooksTaste(),
): PlotScene {
    val parcel = readCompiledParcel(parcelPath)
    // Absent siblings used to fall back to null, which drew the parcel with no trees and no
    // property line while still calling itself the real-parcel scene. Silence is the one answer
    // this cannot give.
    val features = readParcelFeatures(presentOrLoud(parcelPath.resolveSibling("features.json"), CaptureCache.EXTRACT_FEATURES))
    val boundary = readCapturedBoundary(presentOrLoud(boundaryPathBesideCompiled(parcelPath), CaptureCache.FETCH_BOUNDARY))
    return realParcelScene(parcel, features, boundary, moment ?: realParcelMidday(parcel), taste)
}

// the capture cache lays the boundary pull beside the compiled cut, one directory over
fun boundaryPathBesideCompiled(parcelPath: Path): Path =
    parcelPath.parent.parent.resolve("boundary").resolve("boundary.json")
