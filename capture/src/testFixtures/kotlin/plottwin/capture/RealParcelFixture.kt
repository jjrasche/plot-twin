package plottwin.capture

// The parcel boundary's bounding box at 1m, compiled by capture/scripts/compile_parcel.py from the
// real 3DEP tile + NAIP clip; the 10cm cut of the same bbox is capture/data/compiled/parcel.json
object RealParcelFixture {
    const val RESOURCE = "/real_parcel_1m.json"
    const val FEATURES_RESOURCE = "/real_parcel_features.json"
    const val BOUNDARY_RESOURCE = "/real_parcel_boundary.json"

    fun parcel(): CompiledParcel {
        val json = requireNotNull(RealParcelFixture::class.java.getResource(RESOURCE)) {
            "missing committed fixture $RESOURCE — run capture/scripts/compile_parcel.py"
        }.readText()
        return compiledParcelOf(json)
    }

    fun features(): ParcelFeatures {
        val json = requireNotNull(RealParcelFixture::class.java.getResource(FEATURES_RESOURCE)) {
            "missing committed fixture $FEATURES_RESOURCE — run capture/scripts/extract_features.py"
        }.readText()
        return parcelFeaturesOf(json)
    }

    fun boundary(): CapturedBoundary {
        val json = requireNotNull(RealParcelFixture::class.java.getResource(BOUNDARY_RESOURCE)) {
            "missing committed fixture $BOUNDARY_RESOURCE — run capture/scripts/fetch_parcel_boundary.py"
        }.readText()
        return capturedBoundaryOf(json)
    }
}
