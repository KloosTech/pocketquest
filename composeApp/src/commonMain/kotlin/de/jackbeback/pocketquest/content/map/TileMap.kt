package de.jackbeback.pocketquest.content.map

import kotlinx.serialization.Serializable

@Serializable
data class TileMap(
    val id: String,
    val name: String,
    val cols: Int,
    val rows: Int,
    val tileWidthPx: Int,
    val tileHeightPx: Int,
    val tiles: List<TileData> = emptyList(),
) {
    private val index: Map<Pair<Int, Int>, TileType> by lazy {
        tiles.associate { (it.col to it.row) to it.type }
    }

    fun typeAt(col: Int, row: Int): TileType = index[col to row] ?: TileType.FLOOR

    fun isWalkable(col: Int, row: Int): Boolean = typeAt(col, row).isWalkable

    fun neighbours(col: Int, row: Int): List<TileType> =
        listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)
            .map { (dc, dr) -> typeAt(col + dc, row + dr) }
}

@Serializable
data class TileData(val col: Int, val row: Int, val type: TileType)
