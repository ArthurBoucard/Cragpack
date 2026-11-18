package app.cragpack.feature.logbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.CtFieldButton
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtSegmented
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.DateRangeSheet
import app.cragpack.designsystem.component.GradePicker
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDayShort
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.log.LogStyle
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.feature.StyleGradePicker
import app.cragpack.feature.keptFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

data class LogSendForm(
    val name: String = "",
    val crag: String = "",
    val style: ClimbStyle = ClimbStyle.Boulder,
    val grade: Grade? = null,
    val result: LogStyle = LogStyle.Sent,
    val date: LocalDate,
) {
    val canLog get() = name.isNotBlank() && grade != null
}

class LogSendViewModel(private val graph: AppGraph) : ViewModel() {
    val today = graph.today()
    var form by mutableStateOf(LogSendForm(date = today))

    suspend fun log() = withContext(Dispatchers.IO) {
        val f = form
        graph.logs.logOutside(f.name, f.grade!!, f.crag, f.date, f.result)
    }
}

/** "Log a send" outside a trip, from My logbook: Add objective's fields plus the result and the date. */
@Composable
fun LogSendScreen(vm: LogSendViewModel, onClose: () -> Unit) {
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val form = vm.form
    CtScreen(
        dock = { CtaDock("Log it", LucideIcon.Check, enabled = form.canLog, onClick = { scope.launch { vm.log(); onClose() } }) },
        overlay = {
            DateRangeSheet(
                visible = dateOpen, start = form.date, end = form.date, today = vm.today, single = true,
                onDone = { d, _ -> vm.form = form.copy(date = d); dateOpen = false },
                onDismiss = { dateOpen = false },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.X, "Close", onClose) },
                center = { CtText("Log a send", style = CragpackTheme.type.title) },
            )
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            CtTextField(form.name, { vm.form = form.copy(name = it) }, "Name", placeholder = "e.g. Las Cuevas", keyboardOptions = words)
            CtTextField(form.crag, { vm.form = form.copy(crag = it) }, "Crag", placeholder = "e.g. Albarracín", keyboardOptions = words)
            CtSegmented(ClimbStyle.entries.map { it.label }, form.style.ordinal, {
                val s = ClimbStyle.entries[it]
                vm.form = form.copy(style = s, grade = form.grade.keptFor(s))
            })
            Label("Grade")
            StyleGradePicker(form.style, form.grade, { vm.form = form.copy(grade = it) }, Modifier.padding(horizontal = 3.dp))
            CtSegmented(LogStyle.entries.map { it.label }, form.result.ordinal, { vm.form = form.copy(result = LogStyle.entries[it]) })
            CtFieldButton("Date", formatDayShort(form.date), "", { dateOpen = true })
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Label(text: String) =
    CtText(text, Modifier.padding(start = 18.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2)
