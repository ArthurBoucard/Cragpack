package app.cragpack.domain.settings

import app.cragpack.domain.grade.GradeDisplay

enum class Theme { Auto, Dark, Sunlight }

/** This phone's preferences. Defaults: grades as entered (D7), theme follows the system (D10), media waits for Wi-Fi. */
data class Settings(
    val gradeDisplay: GradeDisplay = GradeDisplay.AsEntered,
    val theme: Theme = Theme.Auto,
    val wifiOnly: Boolean = true,
)

/** "820 KB", "12 MB", "1.2 GB". */
fun formatBytes(bytes: Long): String = when {
    bytes < 1_000_000 -> "${(bytes + 999) / 1_000} KB"
    bytes < 1_000_000_000 -> "${(bytes + 500_000) / 1_000_000} MB"
    else -> "${bytes / 1_000_000_000}.${bytes % 1_000_000_000 / 100_000_000} GB"
}
