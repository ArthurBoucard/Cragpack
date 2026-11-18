package app.cragpack.domain.log

import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeScale

/** Stored as [key]. Sent and Flash are sends; Attempt is not. */
enum class LogStyle(val key: String, val label: String) {
    Attempt("attempt", "Attempt"), Sent("sent", "Sent"), Flash("flash", "Flash");

    val isSend get() = this != Attempt

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

/** One bar of the grade pyramid: label like "6A–6C+", the band, and how many sends. */
data class PyramidRow(val label: String, val band: Int, val count: Int)

/**
 * Sends per band, hardest first. Bands 1–4 share one "≤4+" row like the design.
 * Labels use [scale]'s grades (Font for boulders, French for routes).
 */
fun pyramid(sends: List<Grade>, scale: GradeScale): List<PyramidRow> {
    val all = Grade.all(scale)
    fun range(bands: IntRange): String {
        val inBand = all.filter { it.band in bands }
        return if (bands.first <= 1) "≤${inBand.last().value}" else "${inBand.first().value}–${inBand.last().value}"
    }
    return sends.groupBy { if (it.band <= 4) 4 else it.band }.toSortedMap(compareByDescending { it }).map { (band, list) ->
        PyramidRow(if (band == 4) range(1..4) else range(band..band), band, list.size)
    }
}

/** Hardest of a list: highest band, then highest grade within the same scale. */
fun hardest(grades: List<Grade>): Grade? = grades.maxWithOrNull(compareBy<Grade> { it.band }.thenBy { it.sortIndex })
