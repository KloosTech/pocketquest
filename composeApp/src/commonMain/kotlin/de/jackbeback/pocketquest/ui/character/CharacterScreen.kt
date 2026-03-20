package de.jackbeback.pocketquest.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import de.jackbeback.pocketquest.content.definitions.allRelics
import de.jackbeback.pocketquest.ui.relicLocalizedDescription
import de.jackbeback.pocketquest.ui.relicLocalizedName
import de.jackbeback.pocketquest.ui.skillLocalizedDescription
import de.jackbeback.pocketquest.ui.skillLocalizedName
import org.jetbrains.compose.resources.stringResource
import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.btn_back
import pocketquest.composeapp.generated.resources.character_ac_label
import pocketquest.composeapp.generated.resources.character_level
import pocketquest.composeapp.generated.resources.character_mp_cost
import pocketquest.composeapp.generated.resources.character_no_active_run
import pocketquest.composeapp.generated.resources.character_points_to_spend_one
import pocketquest.composeapp.generated.resources.character_points_to_spend_other
import pocketquest.composeapp.generated.resources.character_screen_title
import pocketquest.composeapp.generated.resources.character_stat_fraction
import pocketquest.composeapp.generated.resources.label_attributes
import pocketquest.composeapp.generated.resources.label_hp
import pocketquest.composeapp.generated.resources.label_mp
import pocketquest.composeapp.generated.resources.label_relics
import pocketquest.composeapp.generated.resources.label_skills
import pocketquest.composeapp.generated.resources.label_xp

// ── Colour palette ────────────────────────────────────────────────────────────
private val ColorBg      = Color(0xFF0d1117)
private val ColorCard    = Color(0xFF161b22)
private val ColorBorder  = Color(0xFF30363d)
private val ColorText    = Color(0xFFc9d1d9)
private val ColorSubtext = Color(0xFF8b949e)
private val ColorGold    = Color(0xFFd29922)
private val ColorBlue    = Color(0xFF58a6ff)
private val ColorGreen   = Color(0xFF238636)
private val ColorHpBg    = Color(0xFF3d1f1f)
private val ColorManaBg  = Color(0xFF1f2d3d)

