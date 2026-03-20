package de.jackbeback.pocketquest.designer.io

import de.jackbeback.pocketquest.content.map.TileData
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.map.TileType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

val mapJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object MapFileIo {

    fun loadSourceImage(file: File): Result<BufferedImage> = runCatching {
        ImageIO.read(file) ?: error("Could not read image: ${file.name}")
    }

    /**
     * Slices [sourceImage] into tiles and writes them alongside a JSON descriptor.
     *
     * Output layout (relative to [outputDir]):
     * - `files/tiles/{mapId}/{col}-{row}.png` — individual tile images
     * - `files/maps/{mapId}.json` — serialised [TileMap]
     */
    fun exportMap(
        sourceImage: BufferedImage,
        mapId: String,
        mapName: String,
        tileWidthPx: Int,
        tileHeightPx: Int,
        cols: Int,
        rows: Int,
        offsetX: Int,
        offsetY: Int,
        paintedTiles: Map<Pair<Int, Int>, TileType>,
        outputDir: File,
    ): Result<TileMap> = runCatching {
        val tilesDir = File(outputDir, "files/tiles/$mapId").also { it.mkdirs() }
        val mapsDir  = File(outputDir, "files/maps").also { it.mkdirs() }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = offsetX + c * tileWidthPx
                val y = offsetY + r * tileHeightPx
                val w = minOf(tileWidthPx, sourceImage.width - x).coerceAtLeast(1)
                val h = minOf(tileHeightPx, sourceImage.height - y).coerceAtLeast(1)
                if (x < sourceImage.width && y < sourceImage.height) {
                    val tile = sourceImage.getSubimage(x, y, w, h)
                    ImageIO.write(tile, "png", File(tilesDir, "$c-$r.png"))
                }
            }
        }

        val tiles = paintedTiles
            .filter { (_, type) -> type != TileType.FLOOR }
            .map { (pos, type) -> TileData(pos.first, pos.second, type) }

        val tileMap = TileMap(
            id           = mapId,
            name         = mapName,
            cols         = cols,
            rows         = rows,
            tileWidthPx  = tileWidthPx,
            tileHeightPx = tileHeightPx,
            tiles        = tiles,
        )

        File(mapsDir, "$mapId.json").writeText(mapJson.encodeToString(tileMap))
        tileMap
    }

    fun saveTileMap(tileMap: TileMap, file: File): Result<Unit> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(mapJson.encodeToString(tileMap))
    }

    fun loadTileMap(file: File): Result<TileMap> = runCatching {
        mapJson.decodeFromString<TileMap>(file.readText())
    }
}
