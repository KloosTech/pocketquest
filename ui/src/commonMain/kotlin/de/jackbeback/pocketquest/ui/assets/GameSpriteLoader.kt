package de.jackbeback.pocketquest.ui.assets

import androidx.compose.ui.graphics.ImageBitmap
import de.jackbeback.pocketquest.ui.generated.resources.Res
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * `:ui`'s real-gameplay counterpart to `:designer`'s desktop-only `SpriteLoader` — reads through
 * Compose Resources (`Res.readBytes`, suspend) instead of `java.io.File`/`ImageIO`, so it actually
 * runs on Android/iOS as well as Desktop. Suspend, not `:designer`'s synchronous `remember{}` — a
 * caller needs `produceState`/`LaunchedEffect` to pull the result into Compose state.
 */
object GameSpriteLoader {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    suspend fun load(relativePath: String): ImageBitmap? {
        cache[relativePath]?.let { return it }
        if (cache.containsKey(relativePath)) return null // a prior miss, cached as null
        val bitmap = runCatching { Res.readBytes("files/normalized/$relativePath").decodeToImageBitmap() }.getOrNull()
        cache[relativePath] = bitmap
        return bitmap
    }
}
