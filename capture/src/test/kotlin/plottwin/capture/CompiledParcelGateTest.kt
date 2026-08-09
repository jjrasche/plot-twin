package plottwin.capture

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import plottwin.worldstate.WorldLog
import plottwin.worldstate.encodeHeightsBase64

private fun syntheticParcelJson(columns: Int = 3, rows: Int = 2): String {
    val heights = FloatArray(columns * rows) { 251.0f + it * 0.25f }
    val albedo = ByteArray(columns * rows * 3) { (it * 7 % 256).toByte() }
    return """
        {
          "site": {"latitude_degrees": 42.6006, "longitude_degrees": -84.6547, "time_zone_id": "America/Detroit"},
          "columns": $columns,
          "rows": $rows,
          "cell_size_meters": 0.1,
          "heights_base64": "${encodeHeightsBase64(heights)}",
          "albedo_base64": "${Base64.getEncoder().encodeToString(albedo)}",
          "provenance": {
            "dem_product": "USGS 1m DEM synthetic stand-in",
            "dem_url": "file://synthetic",
            "horizontal_crs": "EPSG:26916",
            "vertical_datum": "NAVD88",
            "source_epoch": "2017-12/2018-04",
            "interpolation": "bilinear",
            "elevation_min_meters": 251.0,
            "elevation_max_meters": ${251.0f + (columns * rows - 1) * 0.25f}
          }
        }
    """.trimIndent()
}

class CompiledParcelGateTest {

    @Test
    fun compiled_parcel_base_terrain_round_trips_through_the_log() {
        val parcel = compiledParcelOf(syntheticParcelJson())
        WorldLog.openInMemory().use { log ->
            appendRealParcel(log, parcel)
            val terrain = assertNotNull(log.currentState().terrain).grid
            assertEquals(parcel.columns, terrain.columns)
            assertEquals(parcel.rows, terrain.rows)
            assertEquals(parcel.cellSizeMeters, terrain.cellSize.value)
            assertContentEquals(rawElevationOf(parcel).surfaceHeights, terrain.surfaceHeights)
        }
    }

    @Test
    fun compiled_parcel_site_row_lands_in_the_projection() {
        val parcel = compiledParcelOf(syntheticParcelJson())
        WorldLog.openInMemory().use { log ->
            appendRealParcel(log, parcel)
            val site = assertNotNull(log.currentState().site)
            assertEquals(parcel.site.latitudeDegrees, site.latitudeDegrees)
            assertEquals(parcel.site.longitudeDegrees, site.longitudeDegrees)
            assertEquals(parcel.site.timeZoneId, site.timeZoneId)
        }
    }

    @Test
    fun replay_is_deterministic_across_two_logs() {
        val parcel = compiledParcelOf(syntheticParcelJson())
        val firstHeights = WorldLog.openInMemory().use { log ->
            appendRealParcel(log, parcel)
            log.currentState().terrain!!.grid.surfaceHeights
        }
        val secondHeights = WorldLog.openInMemory().use { log ->
            appendRealParcel(log, parcel)
            log.currentState().terrain!!.grid.surfaceHeights
        }
        assertContentEquals(firstHeights, secondHeights)
    }

    @Test
    fun albedo_decodes_to_unit_range_triples_per_cell() {
        val parcel = compiledParcelOf(syntheticParcelJson())
        val albedo = assertNotNull(albedoTriplesOf(parcel))
        assertEquals(parcel.columns * parcel.rows * 3, albedo.size)
        assertEquals(true, albedo.all { it in 0f..1f })
    }
}
