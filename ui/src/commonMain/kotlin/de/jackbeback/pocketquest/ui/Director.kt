package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.tween
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Rejection
import kotlinx.coroutines.delay

/** doc07: one place decides timing. Nothing else in the codebase knows how long anything takes. */
sealed interface Timing {
    data object Blocking : Timing
    data object Parallel : Timing
    data object Instant : Timing
}

data class Beat(val timing: Timing, val play: suspend (VisualWorld) -> Unit)

private const val ATTACK_PULSE_MS = 180
private const val HURT_FLASH_MS = 200
private const val STATUS_POP_MS = 200
private const val STATUS_EXPIRE_MS = 200
private const val DEATH_FADE_MS = 400
private const val DAMAGE_NUMBER_HOLD_MS = 700L
private const val HP_ANIMATE_MS = 250
private const val MOVE_STEP_MS = 180
private const val REACTION_MARKER_MS = 150
private const val REDIRECT_ARC_HOLD_MS = 500L
private const val FIZZLE_FLASH_HOLD_MS = 400L
private const val TELEPORT_BLINK_MS = 300

/**
 * doc07's choreograph(): "adding a new game event means adding a when
 * branch here." Covers what the demo scenario actually emits
 * (AttackRolled/DamageTaken/Healed/StatusApplied), MoveStepped per doc07's
 * own sketch (KNOWN_ISSUES.md #1 — missing until now, never exercised
 * since nothing in the demo moves), plus doc15's "things the engine emits
 * that need a visual" (Downed, StatusExpired, DamageRedirected,
 * ReactionTriggered, and Fizzled when its Rejection is Blocked). Died
 * itself carries no visual — see its own branch below. ActionStarted/
 * ResourcesSpent/ResourcesReset/TurnStarted/TurnEnded fall through to the
 * empty default — there is no HUD or turn banner yet for them to drive.
 *
 * Every beat that mutates a *shared* per-entity [Animatable] (`scale`,
 * `hp`) is [Timing.Blocking], never [Timing.Parallel] — found the hard way,
 * by an actual run hanging rather than by inspection: `Animatable` allows
 * only one in-flight mutation at a time and cancels+replaces a prior one
 * the instant a second `animateTo` targets it, so two Parallel beats
 * landing close together on the same entity (e.g. Firebolt's DamageTaken
 * immediately followed by its own StatusApplied, both on the hero) raced
 * to mutate the same Animatable and deadlocked the whole player instead of
 * cleanly interrupting. Only [floatNumber] stays Parallel — it only ever
 * touches its own freshly-created overlay entry, never shared state.
 */
fun choreograph(event: GameEvent): List<Beat> = when (event) {
    is GameEvent.MoveStepped -> listOf(
        Beat(Timing.Blocking) { world -> world.walk(event.who, event.to) },
    )
    // doc17-engine-gaps.md 3.1: a blink, not a walk — fade out, snap to the destination (no tween
    // across the intervening tiles, since nothing was actually crossed), fade back in.
    is GameEvent.Teleported -> listOf(
        Beat(Timing.Blocking) { world -> world.blink(event.who, event.to) },
    )
    is GameEvent.AttackRolled -> listOf(
        Beat(Timing.Blocking) { world -> world.pulse(event.attacker, 1.3f, ATTACK_PULSE_MS) },
    )
    is GameEvent.DamageTaken -> listOf(
        Beat(Timing.Parallel) { world -> world.floatNumber(event.target, -event.amount, event.damageType) },
        Beat(Timing.Blocking) { world -> world.pulse(event.target, 0.85f, HURT_FLASH_MS) },
        Beat(Timing.Blocking) { world -> world.applyDelta(event.target, -event.amount) },
    )
    is GameEvent.Healed -> listOf(
        Beat(Timing.Parallel) { world -> world.floatNumber(event.target, event.amount, damageType = null) },
        Beat(Timing.Blocking) { world -> world.applyDelta(event.target, event.amount) },
    )
    // Died carries no visual of its own — Downed (below) always fires alongside it on the same
    // 0-HP transition and owns the fade, since "downed" (still on the board, revivable) is the
    // visually-true state today; nothing removes an entity from the board at all yet.
    is GameEvent.Died -> emptyList()
    is GameEvent.Downed -> listOf(
        Beat(Timing.Blocking) { world -> world.entities[event.target]?.alpha?.animateTo(DOWNED_ALPHA, tween(world.scaled(DEATH_FADE_MS))) },
    )
    is GameEvent.StatusApplied -> listOf(
        Beat(Timing.Blocking) { world -> world.pulse(event.target, 1.15f, STATUS_POP_MS) },
    )
    is GameEvent.StatusExpired -> listOf(
        Beat(Timing.Blocking) { world -> world.pulse(event.target, 0.85f, STATUS_EXPIRE_MS) },
    )
    // doc15: "an arc from the original target to the tank, then the number lands on the tank" —
    // the arc is this beat; the number is DamageTaken's existing floatNumber beat, which always
    // follows immediately since the resolver emits DamageRedirected before DamageTaken.
    is GameEvent.DamageRedirected -> listOf(
        Beat(Timing.Blocking) { world -> world.showRedirectArc(event.from, event.to) },
    )
    // doc15: "a brief marker on the reactor before its attack animation, so an interruption reads
    // as an interruption" — the reactor's own AttackRolled/DamageTaken beats follow right after in
    // the same emitted list, so this is genuinely a marker-then-attack sequence, not a guess.
    is GameEvent.ReactionTriggered -> listOf(
        Beat(Timing.Blocking) { world -> world.pulse(event.who, 1.4f, REACTION_MARKER_MS) },
    )
    // doc15: "a blocked flash on the affected tile plus a log line" — the log line already exists
    // (App.kt logs every emitted event unconditionally). Only Rejection.Blocked carries a tile to
    // flash; every other Rejection reason (missing target, insufficient resources, prevented...)
    // has nothing to point a tile-flash at, so those get the log line only, not a guessed position.
    // doc17-engine-gaps.md 3.1: no "pop in" beat — settle()'s getOrPut only creates the new
    // entity's VisualEntity AFTER beats drain, so a beat referencing world.entities[event.entityId]
    // here would find nothing yet. It simply appears once settle() runs; a real appear-animation
    // would need settle() reordered ahead of the beat queue, which nothing has asked for.
    is GameEvent.EntitySpawned -> emptyList()
    // Fades to fully invisible (0f, not DOWNED_ALPHA) before settle() removes its VisualEntity
    // entirely — genuine removal, unlike Downed's "still here, low-contrast."
    is GameEvent.EntityDestroyed -> listOf(
        Beat(Timing.Blocking) { world -> world.entities[event.target]?.alpha?.animateTo(0f, tween(world.scaled(DEATH_FADE_MS))) },
    )
    is GameEvent.Fizzled -> (event.reason as? Rejection.Blocked)?.let { blocked ->
        listOf(Beat(Timing.Blocking) { world -> world.flashTile(blocked.pos) })
    } ?: emptyList()
    else -> emptyList()
}

