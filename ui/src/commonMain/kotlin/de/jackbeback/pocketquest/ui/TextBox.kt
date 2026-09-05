package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * docs/36-map-triggers.md: the blocking modal a [PendingMessage] renders as — [RollCard]'s own
 * ink-on-parchment framing (`PAPER_SHEET` background, `INK` border), not a native dialog, so it
 * reads as part of this game's flat visual language rather than a system popup. Dismissal calls
 * [VisualWorld.dismissMessage], which completes the [PendingMessage.dismissed] deferred that
 * [VisualWorld.showMessage]'s Beat is suspended on — that's the entire mechanism, no resolver
 * involvement.
 */
@Composable
fun TextBoxOverlay(message: PendingMessage, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 360.dp).background(PAPER_SHEET).border(1.dp, INK).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(message.text, style = TextStyle(color = INK, fontSize = 15.sp))
        Spacer(Modifier.size(16.dp))
        InkButton("Continue", onClick = onDismiss)
    }
}
