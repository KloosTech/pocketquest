package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.PAPER

/** Shown on `Screen.Splash` while `RunApp` loads `MetaState`/`RunState` — replaces the bare "Loading…" text with something on-brand. No app icon/native SplashScreen API yet (docs/45's Android pass deferred that); this is the in-app gate only. */
@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(PAPER), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("POCKETQUEST", style = TextStyle(color = INK, fontSize = 26.sp, fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.size(12.dp))
            BasicText("Loading…", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        }
    }
}
