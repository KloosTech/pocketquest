package de.jackbeback.pocketquest.content.map

enum class TileType(
    val isWalkable: Boolean,
    val movementCost: Int,
    val coverValue: Float,
    val hasHazard: Boolean,
    val hazardDamage: Int,
) {
    FLOOR            (true,  1, 0.00f, false, 0),
    WALL             (false, 1, 0.50f, false, 0),
    WATER            (false, 1, 0.00f, false, 0),
    COVER_LOW        (true,  1, 0.25f, false, 0),
    COVER_HIGH       (true,  1, 0.50f, false, 0),
    DIFFICULT_TERRAIN(true,  2, 0.00f, false, 0),
    HAZARD           (true,  1, 0.00f, true,  3),
    VOID             (false, 1, 0.00f, false, 0),
}
