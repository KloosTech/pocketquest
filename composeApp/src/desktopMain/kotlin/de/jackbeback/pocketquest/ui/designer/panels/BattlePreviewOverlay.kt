package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.battle.BattleResult
import de.jackbeback.pocketquest.ui.battle.BattleScreen
import de.jackbeback.pocketquest.ui.battle.BattleViewModel
import de.jackbeback.pocketquest.ui.designer.DC

/**
 * Full-screen overlay that runs a live battle using the provided [viewModel].
 * Shown over the designer when the user clicks "Play Encounter".
 */
@Composable
fun BattlePreviewOverlay(
    viewModel: BattleViewModel,
    encounterName: String,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DC.Crust.copy(alpha = 0.92f)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DC.Mantle)
                    .border(width = 1.dp, color = DC.PanelBorder, shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DC.Green.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("▶ PREVIEW", color = DC.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Text(encounterName, color = DC.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(DC.Surface0)
                        .border(1.dp, DC.Surface1, RoundedCornerShape(6.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕  Close Preview", color = DC.Subtext1, fontSize = 13.sp)
                }
            }

            // Battle screen fills the rest
            Box(modifier = Modifier.weight(1f)) {
                BattleScreen(
                    viewModel = viewModel,
                    onBattleEnd = { _: BattleResult ->
                        // Don't route away — just close the preview overlay
                        onClose()
                    },
                    onRelicChosen = { /* No relic system in preview mode */ },
                )
            }
        }
    }
}
