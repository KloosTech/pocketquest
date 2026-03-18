package de.jackbeback.pocketquest

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.jackbeback.pocketquest.di.initKoin

fun main() = application {
    initKoin()
    Window(
        onCloseRequest = ::exitApplication,
        title = "PocketQuest",
        state = rememberWindowState(width = 900.dp, height = 700.dp)
    ) {
        App()
    }
}
