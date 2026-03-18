package de.jackbeback.pocketquest.ui.overworld

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ecs.components.core.Faction

private val ColorPlayer = Color(0xFF58a6ff)
private val ColorEnemy = Color(0xFFf85149)

@Composable
fun UnitMapMarker(unit: OverworldUnitUiState) {
    val color = if (unit.faction == Faction.PLAYER) ColorPlayer else ColorEnemy
    val initial = unit.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color.copy(alpha = 0.85f), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
