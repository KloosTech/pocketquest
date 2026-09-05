package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER

/**
 * Mostly a placeholder for this Android-shell pass — no persisted-settings mechanism or audio
 * system exists anywhere yet — but doubles as the only way to abandon an active run for testing
 * (restarting a campaign otherwise means force-quitting). [onEndCampaign] is null when reached
 * from `HubScreen` (no active run to end); non-null when reached from the in-run battle-log menu.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onEndCampaign: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Settings", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        BasicText("Nothing to configure yet.", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        Spacer(modifier = Modifier.size(16.dp))
        InkButton("Back", onClick = onBack)
        if (onEndCampaign != null) {
            Spacer(modifier = Modifier.size(24.dp))
            InkButton("End Campaign", onClick = onEndCampaign)
        }
    }
}
