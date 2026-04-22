package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClubType(val displayName: String, val shortName: String) {
    @SerialName("unknown")  UNKNOWN("Unknown", "?"),
    @SerialName("driver")   DRIVER("Driver", "D"),
    @SerialName("3w")       WOOD_3("3 Wood", "3W"),
    @SerialName("5w")       WOOD_5("5 Wood", "5W"),
    @SerialName("3h")       HYBRID_3("3 Hybrid", "3H"),
    @SerialName("4h")       HYBRID_4("4 Hybrid", "4H"),
    @SerialName("5h")       HYBRID_5("5 Hybrid", "5H"),
    @SerialName("4i")       IRON_4("4 Iron", "4i"),
    @SerialName("5i")       IRON_5("5 Iron", "5i"),
    @SerialName("6i")       IRON_6("6 Iron", "6i"),
    @SerialName("7i")       IRON_7("7 Iron", "7i"),
    @SerialName("8i")       IRON_8("8 Iron", "8i"),
    @SerialName("9i")       IRON_9("9 Iron", "9i"),
    @SerialName("pw")       PITCHING_WEDGE("Pitching Wedge", "PW"),
    @SerialName("gw")       GAP_WEDGE("Gap Wedge", "GW"),
    @SerialName("sw")       SAND_WEDGE("Sand Wedge", "SW"),
    @SerialName("lw")       LOB_WEDGE("Lob Wedge", "LW"),
    @SerialName("putter")   PUTTER("Putter", "P");

    companion object {
        val defaultBag = listOf(
            DRIVER, WOOD_3, WOOD_5,
            IRON_4, IRON_5, IRON_6, IRON_7, IRON_8, IRON_9,
            PITCHING_WEDGE, GAP_WEDGE, SAND_WEDGE, LOB_WEDGE,
        )

        val allSelectable = entries.filter { it != UNKNOWN && it != PUTTER }
    }
}
