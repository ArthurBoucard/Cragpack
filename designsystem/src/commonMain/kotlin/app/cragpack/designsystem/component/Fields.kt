package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens

/** `.field` + `.input` — label above, 56-high pill input; the focused one gets a 2-wide ink outline. */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val c = CragpackTheme.colors
    val style = CragpackTheme.type.rowName
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Field(label, modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = style.copy(color = c.ink),
            singleLine = true,
            cursorBrush = SolidColor(c.ink),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            decorationBox = { inner ->
                InputBox(focused) {
                    if (value.isEmpty()) CtText(placeholder, style = style, color = c.ink2, maxLines = 1)
                    inner()
                }
            },
        )
    }
}

/** A field that opens a picker (dates, sector) instead of the keyboard. Shows [placeholder] while [text] is null. */
@Composable
fun CtFieldButton(label: String, text: String?, placeholder: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val focus = LocalFocusManager.current
    Field(label, modifier) {
        // Leaving a text field closes the keyboard so the picker sheet has room.
        InputBox(focused = false, Modifier.ctClickable(true, Role.Button, CircleShape) { focus.clearFocus(); onClick() }) {
            CtText(text ?: placeholder, style = CragpackTheme.type.rowName, color = if (text == null) c.ink2 else c.ink, maxLines = 1)
        }
    }
}

@Composable
private fun Field(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CtText(label, Modifier.padding(horizontal = 18.dp), style = CragpackTheme.type.label, color = CragpackTheme.colors.ink2)
        content()
    }
}

@Composable
private fun InputBox(focused: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = CragpackTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(SizeTokens.input)
            .clip(CircleShape)
            .background(c.surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.ink else c.line, CircleShape)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
        content = content,
    )
}
