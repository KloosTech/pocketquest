package de.jackbeback.pocketquest

import androidx.compose.ui.window.ComposeUIViewController
import androidx.room.Room
import de.jackbeback.pocketquest.data.db.PocketQuestDatabase
import de.jackbeback.pocketquest.di.initKoin
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

fun MainViewController() = ComposeUIViewController {
    ensureKoinStarted()
    App()
}

private var koinStarted = false

private fun ensureKoinStarted() {
    if (koinStarted) return
    koinStarted = true
    val docDir = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val dbPath = requireNotNull(docDir?.path) { "Could not resolve iOS document directory" } + "/pocketquest.db"
    initKoin {
        Room.databaseBuilder<PocketQuestDatabase>(name = dbPath).build()
    }
}