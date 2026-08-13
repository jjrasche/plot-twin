package plottwin.capture

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plottwin.worldstate.BoundaryProvenance
import plottwin.worldstate.GroundFrame
import plottwin.worldstate.GroundPoint
import plottwin.worldstate.Meters
import plottwin.worldstate.ParcelBoundaryRow

// fetch_parcel_boundary.py output: the county's ring closed, in WGS84 degrees, absolute
// EPSG:26916 metres, and plot-local metres against the same origin the grid uses.
@Serializable
data class CapturedBoundary(
    @SerialName("parcel_id") val parcelId: String,
    @SerialName("site_address") val siteAddress: String,
    @SerialName("acres_county_stated") val acresCountyStated: Double,
    @SerialName("area_square_meters_derived") val areaSquareMetersDerived: Double,
    @SerialName("ring_wgs84_closed") val ringWgs84Closed: List<List<Double>>,
    @SerialName("ring_utm_closed") val ringUtmClosed: List<List<Double>>,
    @SerialName("ring_local_closed") val ringLocalClosed: List<List<Double>>,
    @SerialName("plot_local_origin") val plotLocalOrigin: CapturedOrigin,
    @SerialName("address_point_local") val addressPointLocal: CapturedLocalPoint,
    val provenance: CapturedBoundaryProvenance,
)

@Serializable
data class CapturedOrigin(
    val crs: String,
    @SerialName("easting_meters") val eastingMeters: Double,
    @SerialName("northing_meters") val northingMeters: Double,
)

@Serializable
data class CapturedLocalPoint(
    @SerialName("east_meters") val eastMeters: Double,
    @SerialName("north_meters") val northMeters: Double,
)

@Serializable
data class CapturedBoundaryProvenance(
    val source: String,
    @SerialName("pulled_at_utc") val pulledAtUtc: String,
    @SerialName("observed_at") val observedAt: String? = null,
    @SerialName("observed_at_absent_reason") val observedAtAbsentReason: String? = null,
    val sha256: String,
    val contract: String,
)

private val boundaryCodec = Json { ignoreUnknownKeys = true }

fun capturedBoundaryOf(json: String): CapturedBoundary =
    boundaryCodec.decodeFromString(CapturedBoundary.serializer(), json)

fun readCapturedBoundary(path: Path): CapturedBoundary = capturedBoundaryOf(path.readText())

fun openRingOf(closedRing: List<List<Double>>): List<GroundPoint> {
    require(closedRing.size >= 4 && closedRing.first() == closedRing.last()) {
        "expected a closed ring of at least four vertices, got ${closedRing.size}"
    }
    return closedRing.dropLast(1).map { (east, north) -> GroundPoint(Meters(east), Meters(north)) }
}

fun parcelBoundaryRowOf(boundary: CapturedBoundary): ParcelBoundaryRow = ParcelBoundaryRow(
    parcelId = boundary.parcelId,
    ring = openRingOf(boundary.ringLocalClosed),
    frame = GroundFrame(
        crs = boundary.plotLocalOrigin.crs,
        originEasting = Meters(boundary.plotLocalOrigin.eastingMeters),
        originNorthing = Meters(boundary.plotLocalOrigin.northingMeters),
    ),
    acresStated = boundary.acresCountyStated,
    provenance = BoundaryProvenance(
        source = boundary.provenance.source,
        pulledAtUtc = boundary.provenance.pulledAtUtc,
        observedAt = boundary.provenance.observedAt,
        observedAtAbsentReason = boundary.provenance.observedAtAbsentReason,
        sha256 = boundary.provenance.sha256,
        contract = boundary.provenance.contract,
    ),
)

fun addressPointOf(boundary: CapturedBoundary): GroundPoint = GroundPoint(
    east = Meters(boundary.addressPointLocal.eastMeters),
    north = Meters(boundary.addressPointLocal.northMeters),
)

fun projectedRingOf(row: ParcelBoundaryRow): List<List<Double>> = row.ring.map { vertex ->
    listOf(vertex.east.value + row.frame.originEasting.value, vertex.north.value + row.frame.originNorthing.value)
}
