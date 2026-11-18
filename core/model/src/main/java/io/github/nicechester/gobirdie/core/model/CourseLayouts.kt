package io.github.nicechester.gobirdie.core.model

/** Helpers for the layouts of a multi-course complex (one [Course] per layout, named "Club – Layout"). */
object CourseLayouts {

    /**
     * Joins layouts into one course for a round, e.g. two 9-hole layouts make 18 holes.
     * Holes are renumbered 1…N in the order given. Par, yardage, and geometry stay with each hole.
     * Name: "Club – Creek + Oaks". Returns null for an empty list.
     */
    fun combine(layouts: List<Course>): Course? {
        val first = layouts.firstOrNull() ?: return null

        val holes = mutableListOf<Hole>()
        for (layout in layouts) {
            for (hole in layout.holes) holes += hole.copy(number = holes.size + 1)
        }

        val club = clubName(first.name)
        val layoutNames = layouts.map { layoutName(it.name) }
        return Course(
            id = layouts.joinToString("+") { it.id },
            name = "$club – ${layoutNames.joinToString(" + ")}",
            location = first.location,
            holes = holes,
            downloadedAt = java.time.Instant.now().toString(),
            osmVersion = first.osmVersion,
        )
    }

    /** "Temecula Creek Inn Golf Course – Creek" -> "Temecula Creek Inn Golf Course" */
    fun clubName(name: String): String = name.substringBefore(" – ")

    /** "Temecula Creek Inn Golf Course – Creek" -> "Creek". A name with no layout part is returned unchanged. */
    fun layoutName(name: String): String =
        if (name.contains(" – ")) name.substringAfter(" – ") else name
}
