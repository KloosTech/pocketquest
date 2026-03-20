package de.jackbeback.pocketquest.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.skillLocalizedName
import de.jackbeback.pocketquest.ui.battle.SkillUiState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pocketquest.composeapp.generated.resources.*

private val ColorBg = Color(0xFF161b22)
private val ColorSelected = Color(0xFF388bfd)
private val ColorBorder = Color(0xFF30363d)
private val ColorMana = Color(0xFF388bfd)
private val ColorText = Color(0xFFc9d1d9)
private val ColorSubtext = Color(0xFF8b949e)

@Composable
fun SkillPanel(
    skills: List<SkillUiState>,
    selectedSkillId: String?,
    onSkillSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Current mana of the player. Skills costing more than this are greyed out. */
    playerMana: Int = Int.MAX_VALUE,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorBg)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        skills.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { skill ->
                    val affordable = skill.manaCost <= playerMana
                    SkillButton(
                        skill = skill,
                        isSelected = skill.id == selectedSkillId,
                        onClick = { if (enabled && affordable) onSkillSelected(skill.id) },
                        enabled = enabled && affordable,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkillButton(
    skill: SkillUiState,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) ColorSelected else ColorBorder
    val bgColor = if (isSelected) ColorSelected.copy(alpha = 0.15f) else Color.Transparent
    val alpha = if (enabled) 1f else 0.4f

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val painter = spriteKeyToPainter(skill.spriteKey)
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = skill.name,
                modifier = Modifier.size(36.dp),
                alpha = alpha,
            )
        } else {
            Spacer(Modifier.size(36.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = skillLocalizedName(skill.id),
                color = ColorText.copy(alpha = alpha),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (skill.manaCost > 0) {
                    Text(
                        text = stringResource(Res.string.skill_mana_cost, skill.manaCost),
                        color = ColorMana.copy(alpha = alpha),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.skill_mana_free),
                        color = ColorSubtext.copy(alpha = alpha),
                        fontSize = 9.sp,
                    )
                }
                Text(
                    text = stringResource(Res.string.skill_range, skill.range),
                    color = ColorSubtext.copy(alpha = alpha),
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun spriteKeyToPainter(spriteKey: String): Painter? = when (spriteKey) {
    "MagicMissile"  -> painterResource(Res.drawable.MagicMissile)
    "BasicHeal"     -> painterResource(Res.drawable.BasicHeal)
    "ThornWhip"     -> painterResource(Res.drawable.ThornWhip)
    "CrimsonArcana" -> painterResource(Res.drawable.CrimsonArcana)
    else            -> null
}
