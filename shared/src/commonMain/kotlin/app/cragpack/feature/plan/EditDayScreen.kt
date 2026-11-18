package app.cragpack.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtSwitchRow
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.format.formatDayShort
import app.cragpack.feature.sector.NewSectorSheet
import app.cragpack.feature.sector.SectorChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [approach] edits the chosen sector's line ("5 min approach · dry from 11:00"), shared by every day there. */
data class DayForm(val sectorId: String? = null, val isRest: Boolean = false, val notes: String = "", val approach: String = "")

class EditDayViewModel(private val graph: AppGraph, private val tripId: String, private val dayId: String) : ViewModel() {
    var form by mutableStateOf(DayForm())
    var title by mutableStateOf("")
    val sectors = graph.objectives.observeSectors(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val day = graph.plan.observeDays(tripId).first().firstOrNull { it.id == dayId } ?: return@launch
            title = formatDayShort(day.date)
            form = DayForm(day.sector?.id, day.isRest, day.notes, day.sector?.approach.orEmpty())
        }
    }

    fun selectSector(id: String?) {
        form = form.copy(sectorId = id, approach = sectors.value.firstOrNull { it.id == id }?.approach.orEmpty())
    }

    fun addSector(name: String) = viewModelScope.launch {
        val id = withContext(Dispatchers.IO) { graph.objectives.addSector(tripId, name) }
        form = form.copy(sectorId = id, approach = "")
    }

    suspend fun save() = withContext(Dispatchers.IO) {
        val f = form
        graph.plan.updateDay(dayId, f.sectorId, f.isRest, f.notes)
        if (f.sectorId != null) graph.plan.setApproach(f.sectorId, f.approach)
    }
}

/** Edit day (22). */
@Composable
fun EditDayScreen(vm: EditDayViewModel, onClose: () -> Unit) {
    val sectors by vm.sectors.collectAsStateWithLifecycle()
    var newSector by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val form = vm.form
    CtScreen(
        dock = { CtaDock("Save", LucideIcon.Check, onClick = { scope.launch { vm.save(); onClose() } }) },
        overlay = { NewSectorSheet(newSector, { vm.addSector(it); newSector = false }, { newSector = false }) },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.X, "Close", onClose) },
                center = { CtText(vm.title, style = CragpackTheme.type.title) },
            )
            Label("Sector")
            SectorChips(sectors, form.sectorId, vm::selectSector) { newSector = true }
            if (form.sectorId != null) {
                CtTextField(form.approach, { vm.form = form.copy(approach = it) }, "Approach · conditions", placeholder = "e.g. 5 min approach · dry from 11:00")
            }
            CtSwitchRow("Rest day", form.isRest, { vm.form = form.copy(isRest = it) })
            CtTextField(form.notes, { vm.form = form.copy(notes = it) }, "Notes", placeholder = "e.g. Warm up on the circuit first.", area = true)
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Label(text: String) =
    CtText(text, Modifier.padding(start = 18.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2)
