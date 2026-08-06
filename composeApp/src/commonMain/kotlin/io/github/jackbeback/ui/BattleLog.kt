package io.github.jackbeback.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jackbeback.vm.LogViewModel

@Composable
fun BattleLog(modifier: Modifier = Modifier) {
    val log: List<String> by LogViewModel.current.log.collectAsState()

    // State für Expand/Collapse
    var expanded by remember { mutableStateOf(false) }

    // Animation für Rotation des Pfeils
    val transition = updateTransition(targetState = expanded, label = "expandTransition")
    val arrowRotation by transition.animateFloat(
        label = "arrowRotation",
        transitionSpec = { tween(300) }
    ) { isExpanded -> if (isExpanded) 180f else 0f }

    // Haupt-Container mit Hintergrundverlauf
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2E2E2E),
                        Color(0xFF1A1A1A)
                    )
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFAB5C00),
                        Color(0xFFFFD700),
                        Color(0xFFAB5C00)
                    )
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        // Header mit Titel und Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(4.dp)
        ) {
            // Feuersymbol oder Rollenspielsymbol könnte hier hinzugefügt werden
            // Icon(...)

            Text(
                text = "KAMPFPROTOKOLL",
                color = Color(0xFFFFD700),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Expand/Collapse Button mit Animation
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Einklappen" else "Ausklappen",
                tint = Color(0xFFFFD700),
                modifier = Modifier
                    .size(28.dp)
                    .rotate(arrowRotation)
            )
        }

        // Einträge mit Animation anzeigen
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                // Zeigt die neuesten 5 Einträge oder so viele wie vorhanden sind
                val latestEntries = log.takeLast(5)

                if (latestEntries.isEmpty()) {
                    Text(
                        text = "Noch keine Kampfaktionen aufgezeichnet...",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    latestEntries.forEachIndexed { index, entry ->
                        BattleLogEntry(
                            entry = entry,
                            isLatest = index == latestEntries.size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BattleLogEntry(entry: String, isLatest: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(
                color = if (isLatest) Color(0x33FFD700) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        // Dekorativer Punkt am Anfang jeder Zeile
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isLatest) Color(0xFFFFD700) else Color(0xFFCC9900),
                    shape = RoundedCornerShape(4.dp)
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Eigentlicher Log-Eintrag
        Text(
            text = entry,
            color = if (isLatest) Color(0xFFFFFFFF) else Color(0xFFCCCCCC),
            fontSize = 14.sp,
            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.Normal
        )
    }
}
