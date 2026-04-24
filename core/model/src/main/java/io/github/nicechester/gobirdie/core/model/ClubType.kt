package io.github.nicechester.gobirdie.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClubType(val displayName: String, val shortName: String, val serialName: String) {
    @SerialName("unknown")  UNKNOWN("Unknown", "?", "unknown"),
    @SerialName("driver")   DRIVER("Driver", "D", "driver"),
    @SerialName("3w")       WOOD_3("3 Wood", "3W", "3w"),
    @SerialName("5w")       WOOD_5("5 Wood", "5W", "5w"),
    @SerialName("3h")       HYBRID_3("3 Hybrid", "3H", "3h"),
    @SerialName("4h")       HYBRID_4("4 Hybrid", "4H", "4h"),
    @SerialName("5h")       HYBRID_5("5 Hybrid", "5H", "5h"),
    @SerialName("4i")       IRON_4("4 Iron", "4i", "4i"),
    @SerialName("5i")       IRON_5("5 Iron", "5i", "5i"),
    @SerialName("6i")       IRON_6("6 Iron", "6i", "6i"),
    @SerialName("7i")       IRON_7("7 Iron", "7i", "7i"),
    @SerialName("8i")       IRON_8("8 Iron", "8i", "8i"),
    @SerialName("9i")       IRON_9("9 Iron", "9i", "9i"),
    @SerialName("pw")       PITCHING_WEDGE("Pitching Wedge", "PW", "pw"),
    @SerialName("gw")       GAP_WEDGE("Gap Wedge", "GW", "gw"),
    @SerialName("sw")       SAND_WEDGE("Sand Wedge", "SW", "sw"),
    @SerialName("lw")       LOB_WEDGE("Lob Wedge", "LW", "lw"),
    @SerialName("putter")   PUTTER("Putter", "P", "putter");

    companion object {
        val defaultBag = listOf(
            DRIVER, WOOD_3, WOOD_5,
            IRON_4, IRON_5, IRON_6, IRON_7, IRON_8, IRON_9,
            PITCHING_WEDGE, GAP_WEDGE, SAND_WEDGE, LOB_WEDGE,
        )

        val allSelectable = entries.filter { it != UNKNOWN && it != PUTTER }
    }
}
