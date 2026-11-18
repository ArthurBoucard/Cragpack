package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.cragpack.designsystem.tokens.RadiusTokens
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.SizeTokens

/**
 * `.field` + `.input` — label above, 56-high pill input; the focused one gets a 2-wide ink outline.
 * [area] makes it the multiline notes box. [trailing] sits inside the pill on the right (the link box's Add).
 */
@Composable
fun CtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    area: Boolean = false,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = CragpackTheme.colors
    val style = when {
        mono -> CragpackTheme.type.mono
        area -> CragpackTheme.type.body.copy(fontSize = CragpackTheme.type.rowName.fontSize, lineHeight = CragpackTheme.type.rowName.fontSize * 1.4f)
        else -> CragpackTheme.type.rowName
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Field(label, modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = style.copy(color = c.ink),
            singleLine = !area,
            minLines = if (area) 4 else 1,
            cursorBrush = SolidColor(c.ink),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            decorationBox = { inner ->
                InputBox(focused, area = area, trailing = trailing) {
                    if (value.isEmpty()) CtText(placeholder, style = style, color = c.ink2, maxLines = if (area) Int.MAX_VALUE else 1)
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
    Field(label, modifier) {
        InputBox(focused = false, Modifier.ctClickable(true, Role.Button, CircleShape, onClick)) {
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
private fun InputBox(
    focused: Boolean,
    modifier: Modifier = Modifier,
    area: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = CragpackTheme.colors
    val shape = if (area) RoundedCornerShape(RadiusTokens.md) else CircleShape
    Row(
        modifier
            .fillMaxWidth()
            .then(if (area) Modifier.heightIn(min = 140.dp) else Modifier.height(SizeTokens.input))
            .clip(shape)
            .background(c.surface)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.ink else c.line, shape)
            .padding(start = 20.dp, end = if (trailing != null) 6.dp else 20.dp, top = if (area) 16.dp else 0.dp, bottom = if (area) 16.dp else 0.dp),
        verticalAlignment = if (area) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = if (area) Alignment.TopStart else Alignment.CenterStart, content = content)
        trailing?.invoke()
    }
}
