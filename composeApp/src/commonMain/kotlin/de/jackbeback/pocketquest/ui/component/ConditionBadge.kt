package de.jackbeback.pocketquest.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ecs.components.combat.ConditionType

private val BurnColor = Color(0xFFFF6B2B)
private val PoisonColor = Color(0xFF3FB950)
private val ColdColor = Color(0xFF58A6FF)

@Composable
fun ConditionBadge(type: ConditionType, stacks: Int, modifier: Modifier = Modifier) {
    val color = when (type) {
        ConditionType.Burn   -> BurnColor
        ConditionType.Poison -> PoisonColor
        ConditionType.Cold   -> ColdColor
    }
    val label = when (type) {
        ConditionType.Burn   -> "🔥"
        ConditionType.Poison -> "☠"
        ConditionType.Cold   -> "❄"
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, fontSize = 9.sp)
        Text(
            text = "×$stacks",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ConditionBadges(
    conditions: Map<ConditionType, Int>,
    modifier: Modifier = Modifier
) {
    if (conditions.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for ((type, stacks) in conditions) {
            ConditionBadge(type = type, stacks = stacks)
        }
    }
}
