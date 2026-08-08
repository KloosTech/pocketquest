package de.jackbeback.pocketquest.designer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import javax.imageio.ImageIO

/**
 * doc19: the player's party is the only thing drawn with real sprites — everything else (enemies,
 * icons, pips) is procedural. The Map editor's Party spawn-zone token borrows one of those same
 * sprites for the same reason. Assets live in the repo-root `assets/` folder, outside every Gradle
 * module, so the path is resolved relative to a few likely `:designer:run` working directories
 * rather than assumed — missing/unreadable just falls back to no image, never a crash.
 */
object SpriteLoader {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun load(relativePath: String): ImageBitmap? = cache.getOrPut(relativePath) {
        val candidates = listOf(File(relativePath), File("../$relativePath"), File("../../$relativePath"))
        val file = candidates.firstOrNull { it.exists() } ?: return@getOrPut null
        runCatching { ImageIO.read(file)?.toComposeImageBitmap() }.getOrNull()
    }
}
