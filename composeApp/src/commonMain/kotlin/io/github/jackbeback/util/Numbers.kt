package io.github.jackbeback.util

import androidx.compose.ui.unit.IntSize
import io.github.jackbeback.data.*
import io.github.jackbeback.vm.*
import kotlin.math.*
import kotlin.math.PI
/**
 * Rundet den Double-Wert auf die angegebene Anzahl von Dezimalstellen.
 *
 * @param digits Die Anzahl der Dezimalstellen, die behalten werden sollen
 * @return Ein Double-Wert mit der angegebenen Anzahl von Dezimalstellen
 */
fun Double.toFixed(digits: Int): Double {
    require(digits >= 0) { "Die Anzahl der Dezimalstellen muss positiv sein" }

    val factor = 10.0.pow(digits)
    return (this * factor).roundToLong() / factor
}

/**
 * Rundet den Double-Wert auf die angegebene Anzahl von Dezimalstellen.
 *
 * @param digits Die Anzahl der Dezimalstellen, die behalten werden sollen
 * @return Ein Double-Wert mit der angegebenen Anzahl von Dezimalstellen
 */
fun Float.toFixed(digits: Int): Float {
    require(digits >= 0) { "Die Anzahl der Dezimalstellen muss positiv sein" }

    val factor = 10.0.pow(digits)
    return ((this * factor).roundToLong() / factor).toFloat()
}

fun Pair<Int, Int>.toPosition(): Position {
    val tileSize = (1.0/MapViewModel.tiles.width)/2f
    return Position(this.first.toDouble()/MapViewModel.tiles.width-tileSize, this.second.toDouble()/MapViewModel.tiles.height-tileSize)
}

fun Position.toTileIndex(): IntSize {
    val size = MapViewModel.tiles

    // Position von 0.0-1.0 zum Tile-Index umwandeln
    // Dabei berücksichtigen wir die Gesamtanzahl der Kacheln

    // Berechne die entsprechenden Tile-Indizes
    // x wird zum horizontalen Index zwischen 0 und (size-1)
    // y wird zum vertikalen Index zwischen 0 und (size-1)

    val tileX = (this.x * size.width).toInt().coerceIn(0, size.width - 1)
    val tileY = (this.y * size.height).toInt().coerceIn(0, size.height - 1)

    return IntSize(tileX, tileY)
}

fun IntSize.getDistanceTo(other: IntSize): Int{
    return sqrt(
        (this.width - other.width).toDouble().pow(2) +
            (this.height - other.height).toDouble().pow(2)
    ).toInt()
}



/**
 * Konvertiert einen Wert von Grad zu Radiant.
 * @return Der Wert in Radiant
 */
fun Number.toRadians(): Double {
    return this.toDouble() * (PI / 180.0)
}

/**
 * Konvertiert einen Wert von Radiant zu Grad.
 * @return Der Wert in Grad
 */
fun Number.toDegrees(): Double {
    return this.toDouble() * (180.0 / PI)
}

