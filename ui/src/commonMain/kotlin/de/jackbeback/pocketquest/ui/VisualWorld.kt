package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.RollBreakdown

/** doc15/doc10: "Downed, not dead" — a 0-HP entity is still on the board, revivable, so it stays
 * visible at reduced contrast rather than fading to fully invisible (which today's engine would
 * otherwise make indistinguishable from "actually removed," a state nothing produces yet). */
const val DOWNED_ALPHA = 0.35f

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
 * doc15's "things the engine emits that need a visual": a redirect arc
 * (`DamageRedirected`) and a blocked-tile flash (`Fizzled` when the reason is
 * `Rejection.Blocked` — the only Rejection variant that carries a tile at
 * all; other reasons get the existing log line only, not a flash with
 * nowhere to draw it).
 */
sealed interface Marker {
    data class Arc(val from: Offset, val to: Offset) : Marker
    data class TileFlash(val pos: GridPos) : Marker
}

data class MarkerOverlay(val id: Long, val marker: Marker)

/**
 * docs/24-projectile-travel-animation.md: a sprite in flight from caster to target. [rotationDegrees]
 * is fixed for the whole flight — computed once from the launch angle, not animated like the dice
 * card's tumbling die. [alpha] fades it out on arrival instead of it just vanishing.
 */
@Stable
class ProjectileVisual(startPos: Offset, val bitmap: ImageBitmap, val rotationDegrees: Float) {
    val pos = Animatable(startPos, Offset.VectorConverter)
    val alpha = Animatable(1f)
}

/**
 * docs/30-hit-telegraph-text.md: "HIT"/"MISS"/"SAVED"/"FAILED" floating over the entity a roll
 * resolved against. [pos] rises and [alpha] fades together (driven by `Director.kt`'s
 * `showTelegraph`), not a static hold like [Overlay]'s damage-number squares — the user asked for
 * "animated," not just "on screen for a moment."
 */
@Stable
class TelegraphVisual(startPos: Offset, val text: String, val color: Color) {
    val pos = Animatable(startPos, Offset.VectorConverter)
    val alpha = Animatable(1f)
}

/**
 * docs/22-dice-roll-ui-and-ability-checks.md's roll card — an important d20 roll (attack/save/
 * out-of-combat check) awaiting its [DiceRoll] tumble, plus everything [RollCard] needs to frame it:
 * [title] ("Attack Roll" / "Dex Save" / "Deception Check"), [target] (AC or DC), [breakdown] for the
 * modifier chip row, [succeeded] for hit/success color framing. [otherResult] is the discarded
 * Advantage/Disadvantage die (null under Normal mode) — when present, [RollCard] shows both dice
 * side by side with [result] highlighted as the one that counted.
 */
data class DiceRollOverlay(
    val id: Long,
    val title: String,
    val result: Int,
    val target: Int,
    val breakdown: RollBreakdown,
    val succeeded: Boolean,
    val otherResult: Int? = null,
)

private fun initialCameraFocus(state: GameState, tilePx: Float): Offset {
    val firstPlayerId = state.turn.order.firstOrNull { state.byId[it]?.actor?.faction == Faction.Player }
    val pos = firstPlayerId?.let { state.byId[it]?.pos }
    return pos?.toOffset(tilePx) ?: Offset(state.map.width * tilePx / 2f, state.map.height * tilePx / 2f)
}

/**
 * doc07's VisualWorld, adapted. `speed` lives here (not on [AnimationPlayer]) so every [Beat]'s
 * `play` lambda — whose signature doc07 fixes to `suspend (VisualWorld) -> Unit` — can read the
 * single scale factor doc07 requires ("all durations must go through a single scale factor")
 * without needing an extra parameter threaded through every call site.
 */
class VisualWorld(initial: GameState, val tilePx: Float) {
    val entities = mutableStateMapOf<EntityId, VisualEntity>()
    val overlays = mutableStateListOf<Overlay>()
    val markers = mutableStateListOf<MarkerOverlay>()
    val diceRolls = mutableStateListOf<DiceRollOverlay>()
    val projectiles = mutableStateMapOf<Long, ProjectileVisual>()
    val telegraphs = mutableStateMapOf<Long, TelegraphVisual>()

    /**
     * World-px point (same unscaled space [VisualEntity.pos] lives in) centered in the viewport.
     * Manual pan writes via `snapTo` (tracks the pointer 1:1, no lag); doc15's auto-follow and the
     * "center on active" button write via `animateTo`. Starts on the first playable character
     * (same "first party member in turn order" pick [TurnOrderStrip] uses for its own pre-combat
     * ring), not the map's own center — a fresh encounter should open already looking at your own
     * party, not wherever the map's bounding box happens to be. Falls back to the map center only
     * if no player-faction entity has a position (shouldn't happen in practice).
     */
    val camera = Animatable(initialCameraFocus(initial, tilePx), Offset.VectorConverter)

    /** doc16: "integer scale factors" keep pixel art crisp — snapped steps only, see [de.jackbeback.pocketquest.ui.MIN_ZOOM]/[MAX_ZOOM]. */
    val zoom = Animatable(1f)

    /** 1f = normal speed, 0f = every animateTo becomes a snapTo (doc07's "fast" setting and skip). */
    var speed: Float = 1f

    private var nextOverlayId = 0L
    private var nextMarkerId = 0L
    private var nextDiceRollId = 0L
    private var nextProjectileId = 0L
    private var nextTelegraphId = 0L

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

    fun addMarker(marker: Marker): Long {
        val id = nextMarkerId++
        markers += MarkerOverlay(id, marker)
        return id
    }

    fun removeMarker(id: Long) {
        markers.removeAll { it.id == id }
    }

    fun addDiceRoll(title: String, result: Int, target: Int, breakdown: RollBreakdown, succeeded: Boolean, otherResult: Int? = null): Long {
        val id = nextDiceRollId++
        diceRolls += DiceRollOverlay(id, title, result, target, breakdown, succeeded, otherResult)
        return id
    }

    fun removeDiceRoll(id: Long) {
        diceRolls.removeAll { it.id == id }
    }

    fun addProjectile(startPos: Offset, bitmap: ImageBitmap, rotationDegrees: Float): Long {
        val id = nextProjectileId++
        projectiles[id] = ProjectileVisual(startPos, bitmap, rotationDegrees)
        return id
    }

    fun removeProjectile(id: Long) {
        projectiles.remove(id)
    }

    fun addTelegraph(startPos: Offset, text: String, color: Color): Long {
        val id = nextTelegraphId++
        telegraphs[id] = TelegraphVisual(startPos, text, color)
        return id
    }

    fun removeTelegraph(id: Long) {
        telegraphs.remove(id)
    }
}
