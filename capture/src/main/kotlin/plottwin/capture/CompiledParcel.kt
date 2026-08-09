package plottwin.capture

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plottwin.worldstate.Meters
import plottwin.worldstate.RawElevation
import plottwin.worldstate.SiteRow
import plottwin.worldstate.decodeHeightsBase64

// compile_parcel.py output; row 0 = southernmost, column 0 = westernmost (TerrainGrid convention)
@Serializable
data class CompiledParcel(
    val site: CompiledSite,
    val columns: Int,
    val rows: Int,
    @SerialName("cell_size_meters") val cellSizeMeters: Double,
    @SerialName("heights_base64") val heightsBase64: String,
    @SerialName("albedo_base64") val albedoBase64: String? = null,
    val provenance: CaptureProvenance,
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
)

private val parcelCodec = Json { ignoreUnknownKeys = true }

fun compiledParcelOf(json: String): CompiledParcel = parcelCodec.decodeFromString(CompiledParcel.serializer(), json)

fun readCompiledParcel(path: Path): CompiledParcel = compiledParcelOf(path.readText())

fun rawElevationOf(parcel: CompiledParcel): RawElevation = RawElevation(
    columns = parcel.columns,
    rows = parcel.rows,
    cellSize = Meters(parcel.cellSizeMeters),
    surfaceHeights = decodeHeightsBase64(parcel.heightsBase64, parcel.columns * parcel.rows),
)

fun siteRowOf(parcel: CompiledParcel): SiteRow = SiteRow(
    latitudeDegrees = parcel.site.latitudeDegrees,
    longitudeDegrees = parcel.site.longitudeDegrees,
    timeZoneId = parcel.site.timeZoneId,
)

fun albedoTriplesOf(parcel: CompiledParcel): FloatArray? {
    val encoded = parcel.albedoBase64 ?: return null
    val bytes = java.util.Base64.getDecoder().decode(encoded)
    require(bytes.size == parcel.columns * parcel.rows * 3) {
        "expected ${parcel.columns * parcel.rows * 3} albedo bytes, got ${bytes.size}"
    }
    return FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
}
