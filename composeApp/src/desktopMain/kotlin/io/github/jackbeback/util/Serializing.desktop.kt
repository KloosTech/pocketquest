package io.github.jackbeback.util

import java.io.File

actual fun save(json: String) {
    //save json string to file on disk
    val file = File("game_save.json")
    file.writeText(json)
}

actual fun load(): String? {
    val file = File("game_save.json")
    return if (file.exists()) {
        file.readText()
    } else {
        null
    }
}
