package plottwin.render

// The looks constants whose right value is the owner's taste and not a measurement: what the
// neighbours' land is made of, how much haze it is seen through, and how tightly the sun's glow
// gathers. Carried as one argument so a render can be asked for at a taste other than the
// standing one without any caller that does not care learning about the knobs.
data class LooksTaste(
    val surroundBaseHaze: Float = SURROUND_BASE_HAZE,
    val surroundAlbedo: Rgb = SURROUND_ALBEDO,
    val sunGlowTightness: Float = SUN_GLOW_TIGHTNESS,
)
