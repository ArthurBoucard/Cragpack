package app.cragpack.feature.sector

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.cragpack.designsystem.component.ChipRow
import app.cragpack.designsystem.component.CtBottomSheet
import app.cragpack.designsystem.component.CtButton
import app.cragpack.designsystem.component.CtButtonSize
import app.cragpack.designsystem.component.CtChip
import app.cragpack.designsystem.component.CtTextField
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.domain.objective.Sector

/** Sector chips (tap the selected one again to clear it) and a + that asks for a new sector. */
@Composable
fun SectorChips(sectors: List<Sector>, selected: String?, onSelect: (String?) -> Unit, onNew: () -> Unit) = ChipRow {
    sectors.forEach { s -> CtChip(s.name, selected = s.id == selected, onClick = { onSelect(s.id.takeIf { it != selected }) }) }
    CtChip(null, selected = false, onClick = onNew, icon = LucideIcon.Plus, contentDescription = "New sector")
}

/** Sheet that names a new sector. Place it in the screen overlay. */
@Composable
fun BoxScope.NewSectorSheet(visible: Boolean, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable(visible) { mutableStateOf("") }
    CtBottomSheet(visible = visible, title = "New sector", onDismiss = onDismiss) {
        CtTextField(
            name, { name = it }, "Name", placeholder = "e.g. Bas Cuvier",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        CtButton("Add sector", { onAdd(name) }, size = CtButtonSize.Wide, enabled = name.isNotBlank())
    }
}
