package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.model.EnemyDefinition
import de.jackbeback.pocketquest.designer.model.StatBlock
import de.jackbeback.pocketquest.ui.designer.DC

private val AI_STRATEGIES = listOf("Aggressive", "Defensive", "Passive")
private val SPRITE_OPTIONS = listOf("grunt", "wizard", "chieftain")
private val DAMAGE_TYPES  = listOf("BLUDGEONING", "SLICING", "PIERCING", "FIRE", "WATER", "ELECTRIC", "FORCE", "ICE", "POISON")

@Composable
fun EnemyEditorPanel(
    enemy: EnemyDefinition,
    availableSkillIds: List<String>,
    onUpdate: (EnemyDefinition) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .background(DC.Background)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { EditorSectionTitle("ENEMY EDITOR", DC.Red) }

        // ── Identity ──────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Identity")
                FieldRow("Name") {
                    DField(enemy.name) { onUpdate(enemy.copy(name = it)) }
                }
                FieldRow("ID") {
                    DField(enemy.id, enabled = false) {}
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Sprite") {
                            DropdownField(enemy.sprite, SPRITE_OPTIONS) { onUpdate(enemy.copy(sprite = it)) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("AI Strategy") {
                            DropdownField(enemy.aiStrategy, AI_STRATEGIES) { onUpdate(enemy.copy(aiStrategy = it)) }
                        }
                    }
                }
                FieldRow("Preferred Skill ID") {
                    if (availableSkillIds.isNotEmpty()) {
                        DropdownField(enemy.preferredSkillId, availableSkillIds) { onUpdate(enemy.copy(preferredSkillId = it)) }
                    } else {
                        DField(enemy.preferredSkillId) { onUpdate(enemy.copy(preferredSkillId = it)) }
                    }
                }
            }
        }

        // ── Stats ─────────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Stats")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("HP") { IntField(enemy.maxHealth) { onUpdate(enemy.copy(maxHealth = it)) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("AC") { IntField(enemy.stats.ac) { onUpdate(enemy.copy(stats = enemy.stats.copy(ac = it))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Initiative") { IntField(enemy.initiative) { onUpdate(enemy.copy(initiative = it)) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("STR") { IntField(enemy.stats.str) { onUpdate(enemy.copy(stats = enemy.stats.copy(str = it))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("DEX") { IntField(enemy.stats.dex) { onUpdate(enemy.copy(stats = enemy.stats.copy(dex = it))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("CON") { IntField(enemy.stats.con) { onUpdate(enemy.copy(stats = enemy.stats.copy(con = it))) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("INT") { IntField(enemy.stats.intelligence) { onUpdate(enemy.copy(stats = enemy.stats.copy(intelligence = it))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("WIS") { IntField(enemy.stats.wis) { onUpdate(enemy.copy(stats = enemy.stats.copy(wis = it))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("CHA") { IntField(enemy.stats.cha) { onUpdate(enemy.copy(stats = enemy.stats.copy(cha = it))) } }
                    }
                }
            }
        }

        // ── Combat ────────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Combat")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Max AP") { IntField(enemy.maxAp) { onUpdate(enemy.copy(maxAp = it)) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Moves/Turn") { IntField(enemy.movesPerTurn) { onUpdate(enemy.copy(movesPerTurn = it)) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Move Range") { IntField(enemy.moveRange) { onUpdate(enemy.copy(moveRange = it)) } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Max Mana") { IntField(enemy.maxMana) { onUpdate(enemy.copy(maxMana = it)) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Mana Regen") { IntField(enemy.manaRegen) { onUpdate(enemy.copy(manaRegen = it)) } }
                    }
                }
            }
        }

        // ── Spawn position ────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Spawn Position (Battle Grid)")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Column (0–13)") { IntField(enemy.spawnCol) { onUpdate(enemy.copy(spawnCol = it.coerceIn(0, 13))) } }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Row (0–8)") { IntField(enemy.spawnRow) { onUpdate(enemy.copy(spawnRow = it.coerceIn(0, 8))) } }
                    }
                }
            }
        }

        // ── Skills ────────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Skills")
                if (enemy.skillIds.isEmpty()) {
                    Text("No skills assigned", color = DC.Overlay0, fontSize = 12.sp)
                }
                enemy.skillIds.forEachIndexed { index, skillId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(DC.InputBg)
                            .border(1.dp, DC.InputBorder, RoundedCornerShape(5.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(skillId, color = DC.Sapphire, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = DC.Overlay0, fontSize = 13.sp, modifier = Modifier.clickable {
                            onUpdate(enemy.copy(skillIds = enemy.skillIds.toMutableList().also { it.removeAt(index) }))
                        })
                    }
                }
                // Add skill dropdown
                var addSkill by remember { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (availableSkillIds.isNotEmpty()) {
                        Box(Modifier.weight(1f)) {
                            DropdownField(
                                selected = if (addSkill.isBlank()) "— select skill —" else addSkill,
                                options = availableSkillIds,
                            ) { addSkill = it }
                        }
                    } else {
                        Box(Modifier.weight(1f)) {
                            DField(addSkill) { addSkill = it }
                        }
                    }
                    ActionChip("+ Add") {
                        if (addSkill.isNotBlank()) {
                            onUpdate(enemy.copy(skillIds = enemy.skillIds + addSkill))
                            addSkill = ""
                        }
                    }
                }
            }
        }

        // ── Resistances ───────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Damage Resistances")
                if (enemy.damageResistances.isEmpty()) {
                    Text("No resistances", color = DC.Overlay0, fontSize = 12.sp)
                }
                enemy.damageResistances.entries.forEachIndexed { _, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1.5f)) {
                            DropdownField(entry.key, DAMAGE_TYPES) { newType ->
                                val newMap = enemy.damageResistances.toMutableMap()
                                val value = newMap.remove(entry.key) ?: 0.5f
                                newMap[newType] = value
                                onUpdate(enemy.copy(damageResistances = newMap))
                            }
                        }
                        Box(Modifier.weight(0.8f)) {
                            FloatField(entry.value) { newVal ->
                                onUpdate(enemy.copy(damageResistances = enemy.damageResistances + (entry.key to newVal)))
                            }
                        }
                        Text("✕", color = DC.Overlay0, fontSize = 13.sp, modifier = Modifier.clickable {
                            onUpdate(enemy.copy(damageResistances = enemy.damageResistances - entry.key))
                        })
                    }
                }
                ActionChip("+ Add Resistance") {
                    val firstUnused = DAMAGE_TYPES.firstOrNull { it !in enemy.damageResistances.keys } ?: "FIRE"
                    onUpdate(enemy.copy(damageResistances = enemy.damageResistances + (firstUnused to 0.5f)))
                }
            }
        }

        // ── Danger zone ───────────────────────────────────────────────────
        item {
            DangerButton("Delete Enemy", onDelete)
        }
    }
}
