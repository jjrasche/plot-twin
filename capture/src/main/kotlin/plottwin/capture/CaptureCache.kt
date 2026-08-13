package plottwin.capture

import java.nio.file.Files
import java.nio.file.Path

// The capture cache is gitignored (capture/data/.gitignore is `*`), so a fresh clone has none of
// it. Absence therefore has to be louder than presence: a gate that quietly steps aside when its
// input is missing reports that the instrument passed when it never launched. Every reader of a
// cached artifact goes through here, and every failure names the command that makes the artifact.
object CaptureCache {

    const val COMPILE_PARCEL = "python capture/scripts/compile_parcel.py 42.68317626142 -84.619591093007"
    const val EXTRACT_FEATURES = "python capture/scripts/extract_features.py"
    const val FETCH_BOUNDARY = "python capture/scripts/fetch_parcel_boundary.py"
    const val GEOCODE = "python capture/scripts/geocode.py \"<the owner address, kept out of the repo>\""

    val dataDir: Path get() = repoRoot().resolve("capture").resolve("data")

    fun compiledParcel(): Path = presentOrLoud(dataDir.resolve("compiled").resolve("parcel.json"), COMPILE_PARCEL)

    fun compiledFeatures(): Path = presentOrLoud(dataDir.resolve("compiled").resolve("features.json"), EXTRACT_FEATURES)

    fun boundary(): Path = presentOrLoud(dataDir.resolve("boundary").resolve("boundary.json"), FETCH_BOUNDARY)

    fun ownerGeocode(): Path = presentOrLoud(dataDir.resolve("geocode.json"), GEOCODE)
}

// Gradle gives each module its own working directory, so nothing that spans modules - the capture
// cache, the receipts tree - can be addressed by a fixed relative path from any one of them.
fun repoRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    while (candidate != null) {
        if (Files.exists(candidate.resolve("settings.gradle.kts"))) return candidate
        candidate = candidate.parent
    }
    throw AssertionError("no settings.gradle.kts above ${System.getProperty("user.dir")}: cannot find the repo root")
}

// A missing capture artifact is a failure with a cure, so the message carries the cure.
fun presentOrLoud(path: Path, regenerateWith: String): Path {
    if (Files.exists(path)) return path
    throw AssertionError(
        "capture artifact absent: $path\n" +
            "  the gate that needs it did NOT run. Regenerate it with:\n" +
            "    $regenerateWith\n" +
            "  (capture/data is gitignored, so a fresh clone has to rebuild it.)"
    )
}
