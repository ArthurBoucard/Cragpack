package app.cragpack.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cragpack.data.db.CragpackDb
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.settings.Settings
import app.cragpack.domain.settings.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Device-only preferences, kept as key/value rows. Unknown or missing values fall back to the defaults. */
class SettingsRepository internal constructor(private val db: CragpackDb) {
    private val q get() = db.settingQueries

    /** Read once at start-up so the first frame already has the right theme. */
    fun current(): Settings = parse(q.selectAll().executeAsList().associate { it.name to it.data_ })

    fun observe(): Flow<Settings> = q.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows -> parse(rows.associate { it.name to it.data_ }) }

    fun observeStorageBytes(): Flow<Long> = q.selectStorageBytes().asFlow().mapToOne(Dispatchers.IO)

    fun setGradeDisplay(value: GradeDisplay) = q.put(GRADES, value.name)
    fun setTheme(value: Theme) = q.put(THEME, value.name)
    fun setWifiOnly(value: Boolean) = q.put(WIFI_ONLY, value.toString())

    private fun parse(map: Map<String, String>): Settings {
        val defaults = Settings()
        return Settings(
            gradeDisplay = map[GRADES]?.let { v -> GradeDisplay.entries.firstOrNull { it.name == v } } ?: defaults.gradeDisplay,
            theme = map[THEME]?.let { v -> Theme.entries.firstOrNull { it.name == v } } ?: defaults.theme,
            wifiOnly = map[WIFI_ONLY]?.toBooleanStrictOrNull() ?: defaults.wifiOnly,
        )
    }

    private companion object {
        const val GRADES = "grade_display"
        const val THEME = "theme"
        const val WIFI_ONLY = "wifi_only"
    }
}
