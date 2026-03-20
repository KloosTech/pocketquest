package de.jackbeback.pocketquest.ui.characterselect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.content.dsl.UnitTemplate
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.ui.skillLocalizedName
import de.jackbeback.pocketquest.ui.unitLocalizedName
import org.jetbrains.compose.resources.stringResource
import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.app_name
import pocketquest.composeapp.generated.resources.btn_begin_run
import pocketquest.composeapp.generated.resources.character_select_title
import pocketquest.composeapp.generated.resources.label_hp
import pocketquest.composeapp.generated.resources.label_mp
import pocketquest.composeapp.generated.resources.label_ac
import pocketquest.composeapp.generated.resources.label_str
import pocketquest.composeapp.generated.resources.label_dex
import pocketquest.composeapp.generated.resources.label_int
import pocketquest.composeapp.generated.resources.label_con
import pocketquest.composeapp.generated.resources.label_skills

private val ColorBg       = Color(0xFF0d1117)
private val ColorCard     = Color(0xFF161b22)
private val ColorBorder   = Color(0xFF30363d)
private val ColorSelected = Color(0xFF388bfd)
private val ColorText     = Color(0xFFc9d1d9)
private val ColorSubtext  = Color(0xFF8b949e)
private val ColorHp       = Color(0xFF238636)
private val ColorMana     = Color(0xFF388bfd)

@Composable
fun CharacterSelectScreen(
    availableCharacters: List<UnitTemplate>,
    runStateHolder: RunStateHolder,
    onRunStarted: () -> Unit,
) {
    var selectedId by remember { mutableStateOf(availableCharacters.firstOrNull()?.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(Res.string.app_name),
            color = ColorSelected,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
        )
        Text(
            text = stringResource(Res.string.character_select_title),
            color = ColorSubtext,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(availableCharacters) { character ->
                CharacterCard(
                    character = character,
                    isSelected = character.id == selectedId,
                    onClick = { selectedId = character.id },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                val id = selectedId ?: return@Button
                runStateHolder.startRun(id)
                onRunStarted()
            },
            enabled = selectedId != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = ColorSelected,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF21262d),
                disabledContentColor = ColorSubtext,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(stringResource(Res.string.btn_begin_run), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun CharacterCard(
    character: UnitTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) ColorSelected else ColorBorder
    val bgColor = if (isSelected) ColorSelected.copy(alpha = 0.08f) else ColorCard

    Column(
        modifier = Modifier
            .width(200.dp)
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Name
        Text(
            text = unitLocalizedName(character.id),
            color = ColorText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        // Stats row
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatPill(label = stringResource(Res.string.label_hp), value = character.maxHealth.toString(), color = ColorHp)
            StatPill(label = stringResource(Res.string.label_mp), value = character.maxMana.toString(), color = ColorMana)
            StatPill(label = stringResource(Res.string.label_ac), value = character.ac.toString(), color = ColorSubtext)
        }

        // Core stats
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            StatLine(stringResource(Res.string.label_str), character.str)
            StatLine(stringResource(Res.string.label_dex), character.dex)
            StatLine(stringResource(Res.string.label_int), character.intelligence)
            StatLine(stringResource(Res.string.label_con), character.con)
        }

        // Starting skills
        if (character.skillIds.isNotEmpty()) {
            Text(text = stringResource(Res.string.label_skills), color = ColorSubtext, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                character.skillIds.forEach { skillId ->
                    Text(
                        text = "· ${skillLocalizedName(skillId)}",
                        color = ColorSubtext,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = ColorSubtext, fontSize = 9.sp)
    }
}

@Composable
private fun StatLine(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ColorSubtext, fontSize = 11.sp)
        Text(value.toString(), color = ColorText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