/** doc07: "all durations must go through a single scale factor" — `speed = 0` collapses every hold to nothing. */
fun VisualWorld.scaled(ms: Int): Int = if (speed <= 0f) 0 else (ms / speed).toInt().coerceAtLeast(0)

private suspend fun VisualWorld.walk(id: EntityId, to: GridPos) {
    val v = entities[id] ?: return
    v.pos.animateTo(to.toOffset(tilePx), tween(scaled(MOVE_STEP_MS)))
}

private suspend fun VisualWorld.blink(id: EntityId, to: GridPos) {
    val v = entities[id] ?: return
    val restingAlpha = v.alpha.value // usually 1f, but DOWNED_ALPHA for a downed entity — restore that, not a hardcoded 1f
    val half = scaled(TELEPORT_BLINK_MS) / 2
    v.alpha.animateTo(0f, tween(half))
    v.pos.snapTo(to.toOffset(tilePx)) // no tween across the intervening tiles — nothing was crossed
    v.alpha.animateTo(restingAlpha, tween(half))
}

private suspend fun VisualWorld.pulse(id: EntityId, factor: Float, ms: Int) {
    val v = entities[id] ?: return
    val d = scaled(ms) / 2
    v.scale.animateTo(factor, tween(d))
    v.scale.animateTo(1f, tween(d))
}

private suspend fun VisualWorld.applyDelta(id: EntityId, delta: Int) {
    val v = entities[id] ?: return
    v.hp.animateTo((v.hp.value + delta).coerceAtLeast(0f), tween(scaled(HP_ANIMATE_MS)))
}

private suspend fun VisualWorld.showRedirectArc(from: EntityId, to: EntityId) {
    val fromV = entities[from] ?: return
    val toV = entities[to] ?: return
    val id = addMarker(Marker.Arc(fromV.pos.value, toV.pos.value))
    if (speed > 0f) delay((REDIRECT_ARC_HOLD_MS / speed).toLong())
    removeMarker(id)
}

private suspend fun VisualWorld.flashTile(pos: GridPos) {
    val id = addMarker(Marker.TileFlash(pos))
    if (speed > 0f) delay((FIZZLE_FLASH_HOLD_MS / speed).toLong())
    removeMarker(id)
}

private suspend fun VisualWorld.floatNumber(
    id: EntityId,
    amount: Int,
    damageType: DamageType?,
) {
    val v = entities[id] ?: return
    val overlayId = addOverlay(id, amount, damageType, v.pos.value)
    if (speed > 0f) delay((DAMAGE_NUMBER_HOLD_MS / speed).toLong())
    removeOverlay(overlayId)
}
