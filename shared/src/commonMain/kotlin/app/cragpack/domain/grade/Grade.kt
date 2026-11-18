package app.cragpack.domain.grade

enum class GradeScale { Font, V, French }

/** User setting: how boulder grades are shown. Never changes what is stored (D7). French route grades always show as entered. */
enum class GradeDisplay { AsEntered, Font, V }

internal class GradeRow(val band: Int, val index: Int, val equivalent: String?)

/** A grade stored exactly as entered: scale + value (D7). Use [Grade.of] for untrusted input. */
data class Grade(val scale: GradeScale, val value: String) {
    private val row: GradeRow = requireNotNull(table(scale)[value]) { "Unknown $scale grade: $value" }

    /** Colour band 1..9: the first digit; V grades take their Font equivalent's band (D8). */
    val band: Int get() = row.band

    /** Sort order within one discipline. Font and V share boulder indexes; French routes have their own. */
    val sortIndex: Int get() = row.index

    val isBoulder: Boolean get() = scale != GradeScale.French

    fun display(setting: GradeDisplay): String = when {
        scale == GradeScale.French || setting == GradeDisplay.AsEntered -> value
        setting == GradeDisplay.Font && scale == GradeScale.Font -> value
        setting == GradeDisplay.V && scale == GradeScale.V -> value
        else -> row.equivalent ?: value
    }

    companion object {
        fun of(scale: GradeScale, value: String): Grade? = if (value in table(scale)) Grade(scale, value) else null

        /** Every grade of a scale in sort order, for the grade picker. */
        fun all(scale: GradeScale): List<Grade> = table(scale).keys.map { Grade(scale, it) }

        private fun table(scale: GradeScale): Map<String, GradeRow> = when (scale) {
            GradeScale.Font -> GradeTable.font
            GradeScale.V -> GradeTable.v
            GradeScale.French -> GradeTable.french
        }
    }
}
