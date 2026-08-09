package plottwin.worldstate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Measured is the distinguished surface; every proposal is a named branch off a measured baseline.
@Serializable
sealed interface Surface {

    @Serializable
    @SerialName("measured")
    data object Measured : Surface

    @Serializable
    @SerialName("proposed")
    data class Proposed(val name: String) : Surface
}
