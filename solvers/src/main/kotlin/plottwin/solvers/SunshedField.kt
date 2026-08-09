package plottwin.solvers

import java.time.LocalDate
import plottwin.worldstate.CurrentState
import plottwin.worldstate.PlacedEntity
import plottwin.worldstate.Surface

fun interface SunshedFieldSource {
    fun directSunHoursOf(state: CurrentState, date: LocalDate, surface: Surface): FloatArray?
}

fun directSunHoursOf(surface: OccluderSurface, rays: List<SunRay>, sampleMinutes: Long): FloatArray {
    val hoursPerSample = (sampleMinutes / 60.0).toFloat()
    val hours = FloatArray(surface.cellCount)
    for (ray in rays) {
        val sunlit = sunlitFractionsAt(surface, ray)
        for (cell in hours.indices) hours[cell] += sunlit[cell] * hoursPerSample
    }
    return hours
}

val UNCACHED_SUNSHED_FIELD: SunshedFieldSource = SunshedFieldSource { state, date, surface ->
    val site = state.site ?: return@SunshedFieldSource null
    val occluders = occluderSurfaceOf(state, surface) ?: return@SunshedFieldSource null
    directSunHoursOf(occluders, daylightRaysOn(site, date), SUN_SAMPLE_MINUTES)
}

// everything the sweep reads is in the key: ground version, what stands on it, and the date
data class SunshedKey(
    val terrainVersionSeq: Long,
    val entities: Map<String, PlacedEntity>,
    val date: LocalDate,
)

class SunshedCache(
    private val source: SunshedFieldSource = UNCACHED_SUNSHED_FIELD,
) : SunshedFieldSource {
    private val hoursByKey = mutableMapOf<SunshedKey, FloatArray>()

    override fun directSunHoursOf(state: CurrentState, date: LocalDate, surface: Surface): FloatArray? {
        val terrain = state.terrainOn(surface) ?: return null
        val key = SunshedKey(terrain.versionSeq, state.entities, date)
        return hoursByKey.getOrPut(key) { source.directSunHoursOf(state, date, surface) ?: return null }
    }
}
