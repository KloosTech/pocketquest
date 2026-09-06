package de.jackbeback.pocketquest.ui

import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.rules.targeting.tilesInShape
import de.jackbeback.pocketquest.ui.assets.GameAssetManifest
import de.jackbeback.pocketquest.ui.assets.GameSpriteLoader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

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
private const val TELEGRAPH_MS = 700
private const val TELEGRAPH_RISE_PX = 40f

// docs/30-hit-telegraph-text.md: same red/green-is-good-or-bad palette already used everywhere
// else (HP bars, the active-turn tile ring) — HIT/FAILED are bad for the target, SAVED is good,
// MISS is neutral (nothing happened to them).
private val TELEGRAPH_HIT_COLOR = Color(0xFFB71C1C)
private val TELEGRAPH_MISS_COLOR = Color(0xFF757575)
private val TELEGRAPH_SAVED_COLOR = Color(0xFF2E7D32)
private val TELEGRAPH_FAILED_COLOR = Color(0xFFB71C1C)
private const val MOVE_STEP_MS = 180
private const val REACTION_MARKER_MS = 150
private const val REDIRECT_ARC_HOLD_MS = 500L
private const val FIZZLE_FLASH_HOLD_MS = 400L
private const val TELEPORT_BLINK_MS = 300
private const val DICE_ROLL_HOLD_MS = 500L
private const val PROJECTILE_MS_PER_TILE = 90
private const val PROJECTILE_MIN_MS = 150
private const val PROJECTILE_MAX_MS = 900
private const val PROJECTILE_FADE_MS = 120
private const val RIPPLE_FLASH_HOLD_MS = 400L

/**
 * doc07's choreograph(): "adding a new game event means adding a when
 * branch here." Covers what the demo scenario actually emits
 * (AttackRolled/DamageTaken/Healed/StatusApplied), MoveStepped per doc07's
 * own sketch (KNOWN_ISSUES.md #1 — missing until now, never exercised
 * since nothing in the demo moves), plus doc15's "things the engine emits
 * that need a visual" (Downed, StatusExpired, DamageRedirected,
 * ReactionTriggered, and Fizzled when its Rejection is Blocked). Died
 * itself carries no visual — see its own branch below. ResourcesSpent/
 * ResourcesReset/TurnStarted/TurnEnded fall through to the empty default —
 * there is no HUD or turn banner yet for them to drive.
 *
 * [state]/[cat] (docs/24-projectile-travel-animation.md) are only needed for
 * `ActionStarted`'s projectile-travel beat — looking up
 * [de.jackbeback.pocketquest.core.model.ActionDef.projectileSprite] and
 * computing the AoE ripple footprint via [tilesInShape] both need the
 * catalog/map, unlike every other beat here which only ever touches
 * [VisualWorld]. [state] is the PRE-update state (same as `App.kt`'s
 * `formatEvent` call uses) — the caster hasn't moved by the time
 * `ActionStarted` fires, so its position there is exactly where the
 * projectile should launch from.
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
fun choreograph(event: GameEvent, state: GameState, cat: Catalog): List<Beat> = when (event) {
    is GameEvent.ActionStarted -> {
        val spriteId = cat.actionDef(event.actionId).projectileSprite
        if (spriteId == null) {
            emptyList()
        } else {
            listOf(Beat(Timing.Blocking) { world -> world.fireProjectile(event, spriteId, state, cat) })
        }
    }
    is GameEvent.MoveStepped -> listOf(
        Beat(Timing.Blocking) { world -> world.walk(event.who, event.to) },
    )
    // doc17-engine-gaps.md 3.1: a blink, not a walk — fade out, snap to the destination (no tween
    // across the intervening tiles, since nothing was actually crossed), fade back in.
    is GameEvent.Teleported -> listOf(
        Beat(Timing.Blocking) { world -> world.blink(event.who, event.to) },
    )
    // The die tumbles and settles on the actual d20 result before the attacker's own lunge —
    // previously this roll was completely silent (the log line was the only trace of it).
    is GameEvent.AttackRolled -> listOf(
        Beat(Timing.Blocking) { world ->
            world.showDiceRoll("Attack Roll", event.d20, event.ac, event.breakdown, event.hit, event.otherD20, attackerId = event.attacker, defenderId = event.target)
        },
        Beat(Timing.Blocking) { world -> world.pulse(event.attacker, 1.3f, ATTACK_PULSE_MS) },
        // docs/30-hit-telegraph-text.md: Parallel, not Blocking — it rides alongside whatever
        // DamageTaken/pulse beats follow (a hit) rather than adding its own hold to the sequence;
        // a miss has nothing else to show at all, so this is its only feedback.
        Beat(Timing.Parallel) { world -> world.showTelegraph(event.target, if (event.hit) "HIT" else "MISS", if (event.hit) TELEGRAPH_HIT_COLOR else TELEGRAPH_MISS_COLOR) },
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
    // Same first-ever visual as AttackRolled — a save previously had no beat at all, only its log line.
    is GameEvent.SaveRolled -> listOf(
        Beat(Timing.Blocking) { world ->
            world.showDiceRoll("${event.ability.name} Save", event.d20, event.dc, event.breakdown, event.success, event.otherD20, attackerId = event.source, defenderId = event.target)
        },
        Beat(Timing.Parallel) { world -> world.showTelegraph(event.target, if (event.success) "SAVED" else "FAILED", if (event.success) TELEGRAPH_SAVED_COLOR else TELEGRAPH_FAILED_COLOR) },
    )
    // docs/36-map-triggers.md: the resolver itself never waits on the player — this Blocking beat's
    // suspend fun (VisualWorld.showMessage) is what actually pauses playback, by awaiting the
    // dismiss tap, same as every other Blocking beat pauses on its own animation/hold finishing.
    is GameEvent.MessageShown -> listOf(Beat(Timing.Blocking) { world -> world.showMessage(event.text) })
    // docs/48/docs/50: Board draws gate sprites/terrain straight from live GameState every
    // recomposition (`state.openGates`/`state.map.terrain`), same as every other map-geometry
    // draw (walls, props) — unlike an entity's Animatable pos/hp, there's nothing to tween, so
    // the redraw is free the instant GameState updates. No Beat needed.
    is GameEvent.GateOpened -> emptyList()
    is GameEvent.TerrainChanged -> emptyList()
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

private suspend fun VisualWorld.showDiceRoll(
    title: String,
    result: Int,
    target: Int,
    breakdown: RollBreakdown,
    succeeded: Boolean,
    otherResult: Int? = null,
    attackerId: EntityId? = null,
    defenderId: EntityId? = null,
) {
    val id = addDiceRoll(title, result, target, breakdown, succeeded, otherResult, attackerId, defenderId)
    // Tumble duration (Dice3D.kt's own animation) plus a short hold on the settled number before
    // it's removed — both go through the same scaled() factor as every other beat's timing.
    delay((scaled(TUMBLE_MS) + scaled(DICE_ROLL_HOLD_MS.toInt())).toLong())
    removeDiceRoll(id)
}

private suspend fun VisualWorld.flashTile(pos: GridPos) {
    val id = addMarker(Marker.TileFlash(pos))
    if (speed > 0f) delay((FIZZLE_FLASH_HOLD_MS / speed).toLong())
    removeMarker(id)
}

/** docs/24: every affected tile flashes together (resolved: simultaneous, not a staggered wave) and clears together. */
private suspend fun VisualWorld.flashTiles(positions: Collection<GridPos>) {
    if (positions.isEmpty()) return
    val ids = positions.map { addMarker(Marker.TileFlash(it)) }
    if (speed > 0f) delay((RIPPLE_FLASH_HOLD_MS / speed).toLong())
    ids.forEach { removeMarker(it) }
}

