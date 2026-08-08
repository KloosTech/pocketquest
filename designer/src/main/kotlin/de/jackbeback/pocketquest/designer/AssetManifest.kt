package de.jackbeback.pocketquest.designer

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of assets.json's `props`/`overlays` list — `tilesW`/`tilesH` are null for non-footprint kinds (decal, compass, number). */
@Serializable
data class ManifestAsset(val id: String, val file: String, val tilesW: Int? = null, val tilesH: Int? = null, val kind: String = "prop")

@Serializable
private data class AssetManifestFile(val tile: Int = 64, val props: List<ManifestAsset> = emptyList(), val overlays: List<ManifestAsset> = emptyList())

private val manifestJson = Json { ignoreUnknownKeys = true }

/**
 * assets.json (doc16: "A manifest carries frame size, sheet layout ... so nothing is hardcoded")
 * — loaded once from the repo-root `assets/` folder, same relative-path resolution as
 * [SpriteLoader] since the manifest lives right next to the sprites it describes.
 */
object AssetManifest {
    private val file: AssetManifestFile by lazy {
        val candidates = listOf(
            File("assets/normalized/assets.json"),
            File("../assets/normalized/assets.json"),
            File("../../assets/normalized/assets.json"),
        )
        val found = candidates.firstOrNull { it.exists() }
        if (found == null) AssetManifestFile() else runCatching { manifestJson.decodeFromString<AssetManifestFile>(found.readText()) }.getOrDefault(AssetManifestFile())
    }

    /** kind == "prop": real placeable furniture/decoration, footprint known — everything [designer.MapEditorPanel]'s Prop tool can place. */
    val placeableProps: List<ManifestAsset> by lazy { file.props.filter { it.kind == "prop" && it.tilesW != null && it.tilesH != null } }

    /** kind == "floor": tileable texture sheets (a grid of swatches) for [designer.MapEditorPanel]'s floor-texture picker. */
    val floorTextures: List<ManifestAsset> by lazy { file.props.filter { it.kind == "floor" } }

    val overlays: List<ManifestAsset> by lazy { file.overlays }

    fun prop(id: String): ManifestAsset? = file.props.find { it.id == id }
    fun floorTexture(id: String): ManifestAsset? = floorTextures.find { it.id == id }
}
