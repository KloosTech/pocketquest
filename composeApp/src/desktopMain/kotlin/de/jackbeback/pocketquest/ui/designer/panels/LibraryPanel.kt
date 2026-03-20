package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.DesignerState
import de.jackbeback.pocketquest.ui.designer.EditorTab

@Composable
fun LibraryPanel(
    state: DesignerState,
    onSelectEncounter: (String) -> Unit,
    onNewEncounter: () -> Unit,
    onSelectEnemy: (String) -> Unit,
    onNewEnemy: () -> Unit,
    onSelectSkill: (String) -> Unit,
    onNewSkill: () -> Unit,
    onSelectOverworld: (String) -> Unit,
    onNewOverworld: () -> Unit,
    onOpenCampaignBrowser: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var encountersExpanded by remember { mutableStateOf(true) }
    var enemiesExpanded by remember { mutableStateOf(true) }
    var skillsExpanded by remember { mutableStateOf(true) }
    var overworldsExpanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .background(DC.Panel)
            .fillMaxHeight()
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DC.Mantle)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                "LIBRARY",
                color = DC.Overlay0,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
        }

        HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp)

        LazyColumn(modifier = Modifier.weight(1f)) {
            // ── Campaign section (shown only when CAMPAIGN tab is active) ──
            if (state.activeTab == EditorTab.CAMPAIGN) {
                item {
                    val campaign = state.activeCampaign
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DC.Mantle)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = campaign?.name ?: "No Campaign",
                                color = if (campaign != null) DC.Mauve else DC.Overlay0,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DC.Mauve.copy(alpha = 0.15f))
                                .clickable { onOpenCampaignBrowser() }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("Switch…", color = DC.Mauve, fontSize = 10.sp)
                        }
                    }
                }

                item { HorizontalDivider(color = DC.PanelBorder, thickness = 1.dp) }

                // Overworlds list
                val campaign = state.activeCampaign
                if (campaign != null) {
                    item {
                        SectionHeader(
                            label = "OVERWORLDS",
                            count = campaign.overworlds.size,
                            expanded = overworldsExpanded,
                            onToggle = { overworldsExpanded = !overworldsExpanded },
                            onAdd = onNewOverworld,
                        )
                    }

                    if (overworldsExpanded) {
                        val orderedOverworlds = campaign.overworldSequence.mapNotNull { id ->
                            campaign.overworlds.find { it.id == id }
                        }
                        if (orderedOverworlds.isEmpty()) {
                            item { EmptyHint("No overworlds yet") }
                        } else {
                            items(orderedOverworlds, key = { it.id }) { ow ->
                                LibraryItem(
                                    label = ow.name,
                                    sublabel = "${ow.nodes.size} node${if (ow.nodes.size != 1) "s" else ""}",
                                    selected = state.activeOverworldId == ow.id,
                                    indicator = DC.Mauve,
                                    onClick = { onSelectOverworld(ow.id) },
                                )
                            }
                        }
                    }

                    item { SectionDivider() }
                }
            }

            // ── Encounters section ─────────────────────────────────────────
            item {
                SectionHeader(
                    label = "ENCOUNTERS",
                    count = state.encounters.size,
                    expanded = encountersExpanded,
                    onToggle = { encountersExpanded = !encountersExpanded },
                    onAdd = onNewEncounter,
                )
            }
            if (encountersExpanded) {
                if (state.encounters.isEmpty()) {
                    item {
                        EmptyHint("No encounters yet")
                    }
                } else {
                    items(state.encounters, key = { it.id }) { enc ->
                        LibraryItem(
                            label = enc.name,
                            sublabel = "${enc.enemies.size} enem${if (enc.enemies.size != 1) "ies" else "y"}",
                            selected = state.selectedEncounterId == enc.id,
                            indicator = DC.Blue,
                            onClick = { onSelectEncounter(enc.id) },
                        )
                    }
                }
            }

            item { SectionDivider() }

            // ── Enemies section ────────────────────────────────────────────
            item {
                SectionHeader(
                    label = "ENEMIES",
                    count = state.enemyLibrary.size,
                    expanded = enemiesExpanded,
                    onToggle = { enemiesExpanded = !enemiesExpanded },
                    onAdd = onNewEnemy,
                )
            }
            if (enemiesExpanded) {
                if (state.enemyLibrary.isEmpty()) {
                    item { EmptyHint("No enemies in library") }
                } else {
                    items(state.enemyLibrary, key = { it.id }) { enemy ->
                        LibraryItem(
                            label = enemy.name,
                            sublabel = "HP ${enemy.maxHealth}  AC ${enemy.stats.ac}",
                            selected = state.selectedEnemyId == enemy.id,
                            indicator = DC.Red,
                            onClick = { onSelectEnemy(enemy.id) },
                        )
                    }
                }
            }

            item { SectionDivider() }

            // ── Skills section ─────────────────────────────────────────────
            item {
                SectionHeader(
                    label = "SKILLS",
                    count = state.skillLibrary.size,
                    expanded = skillsExpanded,
                    onToggle = { skillsExpanded = !skillsExpanded },
                    onAdd = onNewSkill,
                )
            }
            if (skillsExpanded) {
                if (state.skillLibrary.isEmpty()) {
                    item { EmptyHint("No skills in library") }
                } else {
                    items(state.skillLibrary, key = { it.id }) { skill ->
                        LibraryItem(
                            label = skill.name,
                            sublabel = "${skill.manaCost} mana  rng ${skill.range}",
                            selected = state.selectedSkillId == skill.id,
                            indicator = DC.Mauve,
                            onClick = { onSelectSkill(skill.id) },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "▾" else "▸",
            color = DC.Overlay0,
            fontSize = 11.sp,
            modifier = Modifier.width(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = DC.Subtext1,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = DC.Overlay0,
            fontSize = 10.sp,
            modifier = Modifier.padding(end = 6.dp),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onAdd() }
                .background(DC.Surface0),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = DC.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LibraryItem(
    label: String,
    sublabel: String,
    selected: Boolean,
    indicator: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (selected) DC.Selected else DC.Panel)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) indicator else DC.Surface1)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) DC.Text else DC.Subtext1,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sublabel,
                color = DC.Overlay0,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 6.dp)
    ) {
        Text(text, color = DC.Overlay0, fontSize = 11.sp)
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = DC.PanelBorder,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
