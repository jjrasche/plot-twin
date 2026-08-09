package plottwin.capture

// 90x90 cells at 1m, compiled by capture/scripts/compile_parcel.py from the real 3DEP tile + NAIP clip
object RealParcelFixture {
    const val RESOURCE = "/real_parcel_1m_90x90.json"

    fun parcel(): CompiledParcel {
        val json = requireNotNull(RealParcelFixture::class.java.getResource(RESOURCE)) {
            "missing committed fixture $RESOURCE — run capture/scripts/compile_parcel.py"
        }.readText()
        return compiledParcelOf(json)
    }
}
