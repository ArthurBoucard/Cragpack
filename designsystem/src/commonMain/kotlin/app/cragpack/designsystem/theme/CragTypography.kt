package app.cragpack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.cragpack.designsystem.generated.resources.Res
import app.cragpack.designsystem.generated.resources.bricolage_grotesque
import app.cragpack.designsystem.generated.resources.instrument_sans
import app.cragpack.designsystem.generated.resources.jetbrains_mono
import app.cragpack.designsystem.tokens.TypeTokens
import org.jetbrains.compose.resources.Font

internal enum class FontRole { Text, Display, Mono }

internal class TypeToken(
    val role: FontRole,
    val sizeSp: Float,
    val weight: Int,
    val lineHeight: Float,
    val letterSpacingEm: Float,
)

/** Type scale from the tokens. Text below 13 (label) is not allowed. */
@Immutable
data class CragTypography(
    val titleXl: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val big: TextStyle,
    val title: TextStyle,
    val rowName: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val mono: TextStyle,
)

@Composable
internal fun cragTypography(): CragTypography {
    val text = FontFamily((400..700 step 100).map { Font(Res.font.instrument_sans, FontWeight(it)) })
    val mono = FontFamily((400..700 step 100).map { Font(Res.font.jetbrains_mono, FontWeight(it)) })
    val t = TypeTokens
    return CragTypography(
        titleXl = t.titleXl.style(text, mono),
        h1 = t.h1.style(text, mono),
        h2 = t.h2.style(text, mono),
        h3 = t.h3.style(text, mono),
        big = t.big.style(text, mono),
        title = t.title.style(text, mono),
        rowName = t.rowName.style(text, mono),
        body = t.body.style(text, mono),
        label = t.label.style(text, mono),
        mono = t.mono.style(text, mono),
    )
}

@Composable
private fun TypeToken.style(text: FontFamily, mono: FontFamily) = TextStyle(
    fontFamily = when (role) {
        FontRole.Text -> text
        FontRole.Mono -> mono
        // Bricolage has an optical-size axis; browsers set it to the font size, so we do too.
        FontRole.Display -> FontFamily(
            Font(
                Res.font.bricolage_grotesque,
                FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.Setting("opsz", sizeSp),
                ),
            ),
        )
    },
    fontSize = sizeSp.sp,
    fontWeight = FontWeight(weight),
    lineHeight = (sizeSp * lineHeight).sp,
    letterSpacing = letterSpacingEm.em,
)
