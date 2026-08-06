package io.github.jackbeback

import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

fun main() = application {

    Window(
        onCloseRequest = ::exitApplication,
        title = "PocketQuest",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
    ) {
        App()
    }
}
