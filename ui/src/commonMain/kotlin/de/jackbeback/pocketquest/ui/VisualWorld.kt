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
 * doc07's VisualWorld, adapted. `speed` lives here (not on [AnimationPlayer]) so every [Beat]'s
 * `play` lambda — whose signature doc07 fixes to `suspend (VisualWorld) -> Unit` — can read the
 * single scale factor doc07 requires ("all durations must go through a single scale factor")
 * without needing an extra parameter threaded through every call site.
 */
class VisualWorld(initial: GameState, val tilePx: Float) {
    val entities = mutableStateMapOf<EntityId, VisualEntity>()
    val overlays = mutableStateListOf<Overlay>()

    /**
     * World-px point (same unscaled space [VisualEntity.pos] lives in) centered in the viewport.
     * Manual pan writes via `snapTo` (tracks the pointer 1:1, no lag); doc15's auto-follow and the
     * "center on active" button write via `animateTo`. Starts centered on the map, not (0,0).
     */
    val camera = Animatable(Offset(initial.map.width * tilePx / 2f, initial.map.height * tilePx / 2f), Offset.VectorConverter)

    /** doc16: "integer scale factors" keep pixel art crisp — snapped steps only, see [de.jackbeback.pocketquest.ui.MIN_ZOOM]/[MAX_ZOOM]. */
    val zoom = Animatable(1f)

    /** 1f = normal speed, 0f = every animateTo becomes a snapTo (doc07's "fast" setting and skip). */
    var speed: Float = 1f

    private var nextOverlayId = 0L

    init {
        // doc02: pos == null means "not on the map" (reserve, dead) — nothing to draw at any
        // position, so it gets no VisualEntity at all rather than a fake Offset.Zero one.
        initial.entities.forEach { e ->
            val pos = e.pos ?: return@forEach
            entities[e.id] = VisualEntity(pos.toOffset(tilePx), (e.health?.current ?: 0).toFloat())
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
