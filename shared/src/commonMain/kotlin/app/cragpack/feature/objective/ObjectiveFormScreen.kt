package app.cragpack.feature.objective

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.cragpack.data.AppGraph
import app.cragpack.designsystem.component.ChipRow
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtIconButton
import app.cragpack.designsystem.component.CtScreen
import app.cragpack.designsystem.component.CtSegmented
import app.cragpack.designsystem.component.CtText
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.component.CtaDock
import app.cragpack.designsystem.component.GradePicker
import app.cragpack.designsystem.component.TopBar
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens
import app.cragpack.domain.grade.Grade
import app.cragpack.domain.objective.ClimbStyle
import app.cragpack.feature.StyleGradePicker
import app.cragpack.feature.keptFor
import app.cragpack.feature.sector.NewSectorSheet
import app.cragpack.feature.sector.SectorChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ObjectiveForm(val name: String = "", val style: ClimbStyle = ClimbStyle.Boulder, val grade: Grade? = null, val sectorId: String? = null) {
    val canSave get() = name.isNotBlank() && grade != null

    fun withStyle(s: ClimbStyle) = copy(style = s, grade = grade.keptFor(s))
}

/** Add objective (12), or edit one when [objectiveId] is set (the ⋮ on boulder detail). */
class ObjectiveFormViewModel(private val graph: AppGraph, private val tripId: String, private val objectiveId: String?) : ViewModel() {
    var form by mutableStateOf(ObjectiveForm())
    val editing = objectiveId != null
    val sectors = graph.objectives.observeSectors(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (objectiveId != null) viewModelScope.launch {
            val o = graph.objectives.observeObjective(objectiveId).first()?.objective ?: return@launch
            form = ObjectiveForm(o.name, o.style, o.grade, o.sector?.id)
        }
    }

    fun addSector(name: String) = viewModelScope.launch(Dispatchers.IO) {
        val id = graph.objectives.addSector(tripId, name)
        form = form.copy(sectorId = id)
    }

    /** Returns the objective's id. */
    suspend fun save(): String = withContext(Dispatchers.IO) {
        val f = form
        val grade = f.grade!!
        if (objectiveId != null) objectiveId.also { graph.objectives.update(it, f.name, f.style, grade, f.sectorId) }
        else graph.objectives.add(tripId, f.name, f.style, grade, f.sectorId)
    }
}

@Composable
fun ObjectiveFormScreen(vm: ObjectiveFormViewModel, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val sectors by vm.sectors.collectAsStateWithLifecycle()
    var newSector by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val form = vm.form
    CtScreen(
        dock = {
            CtaDock(
                if (vm.editing) "Save" else "Add to projects",
                if (vm.editing) LucideIcon.Check else LucideIcon.Plus,
                enabled = form.canSave,
                onClick = { scope.launch { onSaved(vm.save()) } },
            )
        },
        overlay = { NewSectorSheet(newSector, { vm.addSector(it); newSector = false }, { newSector = false }) },
    ) { bottom ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = SizeTokens.edge),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopBar(
                leading = { CtIconButton(LucideIcon.X, "Close", onClose) },
                center = { CtText(if (vm.editing) "Edit project" else "Add project", style = CragpackTheme.type.title) },
            )
            CtTextField(
                form.name, { vm.form = form.copy(name = it) }, "Name", placeholder = "e.g. La Marie-Rose",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            CtSegmented(ClimbStyle.entries.map { it.label }, form.style.ordinal, { vm.form = form.withStyle(ClimbStyle.entries[it]) })
            Label("Grade")
            StyleGradePicker(form.style, form.grade, { vm.form = form.copy(grade = it) }, Modifier.padding(horizontal = 3.dp))
            Label("Sector")
            SectorChips(sectors, form.sectorId, { vm.form = form.copy(sectorId = it) }, { newSector = true })
            // ponytail: Pin location needs the phone's location; it comes with the map (out of v1, D11 stores pins only).
            Spacer(Modifier.height(bottom.calculateBottomPadding()))
        }
    }
}

@Composable
private fun Label(text: String) =
    CtText(text, Modifier.padding(start = 18.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2)
