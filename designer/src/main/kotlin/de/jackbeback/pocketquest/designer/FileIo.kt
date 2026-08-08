package de.jackbeback.pocketquest.designer

import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.model.Catalog
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val PRETTY_JSON = Json { prettyPrint = true }

/**
 * Plain JSON files, not Room — this is authored CONTENT (versioned in git alongside the demo
 * catalog), not runtime save state, which is what `:data`'s Room usage is reserved for. Swing's
 * `JFileChooser` rather than a Compose file picker: v1's own designer used AWT/Swing dialogs for
 * exactly this (`DesignerFileDialogs.kt`), and this module already isn't part of the Compose-
 * Multiplatform-portable graph (it's desktop-only), so there's no portability cost to it.
 */
object DesignerFileIo {
    /**
     * The one location both this editor and `:app` agree on for authored content — no canonical
     * on-disk catalog existed before this (`:app`'s `Main.kt` hardcodes its own demo catalog as a
     * Kotlin string), so `content/catalog.json` at the repo root is a new, deliberate convention:
     * outside every Gradle module (it's content, not code) and easy for `:app` to `File(...).readText()`
     * without needing Compose resources. Resolved the same way [SpriteLoader]/[AssetManifest] find
     * `assets/` — by walking up from wherever `:designer:run`'s working directory happens to be.
     */
    fun defaultCatalogFile(): File {
        val repoRoot = listOf(File("."), File(".."), File("../..")).firstOrNull { File(it, "assets").isDirectory } ?: File(".")
        val dir = File(repoRoot, "content")
        dir.mkdirs()
        return File(dir, "catalog.json")
    }

    fun chooseLoadFile(): File? {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Catalog JSON", "json") }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun chooseSaveFile(): File? {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Catalog JSON", "json") }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val chosen = chooser.selectedFile
        return if (chosen.extension == "json") chosen else File(chosen.parentFile, "${chosen.name}.json")
    }

    fun load(file: File): Catalog = CatalogLoader.parse(file.readText())

    fun save(file: File, catalog: Catalog) {
        file.writeText(PRETTY_JSON.encodeToString(Catalog.serializer(), catalog))
    }
}
