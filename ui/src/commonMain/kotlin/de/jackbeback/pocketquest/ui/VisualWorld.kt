package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameState

/**
 * doc07: contains no rules data — no HP maximum, no faction, only what is
 * needed to draw. `facing`/`clip` from doc07's sketch are omitted: there is
 * no sprite/animation-clip system yet, just colored circles, so nothing
 * would read them — add back when real art exists. `scale` stands in for
 * a lunge/hit pulse in place of real attack/hurt sprites.
 */
@Stable
class VisualEntity(pos: Offset, hp: Float) {
    val pos = Animatable(pos, Offset.VectorConverter)
    val hp = Animatable(hp)
    val scale = Animatable(1f)
    val alpha = Animatable(1f)
}

/** A floating damage/heal number — doc07's "projectiles, numbers, areas" overlay category, numbers only so far. */
data class Overlay(val id: Long, val entityId: EntityId, val amount: Int, val damageType: DamageType?, val pos: Offset)

/**
 * doc07's VisualWorld, adapted: `camera` is omitted — nothing pans on a
 * board this small (10x10 in the demo); add an `Animatable<Offset>` camera
 * when a board bigger than the viewport needs one. `speed` lives here
 * (not on [AnimationPlayer]) so every [Beat]'s `play` lambda — whose
 * signature doc07 fixes to `suspend (VisualWorld) -> Unit` — can read the
 * single scale factor doc07 requires ("all durations must go through a
 * single scale factor") without needing an extra parameter threaded
 * through every call site.
 */
class VisualWorld(initial: GameState, tilePx: Float) {
    val entities = mutableStateMapOf<EntityId, VisualEntity>()
    val overlays = mutableStateListOf<Overlay>()

    /** 1f = normal speed, 0f = every animateTo becomes a snapTo (doc07's "fast" setting and skip). */
    var speed: Float = 1f

    private var nextOverlayId = 0L

    init {
        initial.entities.forEach { e ->
            entities[e.id] = VisualEntity(e.pos?.toOffset(tilePx) ?: Offset.Zero, (e.health?.current ?: 0).toFloat())
        }
    }

    fun addOverlay(entityId: EntityId, amount: Int, damageType: DamageType?, pos: Offset): Long {
        val id = nextOverlayId++
        overlays += Overlay(id, entityId, amount, damageType, pos)
        return id
    }

    fun removeOverlay(id: Long) {
        overlays.removeAll { it.id == id }
    }
}
