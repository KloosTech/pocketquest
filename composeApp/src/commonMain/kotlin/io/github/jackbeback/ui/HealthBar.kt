package io.github.jackbeback.ui

import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.drawscope.*
import io.github.jackbeback.data.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import io.github.jackbeback.data.Resource
import io.github.jackbeback.data.UnitEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HealthBar(
    unitEntity: UnitEntity,
    modifier: Modifier = Modifier,
    showText: Boolean = true,
    barStyle: HealthBarStyle = HealthBarStyle.Gradient
) {
    val density = LocalDensity.current
    val health = unitEntity.resources.health
    val height = 20.dp / density.density

    // Animierter Füllstand
    val targetFillRatio = (health.current.toFloat() / health.max.toFloat()).coerceIn(0f, 1f)
    val fillRatio by animateFloatAsState(
        targetValue = targetFillRatio,
        animationSpec = tween(durationMillis = 500, easing = EaseOutCubic),
        label = "healthFill"
    )

    // Bestimme die Farbe basierend auf dem Gesundheitszustand
    val healthColor by animateColorAsState(
        targetValue = when {
            targetFillRatio > 0.7f -> Color(0xFF00E676)  // Grün
            targetFillRatio > 0.3f -> Color(0xFFFFD54F)  // Gelb/Orange
            else -> Color(0xFFFF5252)  // Rot
        },
        animationSpec = tween(durationMillis = 300),
        label = "healthColor"
    )

    // Berechne Buff-Indikator
    val buffRatio = (health.buff.toFloat() / health.max.toFloat()).coerceIn(0f, 1f)

    // Pulsierende Animation für kritisch niedrige Gesundheit
    val pulseAnim = rememberInfiniteTransition(label = "pulseAnimation")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = if (targetFillRatio <= 0.2f) 0.6f else 1f,
        targetValue = if (targetFillRatio <= 0.2f) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .height(height)
            .shadow(4.dp, RoundedCornerShape(height / 2))
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0xFF212121))
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Hintergrundraster (optional, für cool-factor)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 8.dp.toPx()
            val gridColor = Color.DarkGray.copy(alpha = 0.3f)

            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Gesundheits-Balken
        when (barStyle) {
            HealthBarStyle.Gradient -> {
                // Gradient Balken
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillRatio)
                        .alpha(pulseAlpha)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    healthColor,
                                    healthColor.copy(alpha = 0.8f),
                                    healthColor.copy(alpha = 0.9f)
                                ),
                                startX = 0f,
                                endX = 300f
                            )
                        )
                )
            }

            HealthBarStyle.Segments -> {
                // Segmentierter Balken
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val segmentWidth = with(density) { 10.dp.toPx() }
                            val segments = (size.width / segmentWidth).toInt()
                            val filledSegments = (segments * fillRatio).toInt()

                            for (i in 0 until segments) {
                                val isFilled = i < filledSegments
                                val segmentColor = if (isFilled) {
                                    healthColor.copy(alpha = if (i % 3 == 0) 1f else 0.7f)
                                } else {
                                    Color.DarkGray.copy(alpha = 0.3f)
                                }

                                val segmentHeight = if (isFilled) size.height else size.height * 0.7f
                                val yOffset = if (isFilled) 0f else (size.height - segmentHeight) / 2

                                drawRect(
                                    color = segmentColor,
                                    topLeft = Offset(i * segmentWidth + 2, yOffset),
                                    size = Size(segmentWidth - 4, segmentHeight),
                                    alpha = if (isFilled) pulseAlpha else 1f
                                )
                            }
                        }
                )
            }

            HealthBarStyle.Hexagonal -> {
                // Hexagonaler Balken
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val hexSize = with(density) { height.toPx() * 0.4f }
                            val hexagons = (size.width / hexSize).toInt()
                            val filledHexagons = (hexagons * fillRatio).toInt()

                            for (i in 0 until hexagons) {
                                val isFilled = i < filledHexagons
                                val hexColor = if (isFilled) {
                                    healthColor
                                } else {
                                    Color.DarkGray.copy(alpha = 0.3f)
                                }

                                val centerX = i * hexSize + hexSize / 2
                                val centerY = size.height / 2

                                drawHexagon(
                                    centerX = centerX,
                                    centerY = centerY,
                                    size = hexSize,
                                    color = hexColor,
                                    alpha = if (isFilled) pulseAlpha else 1f,
                                    filled = isFilled
                                )
                            }
                        }
                )
            }
        }

        // Buff-Indikator (als leuchtende Kante)
        if (buffRatio > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(targetFillRatio)
                    .drawBehind {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.7f),
                                    Color(0xFF64FFDA).copy(alpha = 0.5f)
                                )
                            ),
                            size = Size(width = 4.dp.toPx(), height = size.height),
                            topLeft = Offset(size.width * fillRatio - 4.dp.toPx(), 0f)
                        )
                    }
            )
        }

        // Text-Anzeige
        if (showText) {
            Text(
                text = "${health.current}/${health.max}",
                style = TextStyle(
                    color = Color.White,
                    fontSize = (height.value * 0.7).sp / density.density,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = Offset(1f, 1f),
                        blurRadius = 2f
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// Hilfs-Funktion zum Zeichnen von Hexagonen
private fun DrawScope.drawHexagon(
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    filled: Boolean = true
) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = (i * 60 - 30) * PI / 180
        val x = centerX + size * cos(angle).toFloat()
        val y = centerY + size * sin(angle).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()

    if (filled) {
        drawPath(
            path = path,
            color = color,
            alpha = alpha
        )
    } else {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5f),
            alpha = alpha
        )
    }
}

