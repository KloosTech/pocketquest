package de.jackbeback.pocketquest.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/**
 * The whole of :ui for now: one screen, no navigation, no ViewModel — a
 * smoke test proving the module boundary (only :ui imports Compose) and
 * the render path work, nothing more. :app builds the log entries by
 * running the real engine/persistence pipeline and hands them here as
 * plain strings.
 */
@Composable
fun App(log: List<String>) {
    MaterialTheme {
        Surface {
            LazyColumn(modifier = Modifier.padding(16.dp)) {
                items(log) { line -> Text(line) }
            }
        }
    }
}

fun runDesktopApp(log: List<String>) = application {
    Window(onCloseRequest = ::exitApplication, title = "PocketQuest — end-to-end smoke test") {
        App(log)
    }
}
