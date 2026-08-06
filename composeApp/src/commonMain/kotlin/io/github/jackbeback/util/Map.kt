package io.github.jackbeback.util

import io.github.jackbeback.data.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import ovh.plrapps.mapcompose.api.*
import ovh.plrapps.mapcompose.ui.state.*
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.*

/**
 * Animiert die Position eines Markers von seiner aktuellen Position zu den angegebenen Zielkoordinaten.
 * Diese Funktion kann in einer CoroutineScope aufgerufen werden.
 *
 * @param id Die ID des zu animierenden Markers
 * @param x Die Ziel-X-Koordinate
 * @param y Die Ziel-Y-Koordinate
 * @param durationMillis Die Dauer der Animation in Millisekunden
 * @param onComplete Optional: Wird aufgerufen, wenn die Animation abgeschlossen ist
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun MapState.animateUnit(
    unit: UnitEntity,
    durationMillis: Float = 1000F,
    onComplete: (() -> Unit)? = null
) {
    if (this.getMarkerInfo(unit.id) == null) {
        this.addMarker(unit.id, unit.pos.x, unit.pos.y){
            UnitComposable(unit)
        }
        return
    }
    // Aktuelle Position des Markers abrufen
    val markerInfo = getMarkerInfo(unit.id) ?: return
    val startX = markerInfo.x
    val startY = markerInfo.y

    // Animation mit Kotlin Coroutines
    val startTime = Clock.System.now()
    val endTime = startTime + durationMillis.toLong().milliseconds

    try {
        // Animation Frame für Frame durchführen
        while (Clock.System.now() < endTime) {
            // Aktuelle Zeit und Fortschritt berechnen
            val currentTime = Clock.System.now()
            val elapsed = (currentTime - startTime).inWholeMilliseconds
            val progress = (elapsed / durationMillis).coerceIn(0f, 1f)

            // Easing-Funktion anwenden (optional)
            // Hier wird eine einfache quadratische Ease-In-Out-Funktion verwendet
            val easedProgress = when {
                progress < 0.5f -> 2f * progress * progress
                else -> 1f - (-2f * progress + 2f).pow(2) / 2f
            }

            // Aktuelle Position berechnen
            val currentX = startX + (unit.pos.x - startX) * easedProgress
            val currentY = startY + (unit.pos.y - startY) * easedProgress

            // Marker-Position aktualisieren
            moveMarker(unit.id, currentX, currentY)

            // Kurze Verzögerung für flüssige Animation (~60 FPS)
            delay(16)
        }

        // Endposition sicherstellen
        moveMarker(unit.id, unit.pos.x, unit.pos.y)

        // Callback aufrufen, wenn Animation beendet
        onComplete?.invoke()

    } catch (e: CancellationException) {
        // Animation wurde abgebrochen (z.B. durch Coroutine-Abbruch)
        // Wir könnten hier die letzte Position setzen, aber da wir abgebrochen haben,
        // ist es möglicherweise sinnvoller, nichts zu tun
        throw e
    }
}

/**
 * Animiert die Position eines Markers von seiner aktuellen Position zu den angegebenen Zielkoordinaten.
 * Diese Funktion kann in einer CoroutineScope aufgerufen werden.
 *
 * @param id Die ID des zu animierenden Markers
 * @param x Die Ziel-X-Koordinate
 * @param y Die Ziel-Y-Koordinate
 * @param durationMillis Die Dauer der Animation in Millisekunden
 * @param onComplete Optional: Wird aufgerufen, wenn die Animation abgeschlossen ist
 */
@OptIn(ExperimentalUuidApi::class)
suspend fun MapState.animateAttack(
    targetedSkill: TargetedSkill,
    durationMillis: Float = 1000F,
    onComplete: (() -> Unit)? = null
) {
    if (this.getMarkerInfo(targetedSkill.skill.name) == null) {
        this.addMarker(targetedSkill.skill.name, targetedSkill.start.x, targetedSkill.start.y){
            targetedSkill.projectile()
        }
    }
    // Aktuelle Position des Markers abrufen
    val markerInfo = getMarkerInfo(targetedSkill.skill.name) ?: return
    val startX = markerInfo.x
    val startY = markerInfo.y

    // Animation mit Kotlin Coroutines
    val startTime = Clock.System.now()
    val endTime = startTime + durationMillis.toLong().milliseconds

    try {
        // Animation Frame für Frame durchführen
        while (Clock.System.now() < endTime) {
            // Aktuelle Zeit und Fortschritt berechnen
            val currentTime = Clock.System.now()
            val elapsed = (currentTime - startTime).inWholeMilliseconds
            val progress = (elapsed / durationMillis).coerceIn(0f, 1f)

            // Easing-Funktion anwenden (optional)
            // Hier wird eine einfache quadratische Ease-In-Out-Funktion verwendet
            val easedProgress = when {
                progress < 0.5f -> 2f * progress * progress
                else -> 1f - (-2f * progress + 2f).pow(2) / 2f
            }

            // Aktuelle Position berechnen
            val currentX = startX + (targetedSkill.end.x - startX) * easedProgress
            val currentY = startY + (targetedSkill.end.y - startY) * easedProgress

            // Marker-Position aktualisieren
            moveMarker(targetedSkill.skill.name, currentX, currentY)

            // Kurze Verzögerung für flüssige Animation (~60 FPS)
            delay(16)
        }

        // Endposition sicherstellen
        moveMarker(targetedSkill.skill.name, targetedSkill.end.x, targetedSkill.end.y)

        // Callback aufrufen, wenn Animation beendet
        onComplete?.invoke()

    } catch (e: CancellationException) {
        // Animation wurde abgebrochen (z.B. durch Coroutine-Abbruch)
        // Wir könnten hier die letzte Position setzen, aber da wir abgebrochen haben,
        // ist es möglicherweise sinnvoller, nichts zu tun
        throw e
    }
}

suspend fun MapState.scrollTo(unit: UnitEntity) {
    this.scrollTo(unit.pos.x, unit.pos.y)
}
