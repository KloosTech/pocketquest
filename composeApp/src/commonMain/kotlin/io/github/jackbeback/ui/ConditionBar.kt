package io.github.jackbeback.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jackbeback.data.ConditionType
import org.jetbrains.compose.resources.painterResource
import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.condifion_fire
import pocketquest.composeapp.generated.resources.condifion_ice
import pocketquest.composeapp.generated.resources.condifion_poison

/**
 * Zeigt eine Leiste mit Bedingungssymbolen an, skaliert basierend auf der Bildschirmdichte
 * @param conditions Map von Bedingungstypen zu ihren Stapelgrößen
 * @param modifier Modifier für die Leiste
 * @param baseIconSize Basisgröße der Icons (wird entsprechend der Bildschirmdichte skaliert)
 */
@Composable
fun ConditionBar(
    conditions: Map<ConditionType, Int>,
    modifier: Modifier = Modifier,
    baseIconSize: Dp = 20.dp
) {
    if (conditions.isEmpty()) return

    val density = LocalDensity.current

    // Berechne die tatsächliche Größe basierend auf der Bildschirmdichte
    val scaledIconSize = with(density) { (baseIconSize.toPx() / density.density).dp }
    val badgeSize = scaledIconSize * 0.57f  // ~16dp bei baseIconSize=28dp
    val spacingSize = scaledIconSize * 0.07f  // ~2dp bei baseIconSize=28dp

    Row(
        modifier = modifier
            .padding(bottom = scaledIconSize * 0.14f)  // ~4dp bei baseIconSize=28dp
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        conditions.forEach { (conditionType, stackSize) ->
            ConditionIcon(
                conditionType = conditionType,
                stackSize = stackSize,
                iconSize = scaledIconSize,
                modifier = Modifier.padding(horizontal = spacingSize)
            )
        }
    }
}

/**
 * Zeigt ein einzelnes Bedingungssymbol mit Stapelzähler an
 * @param conditionType Typ der Bedingung
 * @param stackSize Anzahl der Stapel
 * @param iconSize Größe des Icons, skaliert basierend auf Bildschirmdichte
 * @param badgeSize Größe des Badges, skaliert basierend auf Bildschirmdichte
 * @param modifier Modifier für das Symbol
 */
@Composable
private fun ConditionIcon(
    conditionType: ConditionType,
    stackSize: Int,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val badgeSize = iconSize * 0.35f
    // Berechnete Größen basierend auf der Bildschirmdichte
    val offsetSize = badgeSize * 0.7f  // ~3dp bei badgeSize=16dp
    val borderWidth = with(density) { (1.dp.toPx() / density.density).dp }
    val shadowSize = with(density) { (2.dp.toPx() / density.density).dp }

    Box(modifier = modifier.size(iconSize), contentAlignment = Alignment.Center) {
        // Bedingungssymbol basierend auf dem Typ
        when (conditionType) {
            ConditionType.Burn -> {
                Image(
                    painterResource(Res.drawable.condifion_fire),
                    contentDescription = "Feuer",
                    modifier = Modifier.fillMaxSize()
                )
            }

            ConditionType.Poison -> {
                Image(
                    painterResource(Res.drawable.condifion_poison),
                    contentDescription = "Gift",
                    modifier = Modifier.fillMaxSize()
                )
            }

            ConditionType.Cold -> {
                Image(
                    painterResource(Res.drawable.condifion_ice),
                    contentDescription = "Eis",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        // Zeige Stapelzähler nur an, wenn es mehr als einen Stapel gibt
        if (stackSize > 1) {
            Text(
                text = stackSize.toString(),
                textAlign = TextAlign.Center,
                color = Color.White,
                fontSize = with(LocalDensity.current) { (badgeSize.toPx() * 0.7f / density.density).sp },
                // Ergänze ein TextStyle, um Zeilenhöhe und vertikale Ausrichtung zu kontrollieren
                style = TextStyle(
                    lineHeight = with(LocalDensity.current) { (badgeSize.toPx() * 0.7f / density.density).sp },
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .wrapContentSize(align = Alignment.Center) // Explizite Ausrichtungskontrolle
                    .offset(x = offsetSize, y = offsetSize)
                    .shadow(shadowSize, CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f), shape = CircleShape)
                    .padding(horizontal = 2.dp, vertical = 0.dp) // Nur horizontales Padding
                    .size(badgeSize) // Feste Größe, um die gesamte Größe zu kontrollieren
                    .border(
                        width = borderWidth,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.8f),
                                getConditionColor(conditionType)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Gibt die thematische Farbe für einen Bedingungstyp zurück
 * @param conditionType Typ der Bedingung
 * @return Color entsprechend des Bedingungstyps
 */
private fun getConditionColor(conditionType: ConditionType): Color {
    return when (conditionType) {
        ConditionType.Burn -> Color(0xFFFF5722)    // Leuchtendes Orange-Rot
        ConditionType.Poison -> Color(0xFF4CAF50)  // Giftgrün
        ConditionType.Cold -> Color(0xFF2196F3)    // Eisblau
    }
}
