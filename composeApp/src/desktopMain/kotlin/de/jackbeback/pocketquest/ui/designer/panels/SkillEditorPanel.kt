package de.jackbeback.pocketquest.ui.designer.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.designer.model.EffectDto
import de.jackbeback.pocketquest.designer.model.SkillDefinition
import de.jackbeback.pocketquest.ui.designer.DC

private val DAMAGE_TYPES = listOf("BLUDGEONING", "SLICING", "PIERCING", "FIRE", "WATER", "ELECTRIC", "FORCE", "ICE", "POISON")
private val CONDITION_TYPES = listOf("Burn", "Poison", "Cold", "Block", "StrengthUp")
private val ANIM_TYPES = listOf("INSTANT", "PROJECTILE", "MELEE", "HEAL")
private val EFFECT_TYPES = listOf("damage", "heal", "condition")

@Composable
fun SkillEditorPanel(
    skill: SkillDefinition,
    onUpdate: (SkillDefinition) -> Unit,
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
        item { EditorSectionTitle("SKILL EDITOR", DC.Mauve) }

        // ── Identity ──────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Identity")
                FieldRow("Name") {
                    DField(skill.name) { onUpdate(skill.copy(name = it)) }
                }
                FieldRow("ID") {
                    DField(skill.id, enabled = false) {}
                }
                FieldRow("Description") {
                    DField(skill.description, singleLine = false) { onUpdate(skill.copy(description = it)) }
                }
                FieldRow("Sprite Key") {
                    DField(skill.spriteKey) { onUpdate(skill.copy(spriteKey = it)) }
                }
            }
        }

        // ── Combat Stats ──────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Combat")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldRow("Mana Cost") {
                            IntField(skill.manaCost) { onUpdate(skill.copy(manaCost = it)) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Range") {
                            IntField(skill.range) { onUpdate(skill.copy(range = it)) }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        FieldRow("Max Targets") {
                            IntField(skill.maxTargets) { onUpdate(skill.copy(maxTargets = it)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckboxField("Needs Target", skill.needsTarget) { onUpdate(skill.copy(needsTarget = it)) }
                    CheckboxField("Needs Hit Roll", skill.needsHitRoll) { onUpdate(skill.copy(needsHitRoll = it)) }
                }
                FieldRow("Animation") {
                    DropdownField(skill.animationType, ANIM_TYPES) { onUpdate(skill.copy(animationType = it)) }
                }
            }
        }

        // ── Effects ───────────────────────────────────────────────────────
        item {
            EditorCard {
                CardLabel("Effects")
                if (skill.effects.isEmpty()) {
                    Text("No effects — add one below", color = DC.Overlay0, fontSize = 12.sp)
                }
            }
        }
        itemsIndexed(skill.effects) { index, effect ->
            EffectRow(
                effect = effect,
                onUpdate = { updated ->
                    onUpdate(skill.copy(effects = skill.effects.toMutableList().also { it[index] = updated }))
                },
                onRemove = {
                    onUpdate(skill.copy(effects = skill.effects.toMutableList().also { it.removeAt(index) }))
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EFFECT_TYPES.forEach { type ->
                    ActionChip("+ $type") {
                        val newEffect = when (type) {
                            "damage" -> EffectDto("damage", 1, 6, 0, "BLUDGEONING")
                            "heal" -> EffectDto("heal", 1, 6, 0)
                            else -> EffectDto("condition", conditionType = "Burn", stacks = 1)
                        }
                        onUpdate(skill.copy(effects = skill.effects + newEffect))
                    }
                }
            }
        }

        // ── Danger zone ───────────────────────────────────────────────────
        item {
            DangerButton("Delete Skill", onDelete)
        }
    }
}

@Composable
private fun EffectRow(
    effect: EffectDto,
    onUpdate: (EffectDto) -> Unit,
    onRemove: () -> Unit,
) {
    EditorCard(borderColor = when (effect.type) {
        "damage" -> DC.Red.copy(alpha = 0.4f)
        "heal" -> DC.Green.copy(alpha = 0.4f)
        else -> DC.Mauve.copy(alpha = 0.4f)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val typeColor = when (effect.type) {
                "damage" -> DC.Red; "heal" -> DC.Green; else -> DC.Mauve
            }
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(typeColor.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)
            ) { Text(effect.type.uppercase(), color = typeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
            Spacer(Modifier.weight(1f))
            Text("✕", color = DC.Overlay0, fontSize = 14.sp, modifier = Modifier.clickable { onRemove() })
        }
        Spacer(Modifier.height(8.dp))

        when (effect.type) {
            "damage" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { FieldRow("Count") { IntField(effect.count) { onUpdate(effect.copy(count = it)) } } }
                    Column(Modifier.weight(1f)) { FieldRow("Sides") { IntField(effect.sides) { onUpdate(effect.copy(sides = it)) } } }
                    Column(Modifier.weight(1f)) { FieldRow("Bonus") { IntField(effect.bonus) { onUpdate(effect.copy(bonus = it)) } } }
                }
                FieldRow("Damage Type") {
                    DropdownField(effect.damageType, DAMAGE_TYPES) { onUpdate(effect.copy(damageType = it)) }
                }
            }
            "heal" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { FieldRow("Count") { IntField(effect.count) { onUpdate(effect.copy(count = it)) } } }
                    Column(Modifier.weight(1f)) { FieldRow("Sides") { IntField(effect.sides) { onUpdate(effect.copy(sides = it)) } } }
                    Column(Modifier.weight(1f)) { FieldRow("Bonus") { IntField(effect.bonus) { onUpdate(effect.copy(bonus = it)) } } }
                }
            }
            "condition" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(2f)) {
                        FieldRow("Condition") {
                            DropdownField(effect.conditionType, CONDITION_TYPES) { onUpdate(effect.copy(conditionType = it)) }
                        }
                    }
                    Column(Modifier.weight(1f)) { FieldRow("Stacks") { IntField(effect.stacks) { onUpdate(effect.copy(stacks = it)) } } }
                }
            }
        }
    }
}
