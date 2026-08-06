package io.github.jackbeback.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import io.github.jackbeback.data.*
import io.github.jackbeback.vm.*
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.*
import pocketquest.composeapp.generated.resources.*

@Composable
fun Grunt(unit: UnitEntity) {
    val unitVm = remember { UnitViewModel.instance }
    val selectedEnemy by unitVm.selectedEnemy.collectAsState()

    val mapVm = remember { MapViewModel.instance }
    val mapScale by mapVm.mapScale.collectAsState()

    val self = unitVm.units.collectAsState().value.firstOrNull() { it.id == unit.id } ?: return
    val density = LocalDensity.current

    val size = 100.dp/density.density

    // Werte für die Schaden/Heilung-Animation
    var lastHealth by remember { mutableStateOf(self.resources.health.current) }
    var showDamageAnimation by remember { mutableStateOf(false) }
    var damageAmount by remember { mutableStateOf(0) }
    var isHealing by remember { mutableStateOf(false) }

    // Beobachte Änderungen an der Gesundheit
    LaunchedEffect(self.resources.health.current) {
        if (self.resources.health.current != lastHealth) {
            val difference = kotlin.math.abs(self.resources.health.current - lastHealth)
            if (difference > 0) {
                damageAmount = difference
                isHealing = self.resources.health.current > lastHealth
                showDamageAnimation = true
                delay(1500) // Animation für 1.5 Sekunden anzeigen
                showDamageAnimation = false
            }
            lastHealth = self.resources.health.current
        }
    }

    Box(Modifier.size(size).offset(y = size/2).scale(mapScale).clickable {
        if (selectedEnemy == null) {unitVm.select(self.id)} else unitVm.deselectEnemy()
    }, contentAlignment = Alignment.Center){
        if (selectedEnemy?.id == unit.id && self.resources.health.current > 0) {
            Image(
                painter = painterResource(Res.drawable.grunt),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                colorFilter = ColorFilter.tint(
                    Color.Red,
                    blendMode = BlendMode.SrcAtop
                )
            )
        }

        if (self.resources.health.current > 0 && selectedEnemy?.id == self.id){
            Column(modifier = Modifier.align(Alignment.TopCenter).padding(bottom = 8.dp).zIndex(1f)) {
                HealthBar(self)
                ConditionBar(self.conditions)
            }
        }

        Image(painter = if (self.resources.health.current > 0) painterResource(Res.drawable.grunt) else painterResource(Res.drawable.tombstone), "", modifier = Modifier.scale(0.95f))

        // Animation für Schaden oder Heilung anzeigen
        AnimatedVisibility(
            visible = showDamageAnimation,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            DamageHealAnimation(
                amount = damageAmount,
                isHealing = isHealing,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}



@Composable
fun DamageHealAnimation(
    amount: Int,
    isHealing: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(true) }
    val offsetY by animateFloatAsState(
        targetValue = if (visible) -50f else -150f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "offset"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1.2f else 0.8f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    LaunchedEffect(amount) {
        visible = true
        delay(1000)
        visible = false
    }

    Box(modifier = modifier) {
        Text(
            text = if (isHealing) "+$amount" else "-$amount",
            color = if (isHealing) Color.Green else Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(y = offsetY.dp)
                .alpha(alpha)
                .scale(scale)
        )
    }
}

