package plottwin.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GEOCODE_SITE_TOLERANCE_METERS = 30.0

private fun fixtureGeocode(): GeocodedAddress {
    val json = requireNotNull(GeocodedAddress::class.java.getResource("/geocode_delta_township_office.json")) {
        "missing committed census geocode fixture"
    }.readText()
    return geocodedAddressOf(json)
}

class GeocodeStageGateTest {

    @Test
    fun census_geocoder_output_parses_with_coordinates_in_michigan() {
        val located = fixtureGeocode()
        assertEquals("7710 W SAGINAW HWY, LANSING, MI, 48917", located.matchedAddress)
        assertTrue(located.latitudeDegrees in 41.7..47.5 && located.longitudeDegrees in -90.5..-82.1,
            "geocode landed outside Michigan: ${located.latitudeDegrees}, ${located.longitudeDegrees}")
    }

    @Test
    fun ground_distance_matches_a_degree_of_latitude() {
        assertEquals(111_195.0, groundDistanceMeters(42.0, -84.0, 43.0, -84.0), 100.0)
    }

    @Test
    fun the_parcel_address_geocodes_onto_the_site_row() {
        val located = readGeocodedAddress(CaptureCache.ownerGeocode())
        val site = siteRowOf(RealParcelFixture.parcel())
        val distance = groundDistanceMeters(
            located.latitudeDegrees, located.longitudeDegrees,
            site.latitudeDegrees, site.longitudeDegrees,
        )
        println("[geocode] '${located.matchedAddress}' lands %.1f m from the site row".format(distance))
        assertTrue(distance <= GEOCODE_SITE_TOLERANCE_METERS,
            "geocoded address is ${"%.1f".format(distance)} m from the site row (band $GEOCODE_SITE_TOLERANCE_METERS m)")
    }
}
