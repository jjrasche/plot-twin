package plottwin.eyes

import kotlin.math.abs
import plottwin.render.Daylight
import plottwin.render.SKY_ENTITY_ID
import plottwin.render.WalkableSceneSpec
import plottwin.render.hexOf

const val SKY_MATCH_TOLERANCE = 10

// Palette comes from the spec's own dome colours, so the classifier can only bless what the dome was told to paint.
class SkyClassifier(paletteColors: Collection<Int>, private val tolerance: Int = SKY_MATCH_TOLERANCE) {
    private val paletteByBucket = HashMap<Int, MutableList<Int>>()
    private val verdictsByArgb = HashMap<Int, Boolean>()

    init {
        require(tolerance in 1..15) { "tolerance must stay below the bucket width, got $tolerance" }
        for (argb in paletteColors) {
            paletteByBucket.getOrPut(bucketKeyOf(argb)) { mutableListOf() }.add(argb)
        }
    }

    fun isSky(argb: Int): Boolean = verdictsByArgb.getOrPut(argb) { isNearPalette(argb) }

    private fun isNearPalette(argb: Int): Boolean {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        for (redBucket in neighborBuckets(red)) {
            for (greenBucket in neighborBuckets(green)) {
                for (blueBucket in neighborBuckets(blue)) {
                    val candidates = paletteByBucket[(redBucket shl 8) or (greenBucket shl 4) or blueBucket] ?: continue
                    if (candidates.any { chebyshevDistance(it, red, green, blue) <= tolerance }) return true
                }
            }
        }
        return false
    }

    private fun neighborBuckets(channel: Int): IntRange =
        maxOf(0, (channel shr 4) - 1)..minOf(15, (channel shr 4) + 1)

    private fun chebyshevDistance(argb: Int, red: Int, green: Int, blue: Int): Int = maxOf(
        abs(((argb shr 16) and 0xFF) - red),
        abs(((argb shr 8) and 0xFF) - green),
        abs((argb and 0xFF) - blue),
    )

    private fun bucketKeyOf(argb: Int): Int =
        ((((argb shr 16) and 0xFF) shr 4) shl 8) or ((((argb shr 8) and 0xFF) shr 4) shl 4) or ((argb and 0xFF) shr 4)
}

fun skyClassifierOf(spec: WalkableSceneSpec, daylight: Daylight): SkyClassifier? {
    val dome = spec.meshesByEntity[SKY_ENTITY_ID] ?: return null
    val palette = dome.triColors.toHashSet().map(::argbOfHex) + argbOfHex(hexOf(daylight.horizonTint))
    return SkyClassifier(palette)
}

fun argbOfHex(hex: String): Int = (0xFF000000.toInt()) or hex.toInt(16)

fun terrainAndEntityMeshesOf(spec: WalkableSceneSpec) = spec.meshesByEntity - SKY_ENTITY_ID
