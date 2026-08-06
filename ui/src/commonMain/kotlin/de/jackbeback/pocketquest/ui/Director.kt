package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.tween
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
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
private const val DEATH_FADE_MS = 400
private const val DAMAGE_NUMBER_HOLD_MS = 700L
private const val HP_ANIMATE_MS = 250

/**
 * doc07's choreograph(): "adding a new game event means adding a when
 * branch here." Covers what the demo scenario actually emits
 * (AttackRolled/DamageTaken/Healed/StatusApplied), plus Died/Fizzled from
 * doc07's own example for completeness even though this demo never
 * produces them. ActionStarted/ResourcesSpent/ResourcesReset/
 * TurnStarted/TurnEnded fall through to the empty default — there is no
 * HUD or turn banner yet for them to drive.
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
    is GameEvent.Died -> listOf(
        Beat(Timing.Blocking) { world -> world.entities[event.target]?.alpha?.animateTo(0f, tween(world.scaled(DEATH_FADE_MS))) },
    )
    is GameEvent.StatusApplied -> listOf(
        Beat(Timing.Blocking) { world -> world.pulse(event.target, 1.15f, STATUS_POP_MS) },
    )
    is GameEvent.Fizzled -> emptyList() // no entity target on this event — nothing to animate at yet.
    else -> emptyList()
}

/** doc07: "all durations must go through a single scale factor" — `speed = 0` collapses every hold to nothing. */
fun VisualWorld.scaled(ms: Int): Int = if (speed <= 0f) 0 else (ms / speed).toInt().coerceAtLeast(0)

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
