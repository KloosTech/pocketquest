package de.jackbeback.pocketquest.content.map

data class MapConfig(
    val id: String,
    val levelCount: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val colCount: Int,
    val rowCount: Int,
) {
    val fullWidthPx: Int get() = colCount * tileWidth
    val fullHeightPx: Int get() = rowCount * tileHeight
}

// KaerMorhen: 63 columns × 31px wide, 84 rows × 30px tall
val kaerMorhenConfig = MapConfig(
    id = "KaerMorhen",
    levelCount = 1,
    tileWidth = 31,
    tileHeight = 30,
    colCount = 63,
    rowCount = 84,
)

// Throneroom: 9 columns × 100px wide, 13 rows × 100px tall
val throneRoomConfig = MapConfig(
    id = "Throneroom",
    levelCount = 1,
    tileWidth = 100,
    tileHeight = 100,
    colCount = 9,
    rowCount = 13,
)
