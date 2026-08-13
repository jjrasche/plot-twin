package plottwin.capture

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plottwin.worldstate.GroundFrame
import plottwin.worldstate.Meters
import plottwin.worldstate.RawElevation
import plottwin.worldstate.SiteRow
import plottwin.worldstate.decodeHeightsBase64

// compile_parcel.py output; row 0 = southernmost, column 0 = westernmost (TerrainGrid convention).
// The grid is the parcel boundary's bounding box, so the cut declares the frame it was cut in.
@Serializable
data class CompiledParcel(
    val site: CompiledSite,
    val columns: Int,
    val rows: Int,
    @SerialName("cell_size_meters") val cellSizeMeters: Double,
    val frame: CompiledFrame,
    @SerialName("heights_base64") val heightsBase64: String,
    @SerialName("albedo_base64") val albedoBase64: String? = null,
    val provenance: CaptureProvenance,
)

@Serializable
data class CompiledFrame(
    val crs: String,
    @SerialName("origin_easting_meters") val originEastingMeters: Double,
    @SerialName("origin_northing_meters") val originNorthingMeters: Double,
)

@Serializable
data class CompiledSite(
    @SerialName("latitude_degrees") val latitudeDegrees: Double,
    @SerialName("longitude_degrees") val longitudeDegrees: Double,
    @SerialName("time_zone_id") val timeZoneId: String,
)

@Serializable
data class CaptureProvenance(
    @SerialName("dem_product") val demProduct: String,
    @SerialName("dem_url") val demUrl: String,
    @SerialName("naip_product") val naipProduct: String? = null,
    @SerialName("naip_url") val naipUrl: String? = null,
    @SerialName("horizontal_crs") val horizontalCrs: String,
    @SerialName("vertical_datum") val verticalDatum: String,
    @SerialName("source_epoch") val sourceEpoch: String,
    val interpolation: String,
    @SerialName("elevation_min_meters") val elevationMinMeters: Double,
    @SerialName("elevation_max_meters") val elevationMaxMeters: Double,
    @SerialName("compiled_parcel") val compiledParcel: CompiledParcelBinding? = null,
)

// Written into the TRACKED 1m fixture by the same compile_parcel.py pass that writes the
// untracked 10cm cut, naming that cut's exact bytes. The fixture is what every always-run test
// measures; the 10cm cut is what a human's eye scores. Without this they are two artifacts with
// nothing holding them to one run, and their provenance can disagree in silence.
@Serializable
data class CompiledParcelBinding(
    val path: String,
    val sha256: String,
    val columns: Int,
    val rows: Int,
    @SerialName("cell_size_meters") val cellSizeMeters: Double,
    @SerialName("elevation_min_meters") val elevationMinMeters: Double,
    @SerialName("elevation_max_meters") val elevationMaxMeters: Double,
)

private val parcelCodec = Json { ignoreUnknownKeys = true }

fun compiledParcelOf(json: String): CompiledParcel = parcelCodec.decodeFromString(CompiledParcel.serializer(), json)

fun readCompiledParcel(path: Path): CompiledParcel = compiledParcelOf(path.readText())

fun rawElevationOf(parcel: CompiledParcel): RawElevation = RawElevation(
    columns = parcel.columns,
    rows = parcel.rows,
    cellSize = Meters(parcel.cellSizeMeters),
    surfaceHeights = decodeHeightsBase64(parcel.heightsBase64, parcel.columns * parcel.rows),
    frame = groundFrameOf(parcel.frame),
)

fun groundFrameOf(frame: CompiledFrame): GroundFrame = GroundFrame(
    crs = frame.crs,
    originEasting = Meters(frame.originEastingMeters),
    originNorthing = Meters(frame.originNorthingMeters),
)

fun siteRowOf(parcel: CompiledParcel): SiteRow = SiteRow(
    latitudeDegrees = parcel.site.latitudeDegrees,
    longitudeDegrees = parcel.site.longitudeDegrees,
    timeZoneId = parcel.site.timeZoneId,
)

fun sha256Of(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }

// Every way the tracked fixture's recorded binding and the compiled cut on disk fail to name one
// compile run, listed rather than thrown, so a red gate reports all of them at once.
fun fixtureBindingDisagreementsOf(fixture: CompiledParcel, compiledPath: Path): List<String> {
    val bound = fixture.provenance.compiledParcel
        ?: return listOf("the tracked 1m fixture carries no compiled-parcel binding; re-run ${CaptureCache.COMPILE_PARCEL}")
    val compiled = readCompiledParcel(compiledPath)
    val onDisk = CompiledParcelBinding(
        path = bound.path,
        sha256 = sha256Of(compiledPath),
        columns = compiled.columns,
        rows = compiled.rows,
        cellSizeMeters = compiled.cellSizeMeters,
        elevationMinMeters = compiled.provenance.elevationMinMeters,
        elevationMaxMeters = compiled.provenance.elevationMaxMeters,
    )
    if (onDisk == bound) return emptyList()
    return listOf(
        "the 1m fixture and $compiledPath do not name one compile run",
        "  fixture says: $bound",
        "  on disk is:   $onDisk",
        "  re-run ${CaptureCache.COMPILE_PARCEL} so both are written in one pass",
    )
}

fun albedoTriplesOf(parcel: CompiledParcel): FloatArray? {
    val encoded = parcel.albedoBase64 ?: return null
    val bytes = java.util.Base64.getDecoder().decode(encoded)
    require(bytes.size == parcel.columns * parcel.rows * 3) {
        "expected ${parcel.columns * parcel.rows * 3} albedo bytes, got ${bytes.size}"
    }
    return FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
}
