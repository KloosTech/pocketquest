package io.github.jackbeback.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import io.github.jackbeback.data.*
import io.github.jackbeback.vm.*
import kotlinx.coroutines.*

@Composable
fun SkillSelection(modifier: Modifier = Modifier, active: Boolean = true) {
    val unitVm = remember { UnitViewModel.instance }
    val stateVm = remember { StateViewModel.instance }
    val skillVm = remember { SkillViewModel.instance }
    val overlayVm = remember { OverlayViewModel.instance }

    val selectedPlayer by unitVm.selectedPlayer.collectAsState()
    val selectedEnemy by unitVm.selectedEnemy.collectAsState()


    // Farben für die Boxen
    val skills = listOf(
        thornWhip,
        basicHeal,
        magicMissiles,
        fireBall
    )

    var selected: Int? by remember { mutableStateOf(null) }

    LaunchedEffect(active) {
        if (!active) selected = null
    }

    LaunchedEffect(selected){
        if (selected == null){
            overlayVm.reset()
        }else{
            //selectedPlayer?.let { overlayVm.showRangeOverlay(it.pos, 2) }
        }
    }

    // BoxSize und Animationskonstanten
    val boxSize = 100.dp
    val gap = 10.dp
    val animationDuration = 300 // Millisekunden pro Box
    val delayBetweenBoxes = 50 // Verzögerung zwischen den Boxen

    val animatedAlpha by animateFloatAsState(if (selected != null) 1f else 0f, animationSpec = tween(500))

    // Bildschirmbreite ermitteln
    var screenWidth by remember { mutableStateOf(0.dp) }

    // Berechnung des gleichmäßigen Abstands zwischen den Boxen
    val totalBoxesWidth = boxSize * skills.size
    val availableSpace = screenWidth - totalBoxesWidth
    val spaceBetween = availableSpace / (skills.size + 1)

    BoxWithConstraints(modifier.fillMaxWidth().height(boxSize * 2 + gap)) {
        screenWidth = maxWidth
        // Box-Positionen und Animationen
        skills.forEachIndexed { index, skill ->
            // Berechne die Position jeder Box basierend auf dem Index
            val basePosition = spaceBetween + index * (boxSize + spaceBetween)

            // Position für die Anfangs- und Endanimation
            val offscreenRightPosition = screenWidth + boxSize
            val offscreenLeftPosition = -boxSize - 1.dp

            // Animation für die X-Position
            val animatableOffset = remember {
                Animatable(if (active) offscreenRightPosition.value else basePosition.value)
            }

            // Animation basierend auf dem aktiven Status
            val targetPosition = if (active) basePosition.value else offscreenLeftPosition.value
            val initialDelay = if (active)
                index * delayBetweenBoxes
            else
                (skills.size - 1 - index) * delayBetweenBoxes

            LaunchedEffect(active) {
                delay(initialDelay.toLong())
                animatableOffset.animateTo(
                    targetValue = targetPosition,
                    animationSpec = tween(
                        durationMillis = animationDuration,
                        easing = EaseInOutCubic
                    )
                )
            }

            // Rendern der Box mit animiertem Offset
            Box(
                Modifier
                    .size(boxSize)
                    .offset(x = animatableOffset.value.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .alpha(if (selected == index) 1f else 0.75f)
                    .clickable {
                        selected = if (selected != index) index else null
                    }.align(Alignment.BottomStart)
            ) {
                skill.skillIcon()
            }
        }

        //Skill Description
        if (active) {
            Box(
                Modifier.fillMaxWidth()
                    .alpha(animatedAlpha)
                    .padding(start = 10.dp, end = 10.dp)
            ) {
                // Skill-Beschreibungsbox
                val skill = skills.getOrNull(selected ?: -1)
                AnimatedCompactSkillDescription(
                    skill = skill,
                    buttonEnabled = when {
                        selectedPlayer == null -> false
                        (selectedPlayer?.resources?.action?.current ?: 0) < 1 -> false
                        skill?.needTarget == true -> selectedEnemy != null
                        else -> true
                    },
                    onUse = { s ->
                        //take action
                        selectedPlayer?.let { player -> unitVm.updateUnit(s.action.onSource((player as Player).takeAction())) }
                        selectedEnemy?.let { enemy ->
                            skillVm.addSkill(
                                createTargetSkill(
                                    (selectedPlayer as Player).pos,
                                    enemy.pos,
                                    s
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    theme = skills.getOrNull(selected ?: -1)?.theme
                )
            }
        }

        if (selectedPlayer != null) {
            Button(onClick = {
                selected = null
                stateVm.advanceGameState()
            }, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Text("End Turn")
            }
        }
    }
}


@Composable
fun CompactSkillDescriptionBox(
    skill: Skill,
    onUse: suspend () -> Unit,
    buttonEnabled: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    theme: SkillTheme = SkillTheme.ARCANE
) {
    // Animation für das Erscheinen
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha"
    )

    // Themenfarben bestimmen
    val (primaryColor, secondaryColor, accentColor) = when (theme) {
        SkillTheme.ARCANE -> Triple(
            Color(0xFF7E57C2), // Lila Hauptfarbe
            Color(0xFF5E35B1), // Dunkleres Lila
            Color(0xFFB39DDB)  // Helleres Lila
        )

        SkillTheme.NATURE -> Triple(
            Color(0xFF66BB6A), // Grün
            Color(0xFF43A047), // Dunkleres Grün
            Color(0xFFA5D6A7)  // Helleres Grün
        )

        SkillTheme.FIRE -> Triple(
            Color(0xFFEF5350), // Rot
            Color(0xFFD32F2F), // Dunkleres Rot
            Color(0xFFFF8A80)  // Helleres Rot
        )

        SkillTheme.ICE -> Triple(
            Color(0xFF29B6F6), // Blau
            Color(0xFF0288D1), // Dunkleres Blau
            Color(0xFF81D4FA)  // Helleres Blau
        )
    }

    // Pulsierende Glanz-Animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = modifier
            .alpha(animatedAlpha)
            .height(100.dp)  // Genau 100dp Höhe
            .padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        // Hintergrund mit subtilen Effekten
        Canvas(modifier = Modifier.matchParentSize()) {
            // Basis-Hintergrund
            drawRoundRect(
                color = Color(0xFF212121),
                cornerRadius = CornerRadius(10.dp.toPx()),
                alpha = 0.8f
            )

            // Leuchtender Rand
            drawRoundRect(
                color = primaryColor,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.5f.dp.toPx()),
                alpha = 0.7f
            )
        }

        // Inhalt
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skill-Symbol
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor, primaryColor),
                            radius = 20f
                        ),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(24.dp)) {
                    skill.skillIcon()
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text-Bereich (Titel und Beschreibung)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Skill-Titel
                Text(
                    text = skill.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Skill-Beschreibung
                Text(
                    text = skill.description,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Kompakter "Anwenden"-Button
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.Default).launch {
                        onUse()
                    }
                },
                modifier = Modifier
                    .size(width = 85.dp, height = 32.dp)
                    .shadow(4.dp, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = primaryColor.copy(alpha = 0.5f),
                    contentColor = Color.White.copy(alpha = 0.5f)
                ),
                enabled = buttonEnabled,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Text(
                    text = "ANWENDEN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// Animation für das Erscheinen der Skill-Box
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedCompactSkillDescription(
    skill: Skill?,
    onUse: suspend (Skill) -> Unit,
    buttonEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    theme: SkillTheme? = SkillTheme.ARCANE
) {
    AnimatedContent(
        targetState = skill,
        transitionSpec = {
            (fadeIn(animationSpec = tween(200)) +
                slideInVertically(animationSpec = tween(200)) { height -> height / 2 })
                .togetherWith(
                    fadeOut(animationSpec = tween(200)) +
                        slideOutVertically(animationSpec = tween(200)) { height -> -height / 2 }
                )
        },
        label = "skillAnimation"
    ) { targetSkill ->
        if (targetSkill != null) {
            CompactSkillDescriptionBox(
                skill = targetSkill,
                buttonEnabled = buttonEnabled,
                onUse = { onUse(targetSkill) },
                modifier = modifier,
                theme = theme ?: SkillTheme.ARCANE
            )
        } else {
            Box(modifier = modifier.height(100.dp))  // Platzhalter mit korrekter Höhe
        }
    }
}

// Stilthemen für unterschiedliche Skill-Typen
enum class SkillTheme {
    ARCANE, NATURE, FIRE, ICE
}



