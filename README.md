# PocketQuest

A turn-based tactical RPG built with **Kotlin Multiplatform** and **Compose Multiplatform**.
Fight through procedurally-populated dungeons, collect relics, level up, and survive as long as you can.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Building & Running](#building--running)
   - [Android](#android)
   - [iOS](#ios)
   - [Desktop](#desktop)
6. [Running Tests](#running-tests)
   - [What Is Tested](#what-is-tested)
   - [Test Suites in Detail](#test-suites-in-detail)
7. [Gameplay Systems](#gameplay-systems)
8. [Content Authoring](#content-authoring)
9. [Contributing](#contributing)
10. [Release Checklist](#release-checklist)

---

## Overview

PocketQuest is a **roguelike dungeon crawler** where every run is unique:

- Navigate an overworld map packed with battle encounters and rest sites
- Fight turn-based battles on a 14 × 8 tile grid
- Cast spells, apply conditions, absorb damage with Block stacks
- Collect relics after victories to augment your wizard's power
- Die → lose your run progress, keep unlocked characters

The codebase targets **Android** (primary), **iOS**, and **Desktop** (JVM) from a single Kotlin source set.

---

## Architecture

PocketQuest is built on two architectural pillars:

### Entity Component System (ECS)

All gameplay state lives in an in-memory **World** — a lightweight ECS database.

```
World
 ├── createEntity() → EntityId
 ├── set / get / has / remove  (typed component accessors via inline reified extensions)
 ├── query<A>()               (returns alive entities that own component A)
 ├── query<A, B>()            (alive entities with both A and B)
 ├── destroyEntity(id)        (marks pending; actually removed on flushDestroys)
 └── events() → EventBus      (two-phase emit → flush dispatch)
```

**Systems** are classes that implement `GameSystem`. Each system registers event-bus handlers during `init` and/or overrides `update(world, deltaMs)`. The `GameLoop` drives systems in the correct phase order:

```
PlayerPhase  → SkillResolverSystem, MovementSystem, ConditionApplySystem, CombatSystem, DeathSystem
EnemyPhase   → AIDecisionSystem, SkillResolverSystem, MovementSystem, ConditionApplySystem, CombatSystem, DeathSystem
EnvironmentPhase → ConditionTickSystem, CombatSystem, DeathSystem, TurnResetSystem
```

### MVVM + Koin DI

Compose UI never reads the `World` directly. After each phase the ViewModel calls `snapshotBattle()` / `snapshotOverworld()` to produce an **immutable data class** that Compose observes via `StateFlow`.

```
World  ──snapshot──►  BattleSnapshot  ──StateFlow──►  BattleScreen (Compose)
```

Dependency injection is handled by **Koin 4**. Singletons (`World`, `RunStateHolder`, `Navigator`, …) are declared in `AppModule`. ViewModels are created via Koin factories.

---

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin Multiplatform | 2.3.0 |
| UI | Compose Multiplatform | 1.10.0 |
| Dependency Injection | Koin | 4.0.0 |
| Async / Coroutines | kotlinx-coroutines | 1.10.2 |
| Serialization | kotlinx-serialization | 1.8.1 |
| Tile Maps | MapCompose-MP | 0.9.4 |
| ViewModel / StateFlow | androidx-lifecycle | 2.9.6 |
| Testing | kotlin-test + JUnit | bundled |
| Build | Gradle Kotlin DSL + AGP | 8.11.2 |

**Target SDKs:**
- Android: minSdk 24, targetSdk 36, compileSdk 36
- iOS: arm64 + simulatorArm64
- Desktop: JVM 11

---

## Project Structure

```
pocketquest/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/de/jackbeback/pocketquest/
│       │   ├── App.kt                     # Root composable, screen routing
│       │   ├── content/
│       │   │   ├── definitions/           # Skills, Units, Relics — game content tables
│       │   │   ├── dsl/                   # Dice, SkillEffect, SkillTemplate builders
│       │   │   ├── events/                # Overworld event definitions
│       │   │   ├── map/                   # Map metadata (tile paths, display names)
│       │   │   └── registry/              # SkillRegistry — central skill lookup
│       │   ├── di/                        # Koin AppModule + PocketQuestApp init
│       │   ├── ecs/
│       │   │   ├── core/                  # World, EntityId, EventBus, ComponentStore, extensions
│       │   │   └── components/
│       │   │       ├── core/              # Health, Mana, Stats, Position, Faction, …
│       │   │       └── combat/            # DamageType, ConditionType, DamageResistances, …
│       │   ├── game/
│       │   │   ├── loop/                  # GameLoop, TurnPhase, PlayerAction
│       │   │   ├── systems/
│       │   │   │   ├── combat/            # CombatSystem, SkillResolverSystem, DeathSystem, …
│       │   │   │   ├── movement/          # MovementSystem
│       │   │   │   └── ai/               # AIDecisionSystem
│       │   │   ├── battle/               # BattleGrid, BattleSystemFactory, BattleTileCache
│       │   │   ├── snapshot/             # BattleSnapshot, OverworldSnapshot
│       │   │   ├── animation/            # AnimationEvent, AnimationEventCollector
│       │   │   ├── run/                  # RunStateHolder — roguelike progression
│       │   │   └── overworld/            # OverworldEventRegistry
│       │   ├── ui/
│       │   │   ├── battle/               # BattleScreen, BattleViewModel, BattleUiState
│       │   │   ├── overworld/            # OverworldScreen, OverworldViewModel
│       │   │   ├── characterselect/      # CharacterSelectScreen
│       │   │   ├── component/            # SkillPanel, ConditionBadge
│       │   │   └── navigation/           # Navigator
│       │   └── designer/                 # Encounter designer model + converters
│       ├── androidMain/                  # Android-specific Platform impl
│       ├── iosMain/                      # iOS-specific Platform impl
│       ├── desktopMain/                  # Desktop app entry + Encounter Designer UI
│       └── commonTest/                   # All shared unit tests  ◄──────────────────
├── iosApp/                               # Xcode project (Swift entry point)
├── gradle/libs.versions.toml             # Dependency version catalog
└── build.gradle.kts                      # Root build configuration
```

---

## Building & Running

### Prerequisites

- JDK 11 or newer (17 recommended)
- Android SDK with platform 36 installed
- For iOS: macOS + Xcode 15+
- For Desktop: no extras needed

### Android

**Build a debug APK:**
```shell
# macOS / Linux
./gradlew :composeApp:assembleDebug

# Windows
.\gradlew.bat :composeApp:assembleDebug
```

**Install directly on a connected device or emulator:**
```shell
./gradlew :composeApp:installDebug
```

The APK is written to `composeApp/build/outputs/apk/debug/`.

### iOS

Open the Xcode project and run from the IDE:
```shell
open iosApp/iosApp.xcodeproj
```

Or build the Kotlin binary for the simulator from the terminal:
```shell
./gradlew :composeApp:iosSimulatorArm64MainBinaries
```

> **Note:** A full iOS build requires macOS with Xcode installed.

### Desktop

**Run the desktop app directly:**
```shell
./gradlew :composeApp:run
```

The desktop target also includes the **Encounter Designer** — a tool for building and exporting battle encounters to JSON:
```shell
# The same run task starts the designer on desktop
./gradlew :composeApp:run
```

**Package a native distribution:**
```shell
./gradlew :composeApp:packageDistributionForCurrentOS
```

---

## Running Tests

All unit tests live in `composeApp/src/commonTest/` and run on the JVM (no device needed).

### Run all tests

```shell
./gradlew :composeApp:desktopTest
```

This compiles and runs the entire `commonTest` source set against the desktop JVM target — the fastest way to get results.

**Watch mode (re-runs on file changes):**
```shell
./gradlew :composeApp:desktopTest --continuous
```

**Run a single test class:**
```shell
./gradlew :composeApp:desktopTest --tests "de.jackbeback.pocketquest.CombatSystemTest"
```

**Run a single test method:**
```shell
./gradlew :composeApp:desktopTest --tests "de.jackbeback.pocketquest.CombatSystemTest.50 percent fire resistance halves fire damage"
```

**HTML report** is written to:
```
composeApp/build/reports/tests/desktopTest/index.html
```

---

### What Is Tested

| Test file | System under test | # tests |
|---|---|---|
| `ComposeAppCommonTest` | Test infrastructure smoke test | 2 |
| `DiceTest` | `Dice` — randomness primitive | 12 |
| `EventBusTest` | `EventBus` — two-phase dispatch | 9 |
| `WorldTest` | `World` + ECS extensions | 17 |
| `CombatSystemTest` | `CombatSystem` — full damage / heal pipeline | 17 |
| `ConditionTickSystemTest` | `ConditionTickSystem` — DoT tick logic | 14 |
| `ConditionApplySystemTest` | `ConditionApplySystem` — stack application | 9 |
| `DeathSystemTest` | `DeathSystem` — death detection & events | 12 |
| `RunStateHolderTest` | `RunStateHolder` — roguelike progression | 22 |

---

### Test Suites in Detail

#### `DiceTest` — Randomness Primitive

`Dice(count, sides, bonus)` represents a dice expression such as `1d6+2`. The `roll()` function is used by every skill effect.

**What is verified:**
- `roll()` always returns a value within `[count + bonus, count * sides + bonus]` — tested with 100 repetitions per case to catch statistical outliers
- Named skill ranges are correct: `1d4+1` (magic missile), `1d4+2` (basic heal), `1d6+2` (fireball)
- Degenerate case `1d1` always returns exactly 1
- `Dice` is a proper data class with value equality

#### `EventBusTest` — Two-Phase Event Dispatch

The `EventBus` separates _emit_ (queue) from _flush_ (dispatch). This prevents infinite loops — a system's events during `update` are not processed until after all systems in that phase have run.

**What is verified:**
- A handler does **not** fire between `emit` and `flush`
- A handler fires exactly once after `flush`
- The queue is cleared after flush — a second flush is a no-op
- Multiple handlers all receive the same event
- Handlers are type-safe: `on<TestEvent>` ignores `OtherEvent` instances
- Emitting with no handlers registered does not throw
- Handler registered after `emit` but before `flush` still receives the event

#### `WorldTest` — ECS Database & Extensions

`World` is the in-memory database for all entities and components during gameplay.

**What is verified:**
- `createEntity()` produces unique IDs every time
- `isAlive()` is `true` for new entities and `false` after `destroyEntity()`
- `flushDestroys()` removes entities from `allEntities()` and strips their components
- Surviving entities are unaffected when siblings are destroyed
- `set` / `get` / `has` / `remove` on typed component stores work correctly
- `set` overwrites an existing component
- `query<A>()` returns alive entities with component A and excludes pending-destroy entities
- `query<A, B>()` returns only entities that have **both** components

#### `CombatSystemTest` — Damage & Heal Pipeline

`CombatSystem` listens for `DamageEvent` and `HealEvent` on the bus and applies them through a three-stage pipeline.

**The pipeline under test:**
```
Raw damage
  × resistance multiplier   (0.0 = immune, 0.5 = half, 2.0 = vulnerable)
  − block stacks            (1 stack consumed per hit; reduces damage by stack count)
  → applied to HP           (clamped to [0, max])
```

**What is verified:**
- Plain damage reduces HP by the full amount
- Damage is clamped at 0 HP (cannot go negative)
- 50% fire resistance halves fire damage
- Full immunity (0.0 multiplier) deals 0 damage
- Vulnerability (2.0 multiplier) doubles damage
- Resistance applies per damage type — unresisted types take full damage
- Block absorbs damage equal to stack count and decrements stacks by 1
- Block is removed from conditions when stacks reach 0
- Block fully absorbs a hit when damage < stack count (HP unchanged)
- The full pipeline applies resistance **before** block reduction
- Heal restores HP without exceeding the max
- Damage and heal on a destroyed entity (or entity with no `HealthComponent`) are silently ignored

#### `ConditionTickSystemTest` — Damage-Over-Time

`ConditionTickSystem` runs during `EnvironmentPhase`. It iterates all entities with `ConditionsComponent`, emits damage events for DoT conditions, and decrements their stacks.

**Damage formulas tested:**

| Condition | Damage per tick | Damage type |
|---|---|---|
| Burn (N stacks) | N × 2 | FIRE |
| Poison (N stacks) | N × 1 | POISON |
| Cold (N stacks) | N × 1 | FORCE |
| Block | — (not ticked) | — |
| StrengthUp | — (not ticked) | — |

**What is verified:**
- Burn 1 stack → 2 FIRE damage; Burn 3 stacks → 6 FIRE damage
- Burn stacks decrement by 1 per tick and are removed at 0
- Multi-tick burn sequence (2 ticks for 2 stacks) produces correct cumulative damage
- Poison and Cold deal N×1 damage and decrement stacks correctly
- Block and StrengthUp stacks are completely unchanged after a tick
- Multiple DoT conditions on the same entity tick independently in one pass
- The `onLog` callback is invoked once per ticking condition

#### `ConditionApplySystemTest` — Stack Application

`ConditionApplySystem` handles `ConditionAppliedEvent` — the event emitted by `SkillResolverSystem` when a skill applies a condition.

**What is verified:**
- Applying a condition to an entity without a `ConditionsComponent` creates the component
- Block, Poison, Burn, and other conditions are added correctly
- Applying the same condition twice **accumulates** (stacks are additive, not replaced)
- Different conditions coexist on the same entity without overwriting each other
- Applying a condition to an entity that already has a `ConditionsComponent` merges correctly
- Applying a condition to a destroyed entity is silently ignored

#### `DeathSystemTest` — Death Detection

`DeathSystem` scans all entities with `HealthComponent ≤ 0`, emits `DeathEvent`, and calls `World.destroyEntity()`. Actual removal happens on `flushDestroys()`.

**What is verified:**
- Entity at exactly 0 HP is marked for destroy (`isAlive` → `false`)
- Entity at 1 HP or higher is NOT marked for destroy
- Multiple dead entities in one `update()` call are all scheduled for removal
- A living entity is unaffected when a sibling is destroyed
- A `DeathEvent` is emitted for each dying entity and can be consumed by handlers
- No `DeathEvent` is emitted when all entities are alive
- `onLog` is called with the entity's display name (or a fallback if `NameComponent` is absent)
- After `flushDestroys()` the entity is absent from `allEntities()`

#### `RunStateHolderTest` — Roguelike Progression

`RunStateHolder` is the single source of truth for a player's run. It manages XP, levelling, difficulty scaling, relic collection, and inter-encounter HP/mana persistence.

**Levelling formula tested:** threshold for level N = N × 100 XP

**What is verified:**
- `run` is `null` before `startRun` and contains correct defaults after
- `resetRun()` clears run state back to `null`
- `gainExp()` accumulates XP without levelling up when below threshold
- `gainExp()` triggers a level-up at exactly the threshold
- Surplus XP above threshold carries over to the new level's counter
- Level 2 threshold is 200 XP; partial XP does not trigger second level-up
- XP accumulates correctly across multiple `gainExp()` calls
- `gainExp()` with no active run returns a safe no-op result
- `previewGainExp()` correctly predicts level-up / no-level-up without modifying state
- `incrementDifficulty()` accumulates correctly across multiple calls
- `savePlayerState()` persists HP and mana and can be overwritten
- `addRelic()` appends relic IDs to the list in order
- `startNextArea()` increments area number
- Persistent state (unlocked characters) survives a run reset

---

## Gameplay Systems

### Combat Flow

Each battle consists of repeating phases managed by `GameLoop`:

```
PlayerPhase  →  player picks Move / UseSkill / EndTurn
EnemyPhase   →  AIDecisionSystem auto-plays all enemies
EnvironmentPhase  →  DoT ticks, clean-up, turn reset
                        ↑_________________________________|
```

### Skills

Skills are defined with a Kotlin DSL:

```kotlin
skill("fireball") {
    name        = "Fireball"
    manaCost    = 3
    range       = 5
    needsTarget = true
    maxTargets  = 1
    damage(Dice(1, 6, 2), DamageType.FIRE)
    applyCondition(ConditionType.Burn, stacks = 2)
}
```

Current skill roster: `magic_missile`, `basic_heal`, `thorn_whip`, `fireball`, `basic_attack`, `block`.

### Conditions

| Condition | Type | Effect |
|---|---|---|
| Burn | DoT | N×2 FIRE damage per environment tick |
| Poison | DoT | N×1 POISON damage per environment tick |
| Cold | DoT | N×1 FORCE damage per environment tick |
| Block | Buff | Absorbs N damage per hit; loses 1 stack |
| StrengthUp | Buff | +2 damage per stack on skill use |

### Damage Types

`FIRE` `WATER` `ELECTRIC` `FORCE` `ICE` `POISON` `PIERCING` `SLICING` `BLUDGEONING`

Resistances are stored as float multipliers in `DamageResistancesComponent` (e.g. `0.5f` = 50% resistance).

### Roguelike Progression

- **XP & Levelling:** 50 XP per defeated enemy. Level N requires N×100 XP.
- **Relics:** After each won encounter the player picks 1-of-3 random relics:
  - HP bonuses: Ancient Tome (+20), Iron Will (+40), War Trophy (+30)
  - Mana bonuses: Mage Stone (+2), Well of Power (+4)
  - Multipliers: Scholar's Ring (1.5× XP)
  - Utility: Blood Pact (full HP on battle start), Arcane Focus (full mana on battle start)
- **Difficulty:** `difficultyCounter` increments each won encounter; enemy stats scale with it
- **Persistence:** Player HP and mana carry over between encounters within a run

---

## Content Authoring

All game content is code-first — no external data files needed for the base game.

| Content | File | DSL / Data Class |
|---|---|---|
| Skills | `content/definitions/Skills.kt` | `skill { }` builder |
| Units | `content/definitions/Units.kt` | `unitTemplate { }` builder |
| Relics | `content/definitions/Relics.kt` | `RelicDefinition` data class |
| Events | `content/events/OverworldEvents.kt` | `BattleEncounter`, `RestSite` |
| Maps | `content/map/MapConfig.kt` | `MapConfig` data class |

The **Desktop Encounter Designer** provides a GUI for authoring encounters and exporting them to JSON for rapid iteration without recompiling:

```shell
./gradlew :composeApp:run   # runs the designer on desktop
```

---

## Contributing

1. Fork the repository and create a branch from `main`
2. Write tests for any new system logic in `commonTest/`
3. Run the full test suite before opening a PR:
   ```shell
   ./gradlew :composeApp:desktopTest
   ```
4. Keep PR scope focused — one feature or fix per PR
5. Follow existing naming conventions (ECS systems suffix `System`, events suffix `Event`, etc.)

### Code Style

- Kotlin official code style (enforced via `kotlin.code.style=official` in `gradle.properties`)
- All new game logic must live in `commonMain` — platform-specific code only for platform APIs
- Prefer immutable data classes and `copy()` mutations over mutable state
- Systems must not read from the World during event dispatch (emit events during `update`, read state in handlers registered at init time)

---

## Release Checklist

Before tagging a release:

- [ ] `./gradlew :composeApp:desktopTest` passes with zero failures
- [ ] `./gradlew :composeApp:assembleRelease` completes without errors
- [ ] Manual smoke test: start a run, complete one battle, collect a relic, win/lose a run
- [ ] Version code and version name updated in `composeApp/build.gradle.kts`
- [ ] `CHANGELOG.md` entry written (if maintained)
- [ ] iOS build verified in Xcode simulator (if targeting iOS)

---

*Built with [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html) and [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/).*
