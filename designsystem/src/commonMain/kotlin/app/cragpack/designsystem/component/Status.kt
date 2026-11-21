package app.cragpack.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.icon.LucideIcon
import app.cragpack.designsystem.theme.CragpackTheme
import app.cragpack.designsystem.tokens.IconTokens
import app.cragpack.designsystem.tokens.RadiusTokens

/** Design rule 4: what the top-bar pill says. Download states (saved, saving, paused) come with the offline pack. */
sealed interface SyncStatus {
    /** No account: data lives on this phone only. */
    data object OnThisPhone : SyncStatus
    data object Synced : SyncStatus
    data class Pending(val edits: Int) : SyncStatus
}

/** `.status` — the sync pill in the middle of the top bar. */
@Composable
fun SyncStatusPill(status: SyncStatus, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    val todo = status is SyncStatus.Pending
    Row(
        modifier
            .height(40.dp)
            .then(if (todo) Modifier.dashedBorder(c.ink, RadiusTokens.pill) else Modifier.clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            SyncStatus.OnThisPhone -> CtIcon(LucideIcon.CloudOff, null, size = IconTokens.sizeSm)
            SyncStatus.Synced -> CtIcon(LucideIcon.Check, null, size = IconTokens.sizeSm, tint = c.accentInk)
            is SyncStatus.Pending -> Unit
        }
        val text = when (status) {
            SyncStatus.OnThisPhone -> "On this phone"
            SyncStatus.Synced -> "Synced"
            is SyncStatus.Pending -> if (status.edits == 1) "1 edit to sync" else "${status.edits} edits to sync"
        }
        CtText(text, style = CragpackTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** `.tag` — small pill (Settings: how you signed in). ponytail: the offline dot and dashed `todo` come with the offline pack (Phase 3). */
@Composable
fun StatusTag(text: String, modifier: Modifier = Modifier) {
    CtText(
        text,
        modifier.clip(CircleShape).background(CragpackTheme.colors.surface2).padding(horizontal = 14.dp, vertical = 6.dp),
        style = CragpackTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        maxLines = 1,
    )
}

/** `.now` — accent pill: "Now · day 2/5". */
@Composable
fun NowBadge(text: String, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Box(modifier.height(36.dp).clip(CircleShape).background(c.accent).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        CtText(text, style = CragpackTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.onAccent, maxLines = 1)
    }
}

/** `.ticks` — segmented progress: 12-wide ticks, the first [fraction] of the width in accent. */
@Composable
fun SegmentedProgress(fraction: Float, modifier: Modifier = Modifier) {
    val c = CragpackTheme.colors
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val tick = 12.dp.toPx()
        val step = 17.5f.dp.toPx()
        fun ticks(color: androidx.compose.ui.graphics.Color) {
            var x = 0f
            while (x < size.width) {
                drawRect(color, Offset(x, 0f), Size(minOf(tick, size.width - x), size.height))
                x += step
            }
        }
        ticks(c.surface2)
        clipRect(right = size.width * fraction.coerceIn(0f, 1f)) { ticks(c.accent) }
    }
}
