package app.cragpack.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.cragpack.designsystem.component.GradePicker
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.grade.GradeDisplay
import app.cragpack.domain.grade.GradeScale
import app.cragpack.domain.objective.ClimbStyle

/** The Settings "Boulder grades" choice (D7), provided once at the root of the app. */
val LocalGradeDisplay = staticCompositionLocalOf { GradeDisplay.AsEntered }

/** A grade as this phone shows it. Stored grades never change (D7); only the text does. */
@Composable
@ReadOnlyComposable
fun Grade.text(): String = display(LocalGradeDisplay.current)

/** The scale new grades are picked in: routes are French; boulders are V for climbers who chose V, Font otherwise. */
@Composable
@ReadOnlyComposable
fun ClimbStyle.entryScale(): GradeScale = when {
    this != ClimbStyle.Boulder -> GradeScale.French
    LocalGradeDisplay.current == GradeDisplay.V -> GradeScale.V
    else -> GradeScale.Font
}

/** The grade picker for a [style]: every grade of its entry scale, with [grade] ringed. Used by both climb forms. */
@Composable
fun StyleGradePicker(style: ClimbStyle, grade: Grade?, onGrade: (Grade) -> Unit, modifier: Modifier = Modifier) {
    val scale = style.entryScale()
    GradePicker(Grade.all(scale).map { it.value to it.band }, grade?.valueIn(scale), { onGrade(Grade(scale, it)) }, modifier)
}

/** Switching between boulder and route clears the grade; Sport ↔ Trad keeps it. */
fun Grade?.keptFor(style: ClimbStyle): Grade? = this?.takeIf { it.isBoulder == (style == ClimbStyle.Boulder) }

/** [grade]'s text in [scale], so the picker can ring it (a Font grade shows as its V equivalent in a V picker). */
fun Grade.valueIn(scale: GradeScale): String = when {
    this.scale == scale || scale == GradeScale.French -> value
    scale == GradeScale.V -> display(GradeDisplay.V)
    else -> display(GradeDisplay.Font)
}
