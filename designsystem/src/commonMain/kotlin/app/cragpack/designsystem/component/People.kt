package app.cragpack.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.ColorTokens

enum class AvatarSize(internal val box: Dp, internal val text: TextUnit) {
    Sm(26.dp, 12.sp), Md(36.dp, 14.sp), Lg(40.dp, 15.sp), Xl(52.dp, 19.sp)
}

/**
 * `.av` — a climber: their initial on their join-order colour (design rule 6).
 * A blank [name] (no account yet) shows the generic person icon on a neutral circle.
 */
@Composable
fun Avatar(name: String, colorIndex: Int, modifier: Modifier = Modifier, size: AvatarSize = AvatarSize.Md, onClick: (() -> Unit)? = null) {
    val c = CragpackTheme.colors
    val anonymous = name.isBlank()
    Box(
        modifier
            .size(size.box)
            .then(if (onClick != null) Modifier.ctClickable(true, Role.Button, CircleShape, onClick) else Modifier)
            .semantics { contentDescription = name.ifBlank { "You" } }
            .clip(CircleShape)
            .background(if (anonymous) c.surface2 else ColorTokens.people[(colorIndex - 1).mod(ColorTokens.people.size)])
            .border(if (anonymous) 1.dp else 2.dp, if (anonymous) c.line else c.ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (anonymous) {
            CtIcon(LucideIcon.UserRound, null, size = size.box * 0.46f)
        } else {
            CtText(
                name.trim().first().uppercase(),
                style = CragpackTheme.type.body.copy(fontSize = size.text, fontWeight = FontWeight.Bold),
                color = Color(0xFF1C1814),
            )
        }
    }
}

/** `.avs` — overlapping avatars. Each pair is (name, colorIndex). */
@Composable
fun AvatarStack(people: List<Pair<String, Int>>, modifier: Modifier = Modifier, size: AvatarSize = AvatarSize.Md, onClick: (() -> Unit)? = null) {
    Row(
        modifier.then(if (onClick != null) Modifier.ctClickable(true, Role.Button, CircleShape, onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(-10.dp),
    ) {
        people.forEach { (name, color) -> Avatar(name, color, size = size) }
    }
}