@Composable
fun CharacterScreen(viewModel: CharacterViewModel) {
    val uiState by viewModel.state.collectAsState()
    val state = uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .systemBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorCard)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { viewModel.goBack() }) {
                Text(stringResource(Res.string.btn_back), color = ColorBlue, fontSize = 14.sp)
            }
            Text(
                text = stringResource(Res.string.character_screen_title),
                color = ColorText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(64.dp))
        }

        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.character_no_active_run), color = ColorSubtext)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Hero header ───────────────────────────────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(state.name, color = ColorText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(Res.string.character_level, state.level), color = ColorGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (state.unspentPoints > 0) {
                        val pointsText = if (state.unspentPoints == 1)
                            stringResource(Res.string.character_points_to_spend_one, state.unspentPoints)
                        else
                            stringResource(Res.string.character_points_to_spend_other, state.unspentPoints)
                        Text(
                            text = pointsText,
                            color = ColorGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(Color(0xFF2d2a00), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // XP bar
                val xpRatio = state.exp.toFloat() / state.expToNextLevel.toFloat().coerceAtLeast(1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.label_xp), color = ColorSubtext, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                    LinearProgressIndicator(
                        progress = { xpRatio },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = ColorGold,
                        trackColor = Color(0xFF21262d),
                    )
                    Text(stringResource(Res.string.character_stat_fraction, state.exp, state.expToNextLevel), color = ColorSubtext, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))

                // HP bar
                val hpRatio = state.hp.current.toFloat() / state.hp.max.toFloat().coerceAtLeast(1f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.label_hp), color = ColorSubtext, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                    LinearProgressIndicator(
                        progress = { hpRatio },
                        modifier = Modifier.weight(1f).height(6.dp),
                        color = ColorGreen,
                        trackColor = ColorHpBg,
                    )
                    Text(stringResource(Res.string.character_stat_fraction, state.hp.current, state.hp.max), color = ColorSubtext, fontSize = 11.sp)
                }

                // Mana bar
                if (state.mana.max > 0) {
                    Spacer(Modifier.height(4.dp))
                    val manaRatio = state.mana.current.toFloat() / state.mana.max.toFloat().coerceAtLeast(1f)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.label_mp), color = ColorSubtext, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                        LinearProgressIndicator(
                            progress = { manaRatio },
                            modifier = Modifier.weight(1f).height(6.dp),
                            color = ColorBlue,
                            trackColor = ColorManaBg,
                        )
                        Text(stringResource(Res.string.character_stat_fraction, state.mana.current, state.mana.max), color = ColorSubtext, fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.character_ac_label, state.ac), color = ColorSubtext, fontSize = 11.sp)
            }

            // ── Attributes ────────────────────────────────────────────────
            SectionCard {
                SectionTitle(stringResource(Res.string.label_attributes))
                Spacer(Modifier.height(8.dp))
                state.attributes.forEach { attr ->
                    AttributeRow(
                        attr = attr,
                        canSpend = state.unspentPoints > 0,
                        onSpend = { viewModel.spendPoint(attr.key) },
                    )
                }
            }

            // ── Skills ────────────────────────────────────────────────────
            if (state.skills.isNotEmpty()) {
                SectionCard {
                    SectionTitle(stringResource(Res.string.label_skills))
                    Spacer(Modifier.height(8.dp))
                    state.skills.forEach { skill ->
                        SkillRow(skill)
                        if (skill != state.skills.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = ColorBorder,
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            // ── Relics ────────────────────────────────────────────────────
            val heldRelics = allRelics.filter { it.id in state.relics }
            if (heldRelics.isNotEmpty()) {
                SectionCard {
                    SectionTitle(stringResource(Res.string.label_relics))
                    Spacer(Modifier.height(8.dp))
                    heldRelics.forEach { relic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(relicIcon(relic.id), fontSize = 18.sp)
                            Column {
                                Text(relicLocalizedName(relic.id), color = ColorGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(relicLocalizedDescription(relic.id), color = ColorSubtext, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AttributeRow(
    attr: AttributeUiState,
    canSpend: Boolean,
    onSpend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            attr.abbrev,
            color = ColorSubtext,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp),
        )
        Text(
            attr.name,
            color = ColorText,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${attr.value}",
            color = ColorText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End,
        )
        val modStr = if (attr.modifier >= 0) "+${attr.modifier}" else "${attr.modifier}"
        val modColor = when {
            attr.modifier > 0 -> ColorGreen
            attr.modifier < 0 -> Color(0xFFf85149)
            else -> ColorSubtext
        }
        Text(
            text = "($modStr)",
            color = modColor,
            fontSize = 11.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Start,
        )
        if (canSpend) {
            TextButton(
                onClick = onSpend,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = ColorGold),
            ) {
                Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.width(40.dp))
        }
    }
}

@Composable
private fun SkillRow(skill: SkillCharacterUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(skillLocalizedName(skill.id), color = ColorText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (skill.manaCost > 0) {
                    Text(
                        stringResource(Res.string.character_mp_cost, skill.manaCost),
                        color = ColorBlue,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color(0xFF1f2d3d), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            val localizedDesc = skillLocalizedDescription(skill.id)
            if (localizedDesc.isNotBlank()) {
                Text(localizedDesc, color = ColorSubtext, fontSize = 11.sp)
            }
            if (skill.diceDescription.isNotBlank()) {
                Text(skill.diceDescription, color = Color(0xFF8b949e), fontSize = 10.sp)
            }
        }
        if (skill.attributeModifier != null) {
            val modStr = if (skill.modifierValue >= 0) "+${skill.modifierValue}" else "${skill.modifierValue}"
            val modColor = when {
                skill.modifierValue > 0 -> ColorGreen
                skill.modifierValue < 0 -> Color(0xFFf85149)
                else -> ColorSubtext
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(skill.attributeModifier.name, color = ColorSubtext, fontSize = 9.sp)
                Text(modStr, color = modColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorCard, RoundedCornerShape(8.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(8.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = ColorText, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
