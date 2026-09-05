package de.jackbeback.pocketquest.designer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** One entry of assets.json's `props`/`overlays` list — `tilesW`/`tilesH` are null for non-footprint kinds (decal, compass, number). */
@Serializable
data class ManifestAsset(val id: String, val file: String, val tilesW: Int? = null, val tilesH: Int? = null, val kind: String = "prop")

@Serializable
private data class AssetManifestFile(val tile: Int = 64, val props: List<ManifestAsset> = emptyList(), val overlays: List<ManifestAsset> = emptyList())

private val manifestJson = Json { ignoreUnknownKeys = true }
private val prettyManifestJson = Json { prettyPrint = true }

private val MANIFEST_CANDIDATES = listOf(
    File("ui/src/commonMain/composeResources/files/normalized/assets.json"),
    File("../ui/src/commonMain/composeResources/files/normalized/assets.json"),
    File("../../ui/src/commonMain/composeResources/files/normalized/assets.json"),
)

/**
 * assets.json (doc16: "A manifest carries frame size, sheet layout ... so nothing is hardcoded")
 * — docs/23-sprite-rendering.md: loaded from `:ui`'s own `composeResources` tree (the one location
 * actually bundled cross-platform), not a separate repo-root duplicate — same relative-path
 * resolution as [SpriteLoader] since the manifest lives right next to the sprites it describes.
 *
 * [importSprite] (the "add new sprites from the editor" ask) backs [file] with a real Compose
 * `mutableStateOf` rather than `by lazy` — every list below reads `file.props` fresh through a
 * plain `get()`, not a cached `by lazy`, specifically so an import's write-back is visible to
 * every open picker immediately, no `:designer` restart needed.
 */
object AssetManifest {
    private val resolvedManifestFile: File? by lazy { MANIFEST_CANDIDATES.firstOrNull { it.exists() } }

    private var file: AssetManifestFile by mutableStateOf(load())

    private fun load(): AssetManifestFile {
        val found = resolvedManifestFile ?: return AssetManifestFile()
        return runCatching { manifestJson.decodeFromString<AssetManifestFile>(found.readText()) }.getOrDefault(AssetManifestFile())
    }

    /** kind == "prop": real placeable furniture/decoration, footprint known — everything [designer.MapEditorPanel]'s Prop tool can place. */
    val placeableProps: List<ManifestAsset> get() = file.props.filter { it.kind == "prop" && it.tilesW != null && it.tilesH != null }

    /** kind == "floor": tileable texture sheets (a grid of swatches) for [designer.MapEditorPanel]'s floor-texture picker. */
    val floorTextures: List<ManifestAsset> get() = file.props.filter { it.kind == "floor" }

    /** docs/23-sprite-rendering.md: kind == "character" — linkable via [designer.ArchetypePanel]'s sprite picker, never placed as map furniture (excluded from [placeableProps] by its own kind filter). */
    val characterSprites: List<ManifestAsset> get() = file.props.filter { it.kind == "character" }

    /** docs/24-projectile-travel-animation.md: kind == "projectile" — linkable via [designer.ActionPanel]'s projectile-sprite picker (ActionDef.projectileSprite). */
    val projectileSprites: List<ManifestAsset> get() = file.props.filter { it.kind == "projectile" }

    /** docs/38-loot-reveal-screen.md: kind == "item" — linkable via [designer.ItemPanel]'s icon picker (ItemDef.icon), shown in the loot-reveal slot machine's reel. */
    val itemSprites: List<ManifestAsset> get() = file.props.filter { it.kind == "item" }

    /** docs/40-status-icons.md: kind == "status" — linkable via [designer.StatusPanel]'s icon picker (StatusDef.icon), drawn in a row above any entity carrying that status. */
    val statusSprites: List<ManifestAsset> get() = file.props.filter { it.kind == "status" }

    val overlays: List<ManifestAsset> get() = file.overlays

    fun prop(id: String): ManifestAsset? = file.props.find { it.id == id }
    fun floorTexture(id: String): ManifestAsset? = floorTextures.find { it.id == id }

    /**
     * docs/28-sprite-import.md: copies [source] into the sprites folder next to assets.json,
     * appends one `props` entry (`kind`, no `tilesW`/`tilesH` — neither "character" nor
     * "projectile" uses a footprint), and writes the manifest back. The rewrite goes through raw
     * [JsonObject] surgery on just the `props` array, not `AssetManifestFile`'s own serializer —
     * the on-disk file carries a `characters` top-level array [AssetManifestFile] doesn't model
     * (unused by any Kotlin code, confirmed by grep, but still real content) that a round-trip
     * through the typed model would silently drop. Returns null if no manifest file was found or
     * the source can't be read — the caller shows that as a failure, not a crash.
     */
    fun importSprite(source: File, kind: String): ManifestAsset? {
        val manifestFile = resolvedManifestFile ?: return null
        if (!source.exists()) return null
        val spritesDir = File(manifestFile.parentFile, "sprites").apply { mkdirs() }
        val id = uniqueId(source.nameWithoutExtension.sanitizeToId())
        val extension = source.extension.ifBlank { "png" }
        val destFile = File(spritesDir, "$id.$extension")
        source.copyTo(destFile, overwrite = false)
        val asset = ManifestAsset(id = id, file = "sprites/${destFile.name}", kind = kind)

        val root = manifestJson.parseToJsonElement(manifestFile.readText()).jsonObject
        val updatedProps = JsonArray(root["props"]?.jsonArray.orEmpty() + assetToJson(asset))
        val updatedRoot = JsonObject(root.toMutableMap().apply { put("props", updatedProps) })
        manifestFile.writeText(prettyManifestJson.encodeToString(JsonObject.serializer(), updatedRoot))

        file = load()
        return asset
    }

    private fun uniqueId(base: String): String {
        val taken = file.props.map { it.id }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("${base}_$n" in taken) n++
        return "${base}_$n"
    }

    private fun assetToJson(asset: ManifestAsset): JsonObject = buildJsonObject {
        put("id", asset.id)
        put("file", asset.file)
        put("kind", asset.kind)
    }
}

private fun String.sanitizeToId(): String = lowercase().map { c -> if (c.isLetterOrDigit()) c else '_' }.joinToString("").trim('_').ifBlank { "sprite" }

/** File-chooser for [AssetManifest.importSprite]'s source image — same `JFileChooser` pattern [DesignerFileIo] already uses for catalog files. */
fun chooseImageFile(): File? {
    val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Image", "png", "jpg", "jpeg") }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
