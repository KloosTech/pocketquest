package de.jackbeback.pocketquest.ui.assets

import de.jackbeback.pocketquest.ui.generated.resources.Res

/**
 * The Android/iOS counterpart to `:app`'s desktop `Main.kt`, which reads `content/catalog.json`
 * live off disk for `:designer`'s hot-edit workflow — an installed app has no live filesystem to
 * read from, so it reads the bundled snapshot `:ui`'s own `build.gradle.kts` copies into Compose
 * Resources on every build instead (see that file's own doc comment).
 */
suspend fun loadBundledCatalogJson(): String = Res.readBytes("files/catalog.json").decodeToString()
