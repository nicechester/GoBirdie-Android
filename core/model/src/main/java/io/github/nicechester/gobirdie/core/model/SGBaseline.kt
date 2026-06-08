package io.github.nicechester.gobirdie.core.model

enum class SGBaseline(val displayName: String) {
    SCRATCH("Scratch"),
    SINGLE("Single Digit"),
    BOGEY("Bogey Golfer"),
    HIGH_CAP("High Handicap");

    /** Expected SG per category vs scratch baseline. */
    data class Expected(val offTee: Double, val approach: Double, val shortGame: Double, val putting: Double)

    val expected: Expected get() = when (this) {
        SCRATCH  -> Expected( 0.0,  0.0,  0.0,  0.0)
        SINGLE   -> Expected(-0.8, -1.5, -0.9, -0.8)
        BOGEY    -> Expected(-1.8, -3.5, -2.0, -1.7)
        HIGH_CAP -> Expected(-3.2, -6.0, -3.5, -3.3)
    }
}
