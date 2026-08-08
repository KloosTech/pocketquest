package de.jackbeback.pocketquest.ui.assets

import de.jackbeback.pocketquest.ui.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One entry of assets.json's `props`/`overlays` list — the same shape `:designer`'s own
 * `ManifestAsset` uses, deliberately duplicated rather than shared (see [GameAssetManifest]'s own
 * doc comment) so a rename on one side can't silently break the other's serialization.
 */
@Serializable
data class ManifestAsset(val id: String, val file: String, val tilesW: Int? = null, val tilesH: Int? = null, val kind: String = "prop")

@Serializable
internal data class AssetManifestFile(val tile: Int = 64, val props: List<ManifestAsset> = emptyList(), val overlays: List<ManifestAsset> = emptyList())

private val manifestJson = Json { ignoreUnknownKeys = true }

/**
 * The real-gameplay counterpart to `:designer`'s `AssetManifest` — same manifest shape and the
 * same `assets/normalized/assets.json`, but loaded from `:ui`'s own bundled Compose Resources
 * (`Res.readBytes`, works uniformly on Android/iOS/Desktop) instead of `java.io.File`, which only
 * exists on the JVM and can't run on Android/iOS at all. `:designer` is a dev-time tool editing the
 * real repo folder live; `:ui` is the packaged game reading its own bundled copy — two genuinely
 * different runtimes, so this stays a parallel implementation rather than a shared one (same
 * reasoning as keeping `GameSpriteLoader` separate from `:designer`'s `SpriteLoader`).
 *
 * `Res.readBytes` is suspend, so unlike `:designer`'s eager `by lazy`, this loads once into a
 * nullable cached value the first caller (`rememberGameAssetManifest`) populates.
 */
object GameAssetManifest {
    private var cached: AssetManifestFile? = null

    private suspend fun file(): AssetManifestFile {
        cached?.let { return it }
        val bytes = Res.readBytes("files/normalized/assets.json")
        val parsed = runCatching { manifestJson.decodeFromString<AssetManifestFile>(bytes.decodeToString()) }.getOrDefault(AssetManifestFile())
        cached = parsed
        return parsed
    }

    suspend fun load(): Loaded = Loaded(file())

    /** A snapshot of the parsed manifest — avoids every accessor being its own suspend call. */
    class Loaded internal constructor(private val file: AssetManifestFile) {
        val placeableProps: List<ManifestAsset> = file.props.filter { it.kind == "prop" && it.tilesW != null && it.tilesH != null }
        val floorTextures: List<ManifestAsset> = file.props.filter { it.kind == "floor" }

        fun prop(id: String): ManifestAsset? = file.props.find { it.id == id }
        fun floorTexture(id: String): ManifestAsset? = floorTextures.find { it.id == id }
    }
}