// Verschiedene Stile für die Gesundheitsanzeige
enum class HealthBarStyle {
    Gradient,
    Segments,
    Hexagonal
}

// Komponente zum Anzeigen von Gesundheit, Mana und Name
@Composable
fun UnitStatusBar(
    unit: UnitEntity,
    modifier: Modifier = Modifier,
    style: HealthBarStyle = HealthBarStyle.Gradient
) {
    Column(
        modifier = modifier
            .padding(8.dp)
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .background(Color(0xFF2D2D2D), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Einheitenname
        Text(
            text = unit.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        // Gesundheitsbalken
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "HP",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(30.dp)
            )

            HealthBar(
                unitEntity = unit,
                modifier = Modifier.fillMaxWidth(),
                barStyle = style
            )
        }

        // Mana-Balken (ähnlich wie Gesundheitsbalken, aber blau)
        val mana = unit.resources.mana
        val manaRatio = (mana.current.toFloat() / mana.max.toFloat()).coerceIn(0f, 1f)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "MP",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.width(30.dp)
            )

            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF212121))
                    .padding(2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(manaRatio)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF2979FF),
                                    Color(0xFF448AFF),
                                    Color(0xFF2979FF)
                                )
                            )
                        )
                )

                Text(
                    text = "${mana.current}/${mana.max}",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(color = Color.Black, offset = Offset(1f, 1f), blurRadius = 2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Vorschau zum Testen
@Composable
fun HealthBarPreview() {
    val previewUnit = io.github.jackbeback.data.Player(
        name = "Held",
        resources = io.github.jackbeback.data.Resources(
            health = io.github.jackbeback.data.Resource(current = 75, max = 100, buff = 10),
            mana = io.github.jackbeback.data.Resource(current = 50, max = 100)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        UnitStatusBar(
            unit = previewUnit,
            style = HealthBarStyle.Gradient,
            modifier = Modifier.fillMaxWidth()
        )

        UnitStatusBar(
            unit = previewUnit,
            style = HealthBarStyle.Segments,
            modifier = Modifier.fillMaxWidth()
        )

        UnitStatusBar(
            unit = previewUnit,
            style = HealthBarStyle.Hexagonal,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
