package plottwin.render

// The two looks constants whose right value is the owner's taste and not a measurement: how much
// haze the neighbours' land is seen through, and how tightly the sun's glow gathers. Carried as
// one argument so a render can be asked for at a taste other than the standing one without any
// caller that does not care learning about either knob.
data class LooksTaste(
    val surroundBaseHaze: Float = SURROUND_BASE_HAZE,
    val sunGlowTightness: Float = SUN_GLOW_TIGHTNESS,
)
