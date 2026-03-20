package de.jackbeback.pocketquest.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.game.hint.HintDefinition

private val ColorBackdrop  = Color(0xFF0d1117).copy(alpha = 0.80f)
private val ColorCard      = Color(0xFF1c2128)
private val ColorBorder    = Color(0xFF30363d)
private val ColorTitle     = Color(0xFFe6edf3)
private val ColorBody      = Color(0xFF8b949e)
private val ColorAccent    = Color(0xFF388bfd)

/**
 * Full-screen dimmed overlay that displays a tutorial [hint] and a dismiss button.
 * Shown once per hint (persistence is handled by [HintManager]).
 */
@Composable
fun HintOverlay(
    hint: HintDefinition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackdrop),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 24.dp)
                .background(ColorCard, RoundedCornerShape(12.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text       = hint.title,
                color      = ColorTitle,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text     = hint.body,
                color    = ColorBody,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                shape  = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text     = "Verstanden!",
                    fontSize = 13.sp,
                    color    = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
