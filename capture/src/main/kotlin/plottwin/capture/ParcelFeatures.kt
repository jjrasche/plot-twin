package plottwin.capture

import java.nio.file.Path
import kotlin.io.path.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// extract_features.py output: lidar-derived trees/structures/water/road for the 90m square,
// coordinates parcel-local meters (0..90, row 0 = southernmost)
@Serializable
data class ParcelFeatures(
    val trees: List<FeatureTree>,
    val structures: List<FeatureStructure>,
    val water: List<FeatureWater>,
    val road: List<FeatureRoad>,
    val receipts: FeatureReceipts,
)

@Serializable
data class FeaturePoint(
    @SerialName("east_meters") val eastMeters: Double,
    @SerialName("north_meters") val northMeters: Double,
)

@Serializable
data class FeatureTree(
    @SerialName("east_meters") val eastMeters: Double,
    @SerialName("north_meters") val northMeters: Double,
    @SerialName("height_meters") val heightMeters: Double,
    @SerialName("crown_radius_meters") val crownRadiusMeters: Double,
)

@Serializable
data class FeatureStructure(
    val footprint: List<FeaturePoint>,
    @SerialName("height_meters") val heightMeters: Double,
)

@Serializable
data class FeatureWater(
    val footprint: List<FeaturePoint>,
    @SerialName("surface_elevation_meters") val surfaceElevationMeters: Double? = null,
)

@Serializable
data class FeatureRoad(
    val footprint: List<FeaturePoint>,
)

@Serializable
data class FeatureReceipts(
    @SerialName("square_point_count") val squarePointCount: Int,
    @SerialName("class_histogram") val classHistogram: Map<String, Int>,
    @SerialName("first_return_count") val firstReturnCount: Int,
    @SerialName("chm_max_meters") val chmMaxMeters: Double,
    @SerialName("canopy_cover_fraction") val canopyCoverFraction: Double,
    @SerialName("crown_maxima_count") val crownMaximaCount: Int,
    @SerialName("tree_count") val treeCount: Int,
)

private val featuresCodec = Json { ignoreUnknownKeys = true }

fun parcelFeaturesOf(json: String): ParcelFeatures =
    featuresCodec.decodeFromString(ParcelFeatures.serializer(), json)

fun readParcelFeatures(path: Path): ParcelFeatures = parcelFeaturesOf(path.readText())