/**
 * docs/24-projectile-travel-animation.md: launches [spriteId] from [event.who]'s current tile toward
 * "the one that got selected" ([event.point] if the action set one, else the first of
 * [event.targets]) — never one projectile per hit entity, regardless of how many
 * AttackRolled/SaveRolled/DamageTaken events this same cast goes on to emit. Fades out on arrival
 * (never just vanishes) rather than differentiating a hit/miss visual at the projectile itself — the
 * existing dice-roll card and pulse/damage-number beats that follow already carry that distinction.
 * An AoE action (non-[Shape.Single]) additionally flashes every tile in the shape's footprint the
 * moment the projectile lands, reusing the exact geometry [de.jackbeback.pocketquest.core.rules.targeting.affectedBy]
 * itself resolves hits with — a visual ripple, not a second hit-detection pass.
 */
private suspend fun VisualWorld.fireProjectile(event: GameEvent.ActionStarted, spriteId: String, state: GameState, cat: Catalog) {
    val casterVisual = entities[event.who] ?: return
    val origin = casterVisual.pos.value
    val destination = event.point?.toOffset(tilePx)
        ?: event.targets.firstOrNull()?.let { entities[it]?.pos?.value }
        ?: return
    val manifest = GameAssetManifest.load()
    val meta = manifest.prop(spriteId) ?: return
    val bitmap = GameSpriteLoader.load(meta.file) ?: return

    // Source art assumed to face east/rightward by default — atan2's own convention already matches
    // that (0 rad == pointing along +x), so the raw angle needs no offset correction.
    val angleDegrees = (atan2((destination.y - origin.y).toDouble(), (destination.x - origin.x).toDouble()) * 180.0 / PI).toFloat()
    val distanceTiles = (destination - origin).getDistance() / tilePx
    val travelMs = (distanceTiles * PROJECTILE_MS_PER_TILE).roundToInt().coerceIn(PROJECTILE_MIN_MS, PROJECTILE_MAX_MS)

    val id = addProjectile(origin, bitmap, angleDegrees)
    val visual = projectiles[id]
    visual?.pos?.animateTo(destination, tween(scaled(travelMs)))
    visual?.alpha?.animateTo(0f, tween(scaled(PROJECTILE_FADE_MS)))
    removeProjectile(id)

    val point = event.point ?: return
    val shape = cat.actionDef(event.actionId).targeting.shape
    if (shape !is Shape.Single) {
        val casterPos = state.byId[event.who]?.pos ?: return
        flashTiles(tilesInShape(casterPos, point, shape, state.map))
    }
}

/**
 * docs/30-hit-telegraph-text.md: "HIT"/"MISS"/"SAVED"/"FAILED" rising and fading together over
 * [id]'s current position — captured once at spawn (`v.pos.value`), same "doesn't track further
 * movement" convention [floatNumber]'s damage numbers already use.
 */
private suspend fun VisualWorld.showTelegraph(id: EntityId, text: String, color: Color) {
    val v = entities[id] ?: return
    val telegraphId = addTelegraph(v.pos.value, text, color)
    val visual = telegraphs[telegraphId] ?: return
    coroutineScope {
        launch { visual.pos.animateTo(v.pos.value - Offset(0f, TELEGRAPH_RISE_PX), tween(scaled(TELEGRAPH_MS))) }
        launch { visual.alpha.animateTo(0f, tween(scaled(TELEGRAPH_MS))) }
    }
    removeTelegraph(telegraphId)
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
