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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.events.OverworldEvent

private val ColorEncounter    = Color(0xFFf85149)
private val ColorEncounterBg  = Color(0xFF5a1a1a)
private val ColorRest         = Color(0xFF3fb950)
private val ColorRestBg       = Color(0xFF1a3a1f)
private val ColorLabelBg      = Color(0xCC0d1117)

@Composable
fun EventMapMarker(event: OverworldEvent) {
    val (icon, tint, bg) = when (event) {
        is OverworldEvent.BattleEncounter -> Triple("⚔", ColorEncounter, ColorEncounterBg)
        is OverworldEvent.RestSite        -> Triple("✦", ColorRest, ColorRestBg)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(bg, CircleShape)
                .border(2.dp, tint, CircleShape),
        ) {
            Text(text = icon, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = event.label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(ColorLabelBg, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
