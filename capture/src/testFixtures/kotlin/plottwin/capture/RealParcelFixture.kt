package plottwin.capture

// 90x90 cells at 1m, compiled by capture/scripts/compile_parcel.py from the real 3DEP tile + NAIP clip
object RealParcelFixture {
    const val RESOURCE = "/real_parcel_1m_90x90.json"
    const val FEATURES_RESOURCE = "/real_parcel_features.json"

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
}
