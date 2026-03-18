package de.jackbeback.pocketquest.ui.overworld

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.events.OverworldEvent

private val ColorEncounter    = Color(0xFFf85149)
private val ColorEncounterBg  = Color(0xFF5a1a1a)
private val ColorBoss         = Color(0xFFd29922)
private val ColorBossBg       = Color(0xFF2d2a00)
private val ColorRest         = Color(0xFF3fb950)
private val ColorRestBg       = Color(0xFF1a3a1f)
private val ColorLabelBg      = Color(0xCC0d1117)

@Composable
fun EventMapMarker(event: OverworldEvent) {
    val isBoss = event is OverworldEvent.BattleEncounter && event.isBoss
    val (icon, tint, bg, size, fontSize) = when {
        event is OverworldEvent.RestSite -> MarkerStyle("✦", ColorRest,      ColorRestBg,      32.dp, 14.sp)
        isBoss                           -> MarkerStyle("☠", ColorBoss,      ColorBossBg,      40.dp, 18.sp)
        else                             -> MarkerStyle("⚔", ColorEncounter, ColorEncounterBg, 32.dp, 14.sp)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .background(bg, CircleShape)
                .border(if (isBoss) 2.5.dp else 2.dp, tint, CircleShape),
        ) {
            Text(text = icon, color = tint, fontSize = fontSize, fontWeight = FontWeight.Bold)
        }
        Text(
            text = event.label,
            color = if (isBoss) ColorBoss else Color.White,
            fontSize = 9.sp,
            fontWeight = if (isBoss) FontWeight.ExtraBold else FontWeight.Medium,
            modifier = Modifier
                .background(ColorLabelBg, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

private data class MarkerStyle(
    val icon: String,
    val tint: Color,
    val bg: Color,
    val size: Dp,
    val fontSize: TextUnit,
)
