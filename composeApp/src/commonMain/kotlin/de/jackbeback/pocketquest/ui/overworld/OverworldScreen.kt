package de.jackbeback.pocketquest.ui.overworld

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.definitions.Relic
import de.jackbeback.pocketquest.content.definitions.allRelics
import de.jackbeback.pocketquest.content.events.OverworldEvent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.game.run.RunScopedState
import ovh.plrapps.mapcompose.ui.MapUI

private val ColorBg      = Color(0xCC0d1117)
private val ColorHp      = Color(0xFF238636)
private val ColorHpBg    = Color(0xFF3d1f1f)
private val ColorMana    = Color(0xFF388bfd)
private val ColorManaBg  = Color(0xFF1f2d3d)
private val ColorText    = Color(0xFFc9d1d9)
private val ColorSubtext = Color(0xFF8b949e)
private val ColorGold    = Color(0xFFd29922)

@Composable
fun OverworldScreen(
    viewModel: OverworldViewModel,
    onNextArea: () -> Unit = {},
    onEndRun: () -> Unit = {},
) {
    val state           by viewModel.state.collectAsState()
    val runState        by viewModel.runState.collectAsState()
    val eventsRemaining by viewModel.eventsRemaining.collectAsState()
    val pendingRest     by viewModel.pendingRest.collectAsState()
    val mapCleared      by viewModel.mapCleared.collectAsState()

    val player = state.units.firstOrNull { it.faction == Faction.PLAYER }

    Box(modifier = Modifier.fillMaxSize()) {
        MapUI(modifier = Modifier.fillMaxSize(), state = viewModel.mapState)

        // HUD overlay — sits at the top of the screen
        if (runState != null) {
            OverworldHud(
                runState = runState!!,
                health = player?.health ?: HealthComponent(0, 0),
                mana = player?.mana ?: ManaComponent(0, 0),
                eventsRemaining = eventsRemaining,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .systemBarsPadding(),
            )
        }

        // Rest site confirmation dialog
        pendingRest?.let { rest ->
            RestSiteDialog(
                rest = rest,
                currentHp = player?.health?.current ?: 0,
                maxHp = player?.health?.max ?: 0,
                onConfirm = { viewModel.onRestConfirmed() },
                onDismiss = { viewModel.onRestDismissed() },
            )
        }

        // Area-clear overlay — shown when all events have been completed
        AnimatedVisibility(
            visible = mapCleared,
            enter = scaleIn(tween(300)) + fadeIn(tween(300)),
            exit  = scaleOut(tween(200)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize(),
        ) {
            MapClearOverlay(
                runState = runState,
                onNextArea = {
                    viewModel.dismissMapClear()
                    onNextArea()
                },
                onEndRun = onEndRun,
            )
        }
    }
}

@Composable
private fun RestSiteDialog(
    rest: OverworldEvent.RestSite,
    currentHp: Int,
    maxHp: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val healAmount = (maxHp * rest.healPercent).toInt().coerceAtLeast(1)
    val healedHp   = (currentHp + healAmount).coerceAtMost(maxHp)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rest.label) },
        text  = {
            Text("Rest here to restore ${(rest.healPercent * 100).toInt()}% HP?\n" +
                 "($currentHp → $healedHp / $maxHp)")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Rest") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Leave") }
        },
    )
}

@Composable
private fun OverworldHud(
    runState: RunScopedState,
    health: HealthComponent,
    mana: ManaComponent,
    eventsRemaining: Int,
    modifier: Modifier = Modifier,
) {
    val characterName = runState.characterTemplateId
        .replaceFirstChar { it.uppercase() }
    val encounterNum  = runState.difficultyCounter + 1
    val hpRatio       = health.current.toFloat() / health.max.toFloat().coerceAtLeast(1f)
    val manaRatio     = if (mana.max > 0) mana.current.toFloat() / mana.max.toFloat() else 0f
    val heldRelics    = allRelics.filter { it.id in runState.relics }

    Column(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(ColorBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Character name + resource bars
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = characterName,
                    color = ColorText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                // HP bar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("HP", color = ColorSubtext, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                    LinearProgressIndicator(
                        progress = { hpRatio },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = ColorHp, trackColor = ColorHpBg,
                    )
                    Text("${health.current}/${health.max}", color = ColorSubtext, fontSize = 10.sp)
                }
                // Mana bar (only if the character has mana)
                if (mana.max > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MP", color = ColorSubtext, fontSize = 10.sp, modifier = Modifier.width(20.dp))
                        LinearProgressIndicator(
                            progress = { manaRatio },
                            modifier = Modifier.weight(1f).height(5.dp),
                            color = ColorMana, trackColor = ColorManaBg,
                        )
                        Text("${mana.current}/${mana.max}", color = ColorSubtext, fontSize = 10.sp)
                    }
                }
            }

            // Encounter counter + events remaining
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#$encounterNum",
                    color = ColorGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "$eventsRemaining left",
                    color = ColorSubtext,
                    fontSize = 10.sp,
                )
            }
        }

        // Relic row — only shown when the player holds at least one relic
        if (heldRelics.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(heldRelics) { relic ->
                    RelicChip(relic)
                }
            }
        }
    }
}

@Composable
private fun RelicChip(relic: Relic) {
    Row(
        modifier = Modifier
            .background(Color(0xFF0d1117), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF30363d), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = relicIcon(relic.id),
            fontSize = 11.sp,
        )
        Text(
            text = relic.name,
            color = Color(0xFFd29922),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
        )
    }
}

@Composable
private fun MapClearOverlay(
    runState: RunScopedState?,
    onNextArea: () -> Unit,
    onEndRun: () -> Unit,
) {
    val heldRelics = allRelics.filter { it.id in (runState?.relics ?: emptyList()) }
    val areaNum    = runState?.areaNumber ?: 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .background(Color(0xFF161b22), RoundedCornerShape(12.dp))
                .border(1.dp, ColorGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("☠  Area $areaNum Cleared!",
                color = ColorGold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

            HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)

            // Run stats
            if (runState != null) {
                StatRow("Level",     "Lv ${runState.level}")
                StatRow("Encounters", "${runState.difficultyCounter}")
                if (heldRelics.isNotEmpty()) {
                    StatRow("Relics", "${heldRelics.size}")
                }
            }

            HorizontalDivider(color = Color(0xFF30363d), thickness = 0.5.dp)

            Text(
                "The map is clear. Press on.",
                color = ColorSubtext,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )

            // Next area
            Button(
                onClick = onNextArea,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorGold,
                    contentColor = Color(0xFF0d1117),
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Next Area  →", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // End run
            TextButton(
                onClick = onEndRun,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("End Run", color = ColorSubtext, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ColorSubtext, fontSize = 12.sp)
        Text(value, color = ColorText,    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun relicIcon(relicId: String): String = when (relicId) {
    "ancient_tome"   -> "📖"
    "iron_will"      -> "🛡"
    "war_trophy"     -> "⚔"
    "mage_stone"     -> "💎"
    "well_of_power"  -> "🌊"
    "scholars_ring"  -> "💍"
    "blood_pact"     -> "❤"
    "arcane_focus"   -> "✨"
    else             -> "★"
}
