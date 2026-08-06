# 07 — The animation pipeline

Lives entirely in `:ui`. The core knows nothing about it.

## Three states, not two

| State | Owner | Purpose |
| --- | --- | --- |
| `logicalState: GameState` | ViewModel | The truth. Always current. |
| `VisualWorld` | Director | What the screen shows. Lags behind on purpose. |
| `Resolver` | GameSession | Includes the pending stack; persisted. |

`VisualWorld` contains **no rules data**. It has no HP maximum, no mana cost, no
faction logic — only what is needed to draw.

```kotlin
@Stable
class VisualEntity(pos: Offset, hp: Float) {
    val pos    = Animatable(pos, Offset.VectorConverter)   // tile coords as Float
    val hp     = Animatable(hp)
    val scale  = Animatable(1f)
    val alpha  = Animatable(1f)
    var facing by mutableStateOf(Facing.South)
    var clip   by mutableStateOf(Clip.Idle)                 // Idle/Walk/Attack/Hurt/Die
}

class VisualWorld {
    val entities = mutableStateMapOf<EntityId, VisualEntity>()
    val overlays = mutableStateListOf<Overlay>()            // projectiles, numbers, areas
    val camera   = Animatable(Offset.Zero, Offset.VectorConverter)
}
```

`pos` is `Offset`, not `GridPos`: mid-move a figure is at `(3.4, 5.0)`, which
does not exist in the rules model. That is the whole reason for a separate
visual layer.

Note what changed from v1: `AnimationEvent` there carries pre-computed
normalised canvas floats (`fromNormX`) produced inside game logic, with the
collector holding mutable `gridCols` / `gridRows` that the ViewModel writes to.
Coordinates convert at **render** time now, from `GridPos`.

## The Director translates events into beats

One place decides timing. Nothing else in the codebase knows how long anything
takes.

```kotlin
sealed interface Timing {
    data object Blocking : Timing   // queue waits for it
    data object Parallel : Timing   // starts, queue continues
    data object Instant  : Timing   // no time at all
}

data class Beat(
    val timing: Timing,
    val play: suspend (VisualWorld) -> Unit,
)

fun choreograph(event: GameEvent, cat: Catalog): List<Beat> = when (event) {
    is MoveStepped -> listOf(
        Beat(Blocking) { it.walk(event.who, event.to, 180.ms) }
    )
    is AttackRolled -> listOf(
        Beat(Blocking) { it.attackAnim(event.attacker, event.target) },
        Beat(Parallel) { it.showRoll(event) },              // dice readout
    )
    is DamageTaken -> listOf(
        Beat(Parallel) { it.floatNumber(event.who, event.amount, event.type) },
        Beat(Blocking) { it.hurtFlash(event.who, 250.ms) },
        Beat(Parallel) { it.entities[event.who]?.hp?.animateTo(event.remaining.toFloat()) },
    )
    is ResourcesSpent -> listOf(Beat(Instant) { it.hud(event) })
    is Died           -> listOf(Beat(Blocking) { it.deathFade(event.who) })
    is Fizzled        -> listOf(Beat(Parallel) { it.blockedFlash(event) })
    is StatusApplied  -> listOf(Beat(Parallel) { it.statusPop(event.who, event.status) })
    else -> emptyList()
}
```

Adding a new game event means adding a `when` branch here. Neither the engine
nor any composable changes.

`Parallel` beats are what make it feel alive: the damage number is still
floating while the next step begins.

### Correlating rolls with animations

v1 needs a `pendingProjectile` field in `AnimationEventCollector` to hold a
travel animation until a later `DamageEvent` or `MissEvent` reveals whether it
hit — and that hack breaks for multi-target skills, because
`UseSkillOnTargets` emits N `SkillUsedEvent`s that all overwrite the single
pending slot.

In v2 there is nothing to correlate: `AttackRolled` carries `hit`, the roll, the
modifier and the target AC. The director reads one event and knows everything.
Delete the concept.

## The player

```kotlin
class AnimationPlayer(private val world: VisualWorld) {
    private val queue = Channel<Beat>(Channel.UNLIMITED)
    val isPlaying = MutableStateFlow(false)
    var speed: Float = 1f            // 0f → snap instantly

    suspend fun run() = coroutineScope {
        for (beat in queue) {
            isPlaying.value = true
            when (beat.timing) {
                Timing.Instant  -> beat.play(world)
                Timing.Parallel -> launch { beat.play(world) }
                Timing.Blocking -> beat.play(world)
            }
            if (queue.isEmpty) isPlaying.value = false
        }
    }

    fun enqueue(beats: List<Beat>) = beats.forEach { queue.trySend(it) }
    suspend fun awaitDrained() { isPlaying.first { !it } }
}
```

## Reconciliation is the safety net

The most important function in this document. When the queue drains, the visual
world is checked against the logical one:

```kotlin
suspend fun settle(logical: GameState) {
    logical.entities.forEach { e ->
        val v = world.entities.getOrPut(e.id) {
            VisualEntity(e.pos.toOffset(), (e.health?.current ?: 0).toFloat())
        }
        e.pos?.toOffset()?.let { if (v.pos.value != it) v.pos.snapTo(it) }
        e.health?.let { if (v.hp.value != it.current.toFloat()) v.hp.snapTo(it.current.toFloat()) }
        v.alpha.snapTo(1f)
    }
    world.entities.keys.retainAll(logical.byId.keys)
}
```

With this, a forgotten or cancelled beat is a one-frame cosmetic glitch instead
of a sprite that is permanently in the wrong place. Without it you spend days
chasing ghost figures. Call it after every drain, unconditionally.

## Skip and speed from day one

If the player taps during an animation, it should complete instantly, not be
ignored:

```kotlin
fun skipAll() {
    playerJob.cancel()
    scope.launch { settle(logicalState) }
}
```

All durations must go through a single scale factor so that `speed = 0f` turns
every `animateTo` into a `snapTo`. That is both the "fast" setting and the way
UI tests run without waiting. Retrofitting this later is impossible once
durations are hard-coded at call sites.

## Compose rules

**Do not recompose per frame.** Use the lambda form of `graphicsLayer`, which
runs in the draw phase:

```kotlin
Box(
    Modifier.graphicsLayer {
        translationX = visual.pos.value.x * tilePx
        translationY = visual.pos.value.y * tilePx
        alpha = visual.alpha.value
    }
)
```

**The grid is one `Canvas`, not 400 composables.** Tiles, highlights, fog of
war and range overlays are drawn in `drawBehind`. Only entities get their own
layer, because only they need independent animation. On a 44×32 map — which
`Island_Prison.json` already is — a composable per tile is not viable on a
phone.

**The camera follows only when it must.** Animate it when the active entity
leaves a comfortable inner rectangle; continuous centring is nauseating.

## Ordering with the decision dialog

```kotlin
player.enqueue(events.flatMap { choreograph(it, catalog) })
player.awaitDrained()
player.settle(logical)
pendingRequest = res.request      // only now
```

Otherwise the reaction prompt appears while the figure is still visibly mid-move.

## Input during playback

Discard, do not buffer. Queued taps in a tactics game produce moves the player
no longer wants. Gate on `isPlaying`, and offer skip as the escape valve.

## Process death

Animations are not persisted. On restore, load the `Resolver`, then call
`settle()` with no animation. Half-played beats are lost, which is correct: the
snapshot is the truth, the animation was only presentation.
