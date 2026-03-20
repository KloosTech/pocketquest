package de.jackbeback.pocketquest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.jackbeback.pocketquest.di.initKoin
import de.jackbeback.pocketquest.ui.designer.DC
import de.jackbeback.pocketquest.ui.designer.EncounterDesignerApp

private enum class LaunchMode { GAME, DESIGNER }

fun main() = application {
    var mode by remember { mutableStateOf<LaunchMode?>(null) }

    // ── Launcher window (shown until user picks a mode) ───────────────────
    if (mode == null) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PocketQuest",
            state = rememberWindowState(width = 720.dp, height = 900.dp),
            resizable = false,
        ) {
            LauncherScreen(
                onPlayGame = {
                    initKoin()
                    mode = LaunchMode.GAME
                },
                onOpenDesigner = {
                    mode = LaunchMode.DESIGNER
                },
            )
        }
    }

    // ── Game window ───────────────────────────────────────────────────────
    if (mode == LaunchMode.GAME) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PocketQuest",
            state = rememberWindowState(width = 900.dp, height = 700.dp),
        ) {
            App()
        }
    }

    // ── Designer window ───────────────────────────────────────────────────
    if (mode == LaunchMode.DESIGNER) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PocketQuest — Encounter Designer",
            state = rememberWindowState(width = 1280.dp, height = 820.dp),
        ) {
            EncounterDesignerApp()
        }
    }
}

@Composable
private fun LauncherScreen(
    onPlayGame: () -> Unit,
    onOpenDesigner: () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DC.Background,
            surface = DC.Panel,
            primary = DC.Primary,
            onBackground = DC.Text,
            onSurface = DC.Text,
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DC.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                // Logo area
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(DC.Primary.copy(alpha = 0.15f))
                            .border(2.dp, DC.Primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⚔", fontSize = 32.sp)
                    }
                    Text("PocketQuest", color = DC.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Choose how to launch", color = DC.Overlay0, fontSize = 13.sp)
                }

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LaunchCard(
                        icon = "▶",
                        title = "Play Game",
                        subtitle = "Start a new roguelike run",
                        accentColor = DC.Green,
                        onClick = onPlayGame,
                    )
                    LaunchCard(
                        icon = "✎",
                        title = "Encounter Designer",
                        subtitle = "Create & test encounters",
                        accentColor = DC.Blue,
                        onClick = onOpenDesigner,
                    )
                }

                Text(
                    "v1.0 — Desktop",
                    color = DC.Overlay0,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun LaunchCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DC.Panel)
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 24.sp, color = accentColor)
        }
        Text(title, color = DC.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = DC.Overlay0, fontSize = 11.sp)
    }
}
