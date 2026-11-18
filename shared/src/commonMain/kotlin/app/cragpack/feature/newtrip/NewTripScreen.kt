package app.cragpack.feature.newtrip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.DateRangeSheet
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDayShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

data class NewTripForm(val name: String = "", val crag: String = "", val start: LocalDate? = null, val end: LocalDate? = null) {
    val canCreate get() = name.isNotBlank() && start != null && end != null
}

class NewTripViewModel(private val graph: AppGraph) : ViewModel() {
    var form by mutableStateOf(NewTripForm())
    val today: LocalDate = graph.today()

    /** Returns the new trip's id. */
    suspend fun create(): String = withContext(Dispatchers.IO) {
        val f = form
        graph.trips.create(f.name, f.crag, f.start!!, f.end!!)
    }
}

/** Screen 09. ponytail: the cover photo picker arrives with media (beta slice). */
@Composable
fun NewTripScreen(vm: NewTripViewModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    var datesOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val form = vm.form
    CtScreen(
        dock = {
            CtaDock("Create trip", LucideIcon.Check, enabled = form.canCreate, onClick = {
                scope.launch { onCreated(vm.create()) }
            })
        },
        overlay = {
            DateRangeSheet(
                visible = datesOpen,
                start = form.start,
                end = form.end,
                today = vm.today,
                onDone = { s, e -> vm.form = form.copy(start = s, end = e); datesOpen = false },
                onDismiss = { datesOpen = false },
            )
        },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.X, "Close", onClose) },
                center = { CtText("New trip", style = CragpackTheme.type.title) },
            )
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            CtTextField(form.name, { vm.form = form.copy(name = it) }, "Name", placeholder = "e.g. Font Easter", keyboardOptions = words)
            CtTextField(form.crag, { vm.form = form.copy(crag = it) }, "Crag", placeholder = "e.g. Fontainebleau", keyboardOptions = words)
            Row(horizontalArrangement = Arrangement.spacedBy(SizeTokens.gap)) {
                CtFieldButton("Start", form.start?.let(::formatDayShort), "Pick dates", { datesOpen = true }, Modifier.weight(1f))
                CtFieldButton("End", form.end?.let(::formatDayShort), "–", { datesOpen = true }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}
