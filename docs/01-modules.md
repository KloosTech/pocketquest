# 01 — Modules and dependency rules

## Why split at all

Today everything lives in `:composeApp`. That means nothing stops a rules class
from importing `androidx.compose.ui.geometry.Offset` — and v1 already does the
equivalent: `AnimationEventCollector` computes normalised canvas coordinates
inside what is nominally game logic, and holds mutable `gridCols`/`gridRows`
that the ViewModel pokes at.

A module boundary turns "we agreed not to do that" into a compile error. That
is the entire justification. It also makes the rules testable as a plain JVM
test run measured in milliseconds, instead of anything touching the Android or
iOS toolchain.

## Target graph

```
:app                (android / ios / desktop entry points, DI wiring)
  └── :ui           Compose, ViewModels, Director, AnimationPlayer
        ├── :data   Room, repositories, snapshot serialization
        │     └── :core:content
        ├── :core:ai
        └── :core:content   catalog loading, JSON schemas
              └── :core:rules   resolver, handlers, actions, stats
                    └── :core:model   GameState, Entity, Effect, GameEvent
```

Rules, read top to bottom:

1. `:core:model` depends on **nothing** except `kotlin-stdlib` and
   `kotlinx-serialization-core`.
2. `:core:rules` depends on `:core:model` only. No coroutines. No `Random` —
   RNG comes from state (see [02](02-state-model.md)).
3. `:core:content` may depend on `kotlinx-serialization-json` and Compose
   Resources for reading `composeResources/files/`. It must not depend on
   `:core:rules` internals beyond the public types.
4. `:core:ai` depends on `:core:rules`. It is a *consumer* of the resolver, not
   a special case inside it.
5. `:data` owns Room and is the only module that may perform IO.
6. `:ui` may depend on everything below it, and is the only module allowed to
   import Compose.
7. **No module may depend on `:ui`.** If something in `:core` needs a UI
   concept, the concept is in the wrong place.

## Multiplatform shape

The project targets Android, iOS (`iosArm64`, `iosSimulatorArm64`) and Desktop
JVM. Therefore:

- `:core:model` and `:core:rules` are **Kotlin Multiplatform library** modules
  with only a `commonMain` source set. Not `jvm()`-only — iOS needs them.
- They apply `kotlinMultiplatform` + `kotlinSerialization`, and explicitly
  **not** `composeMultiplatform`, `androidApplication`, or `room`.
- Their tests live in `commonTest` and run on the JVM target for speed
  (`./gradlew :core:rules:desktopTest`) while remaining compilable for iOS.

`:data` needs `androidLibrary` + `room` + `ksp` and keeps the existing
`sqlite-bundled` setup for iOS and Desktop.

## Migration path (non-breaking, in this order)

The v1 code keeps running the whole time. We are not doing a big-bang move.

1. **Create empty modules** `:core:model`, `:core:rules` and wire them into
   `settings.gradle.kts`. Nothing depends on them yet.
2. **Build the new core inside them**, with tests, entirely disconnected from
   the running game. This is the step described in
   [09-test-plan.md](09-test-plan.md).
3. **Port content** — `content/definitions/*.kt` and the JSON under
   `composeResources/files/` map onto the new `Archetype` / `ActionDef`
   catalog. The designer tooling in `desktopMain` keeps writing the same JSON;
   only the loader changes.
4. **Add a second battle screen** behind a debug flag that runs on the new
   engine. Both engines coexist.
5. **Delete `ecs/` and `game/systems/`** once the new screen reaches parity.

Step 4 is what keeps this from becoming a six-week rewrite with nothing
playable in the middle.

## Package naming

Inside the new modules, drop the `ecs` prefix — the new design is not an ECS,
and keeping the name will mislead future readers.

```
de.jackbeback.pocketquest.core.model      GameState, Entity, GridPos, ids
de.jackbeback.pocketquest.core.model.event
de.jackbeback.pocketquest.core.rules      Resolver, StepResult, handlers
de.jackbeback.pocketquest.core.rules.stat Stats derivation
de.jackbeback.pocketquest.core.rules.action
de.jackbeback.pocketquest.core.content    Catalog, loaders
```

## What "no Compose in core" costs us

One real thing: `GridPos` cannot be `IntOffset`, and interpolated positions
cannot be `Offset`. The core uses its own `GridPos(col, row)`; the UI layer
converts. This is a few lines of adapter code and is worth it — the v1
`AnimationEvent` carrying `fromNormX: Float` is exactly the coupling we are
paying to remove.
