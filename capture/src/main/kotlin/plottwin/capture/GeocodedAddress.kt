package plottwin.capture

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val EARTH_RADIUS_METERS = 6_371_008.8

@Serializable
data class GeocodedAddress(
    @SerialName("input_address") val inputAddress: String,
    @SerialName("matched_address") val matchedAddress: String,
    @SerialName("latitude_degrees") val latitudeDegrees: Double,
    @SerialName("longitude_degrees") val longitudeDegrees: Double,
    val source: String,
)

private val geocodeCodec = Json { ignoreUnknownKeys = true }

fun geocodedAddressOf(json: String): GeocodedAddress = geocodeCodec.decodeFromString(GeocodedAddress.serializer(), json)

fun readGeocodedAddress(path: Path): GeocodedAddress = geocodedAddressOf(path.readText())

fun groundDistanceMeters(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
    val latA = Math.toRadians(latitudeA)
    val latB = Math.toRadians(latitudeB)
    val halfLatSpan = Math.toRadians(latitudeB - latitudeA) / 2.0
    val halfLonSpan = Math.toRadians(longitudeB - longitudeA) / 2.0
    val chord = sin(halfLatSpan) * sin(halfLatSpan) + cos(latA) * cos(latB) * sin(halfLonSpan) * sin(halfLonSpan)
    return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(chord))
}
