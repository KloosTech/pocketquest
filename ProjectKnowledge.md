# PocketQuest — Project Knowledge

This document captures the architecture, patterns, and improvement notes for the PocketQuest POC. It is intended to guide the rewrite into a production-ready Android/iOS mobile game.

---

## 1. Project Overview

PocketQuest is a **turn-based RPG** built with **Kotlin Multiplatform + Jetpack Compose Multiplatform**. It features a tile-based battle map, a skill/action system, D&D-style stats, and a condition/resistance system. The current codebase is a POC — functional but rough. The rewrite should preserve the core design intent while fixing architecture issues.

**Target platforms:** Android (primary), iOS, Desktop (secondary / dev tooling)

---

## 2. Tech Stack

| Layer | Library | Version | Notes |
|---|---|---|---|
| Language | Kotlin Multiplatform | 2.1.10 | |
| UI | Compose Multiplatform | 1.7.3 | |
| Map Engine | MapCompose-MP | 0.9.4 | Tile-based map, zoom/pan, markers |
| State | Lifecycle ViewModel + StateFlow | — | KMP lifecycle support |
| Serialization | kotlinx.serialization | 1.6.3 | JSON, polymorphic |
| DateTime | kotlinx-datetime | 0.6.2 | |
| Android AGP | 8.5.2 | minSdk 24, targetSdk 35 | |

**Build system:** Gradle Kotlin DSL with `libs.versions.toml` version catalog.

---

## 3. Directory Structure (POC)

```
composeApp/src/
├── commonMain/kotlin/io/github/jackbeback/
│   ├── App.kt               # Root composable, MaterialTheme entry
│   ├── Platform.kt          # expect/actual platform interface
│   ├── data/                # Core data models
│   ├── ui/                  # Composable UI components
│   ├── units/               # Preset unit factory functions
│   ├── util/                # Extension functions and helpers
│   └── vm/                  # ViewModels (state management)
├── androidMain/             # Android-specific implementations
├── iosMain/                 # iOS-specific implementations
└── desktopMain/             # Desktop (JVM) implementations
```

---

## 4. Architecture: What Was Done

### 4.1 MVVM with Singleton ViewModels

All ViewModels are global singletons accessed via `companion object { val instance = XViewModel() }`. This works for a POC but prevents testing and multi-game-session support.

**ViewModels and their responsibilities:**

| ViewModel | Responsibility |
|---|---|
| `StateViewModel` | Game state machine (turn phases) |
| `UnitViewModel` | Unit list, selection, serialization (save/load) |
| `NavigationViewModel` | Screen routing (`Map`, `Settings`, `Skill`, `Unit`) |
| `MapViewModel` | MapCompose state, tile configuration |
| `SkillViewModel` | Active skill queue, magic missile expansion |
| `LogViewModel` | Battle log entries |
| `OverlayViewModel` | Range indicator overlay on the map |

### 4.2 Game State Machine

Defined in `StateViewModel` as a sealed interface `GameState`:

```
Initializing → PlayerTurnStart → PlayersTurn → PlayersTurnEnd
→ EnemiesTurnStart → EnemiesTurn → EnemiesTurnEnd → EnvironmentTurn → (loop)
```

Progression is linear via `nextState()` cycling through an ordered list.

### 4.3 Data Models

All models are `@Serializable` data classes / sealed interfaces.

```kotlin
sealed interface UnitEntity {
    val name: String; val type: UnitType; val pos: Position
    val stats: Stats; val resources: Resources; val id: String
    val roll: Roll; val conditions: Map<ConditionType, Int>
    val resistances: Map<DamageType, Float>
}
data class Player(...) : UnitEntity
data class Enemy(...) : UnitEntity

data class Position(val x: Double, val y: Double)  // normalized 0.0–1.0

data class Stats(strength, dexterity, constitution, intelligence, wisdom, charisma, armorClass)

data class Resources(health: Resource, mana: Resource, action: Resource, steps: Resource)
data class Resource(current: Int, max: Int, buff: Int)
```

**DamageType:** FIRE, WATER, ELECTRIC, FORCE, ICE, POISON, PIERCING, SLICING, BLUDGEONING

**ConditionType:** Burn, Poison, Cold — stored as `Map<ConditionType, Int>` (stacking counters)

**Resistances:** `Map<DamageType, Float>` — multiplier applied on `takeDamage()`

### 4.4 Skill System

```kotlin
data class Skill(
    name: String, description: String,
    needTarget: Boolean, needHitRoll: Boolean,
    theme: SkillTheme, action: Action,
    skillIcon: @Composable () -> Unit
)

data class Action(
    onSource: (UnitEntity) -> UnitEntity,
    onTarget: (UnitEntity) -> UnitEntity
)
```

Skills are pure functions — `onSource` and `onTarget` transform unit state. This is a solid functional pattern. Keep it.

`TargetedSkill` pairs a `Skill` with start/end `Position` and a projectile composable for animation.

**Current hardcoded skills:** thornWhip, basicHeal, magicMissiles, magicMissile, fireBall, ignite

### 4.5 Map System (MapCompose)

- Tile maps stored in `composeResources/files/tiles/` (KaerMorhen, Throneroom)
- `MapViewModel` wraps `MapState` from MapCompose
- Tiles loaded as `TileStreamProvider` — tile path built from `z/y/x.png`
- Unit markers placed via `MapState.addMarker()` at normalized positions
- Click listener on map triggers movement to tapped position

### 4.6 Serialization / Save-Load

- Polymorphic JSON via `kotlinx.serialization`
- Discriminator field: `"kind"` (SerialName on Player/Enemy)
- Serialization is platform-specific (expect/actual pattern):
  - Android: `File("game_save.json")` via Java File API
  - Desktop: similar Java file API
  - iOS: **stubbed/incomplete**

### 4.7 Navigation

Simple enum-based routing in `NavigationViewModel`:
```kotlin
enum class GameScreen { Map, Unit, Skill, Settings }
```
`GameScreen.Skill` and `GameScreen.Unit` are `TODO()` — never implemented.

### 4.8 UI Components

| Component | File | Lines | Notes |
|---|---|---|---|
| Map battle screen | `Map.kt` | 228 | LaunchedEffects contain business logic |
| Skill panel | `SkillSelection.kt` | 381 | Animated bottom panel, 4 skills |
| Health bar | `HealthBar.kt` | 417 | 3 styles: Gradient, Segments, Hexagonal |
| Battle log | `BattleLog.kt` | 182 | Collapsible, last 5 entries |
| Wizard (player) | `Wizard.kt` | 88 | Player sprite, selection tint, dice roll |
| Grunt (enemy) | `Grunt.kt` | 147 | Enemy sprite, condition/damage animation |
| Condition bar | `ConditionBar.kt` | 165 | Icons with stack counters |
| Dice roll | `DiceRoll.kt` | 147 | Animated dice, success/failure state |
| Action circles | `ActionCircle.kt` | 34 | Green circles for action points |

### 4.9 Utilities

- `util/DiceRoll.kt` — `DiceType` (D4–D20), `roll(type, amount)`, `diceRollCheck(target, type)`
- `util/Numbers.kt` — position normalization, number formatting
- `util/Map.kt` — animation extensions on `MapState` (move marker with coroutine)
- `util/Serializing.kt` — JSON config with polymorphic module setup

---

## 5. What Works Well (Keep in Rewrite)

1. **Functional skill actions** — `Action(onSource, onTarget)` lambdas are clean and composable. Extend this pattern.
2. **Immutable data classes with `copy()`** — correct approach for reactive state.
3. **Sealed interface `UnitEntity`** — polymorphism without inheritance hierarchies. Keep.
4. **StateFlow-based ViewModels** — reactive, Compose-friendly. Keep pattern, fix the singleton issue.
5. **Normalized `Position(x, y)` (0.0–1.0)** — decouples game logic from pixel/tile coordinates.
6. **Condition stacking as `Map<ConditionType, Int>`** — simple and extendable.
7. **`SkillTheme` for per-skill visual theming** — good UX pattern.
8. **`TargetedSkill` + projectile composable** — keeps animation data with skill context.
9. **Version catalog (`libs.versions.toml`)** — good dependency management practice.

---

## 6. Problems to Fix in the Rewrite

### 6.1 Singleton ViewModels — HIGH PRIORITY
Every ViewModel is `companion object { val instance = ... }`. This makes testing impossible and prevents multi-session support.

**Fix:** Use a dependency injection framework (Koin for KMP) or a top-level composition local. ViewModels should be scoped to a navigation graph, not global singletons.

### 6.2 Business Logic in LaunchedEffect — HIGH PRIORITY
`MapContainer` in `Map.kt` contains LaunchedEffect blocks that drive game state transitions, enemy AI turns, condition processing, etc. This logic belongs in ViewModels.

**Fix:** Move all state-changing logic out of composables into ViewModel methods. Composables should only observe state and dispatch events.

### 6.3 No Dependency Injection
No DI framework. ViewModels are instantiated manually. Makes cross-ViewModel dependencies implicit.

**Fix:** Add **Koin** (KMP-compatible). Register ViewModels and repositories as modules. Use `koinViewModel()` in composables.

### 6.4 Platform-Specific Serialization is Incomplete
iOS save/load is stubbed. Serialization lives in `expect/actual` but iOS implementation is missing.

**Fix:** Use `okio` (KMP-compatible) for file I/O, or use platform-appropriate storage (DataStore KMP). Centralize serialization in commonMain.

### 6.5 Hardcoded Skill and Unit Definitions
Skills are hardcoded Kotlin objects in `Skill.kt`. Unit stats are hardcoded in `units/Wizard.kt` and `units/Grunt.kt`.

**Fix:** Define skills and unit templates as JSON/YAML data files in `composeResources`. Load them at startup via a `SkillRepository` / `UnitTemplateRepository`. This enables content-driven design.

### 6.6 Unused Systems (Mana, Steps)
`Resources` has `mana` and `steps` fields that are never used in any game logic.

**Fix:** Either implement them in the rewrite or remove them to reduce cognitive overhead. Don't carry dead fields.

### 6.7 No Error Handling
File I/O has no try-catch. List indexing (`units[0]`) has no bounds check. Missing null safety on optional values.

**Fix:** Wrap file operations in `Result<T>`. Use `firstOrNull()` instead of `[0]`. Add explicit error states to ViewModels.

### 6.8 Debug Code in Production
Multiple `println()` statements in `Skill.kt` and elsewhere. Debug info overlay always visible in `MapContainer`.

**Fix:** Use a proper logging abstraction (e.g., `expect/actual Logger`). Gate debug UI behind a build config flag.

### 6.9 Magic Numbers and Hardcoded Sizes
Tile counts (30, 100), map dimensions (1960×2560, 900×1300), and colors are hardcoded inline.

**Fix:** Define map metadata (tile count, dimensions, name) in a `MapConfig` data class loaded from resources. Extract colors into design tokens / theme.

### 6.10 No Tests
Zero test coverage.

**Fix:** Start with unit tests for pure functions: `DiceRoll`, `takeDamage()`, skill `Action` lambdas, condition effects. These have no platform dependencies and are easy to test. Add ViewModel state machine tests second.

### 6.11 Hit Roll Not Fully Wired
`needHitRoll` flag exists on `Skill` but the actual roll check is not consistently applied before damage.

**Fix:** Centralize combat resolution in a `CombatResolver` or similar class that checks `needHitRoll`, rolls dice vs target AC, then applies `onTarget`. Keep the resolver as a pure function for testability.

### 6.12 Enemy AI is Hardcoded
Enemy turn logic is inline in `MapContainer` LaunchedEffects with basic targeting.

**Fix:** Define an `EnemyAI` interface with at minimum a `decideTurn(enemy: Enemy, gameState: CombatState): List<CombatAction>` function. Start with a simple `BasicMeleeAI` implementation. This makes AI swappable.

---

## 7. Recommended Architecture for Rewrite

```
commonMain/
├── data/
│   ├── model/           # Pure data classes (UnitEntity, Skill, Position, etc.)
│   ├── repository/      # Interfaces: UnitRepository, SkillRepository, SaveRepository
│   └── source/          # In-memory / file-backed implementations
├── domain/
│   ├── combat/          # CombatResolver, DiceRoll, ConditionProcessor
│   ├── ai/              # EnemyAI interface + implementations
│   └── game/            # GameEngine (orchestrates turns, validates actions)
├── ui/
│   ├── screen/          # One file per screen (MapScreen, SettingsScreen, etc.)
│   ├── component/       # Reusable composables (HealthBar, SkillPanel, etc.)
│   └── theme/           # Colors, typography, design tokens
├── vm/                  # ViewModels (scoped, not singletons)
└── di/                  # Koin modules
```

**Key principle:** `domain/` has zero platform or UI dependencies. `data/` depends only on `domain/` interfaces. `vm/` depends on `domain/` and `data/`. `ui/` depends only on `vm/`.

---

## 8. Suggested Dependency Additions for Rewrite

| Library | Purpose |
|---|---|
| Koin KMP | Dependency injection |
| Okio or DataStore KMP | Cross-platform file/preferences I/O |
| Kotlin Coroutines Flow | Already used, keep |
| Kotlin Result | Explicit error handling |
| kotest or kotlin-test | Multiplatform testing |
| Voyager or Decompose | Navigation (replaces manual enum routing) |

---

## 9. Asset Notes

- **Tile maps:** Located in `composeResources/files/tiles/KaerMorhen/` and `Throneroom/`. Format: `z/y/x.png`. Zoom levels: KaerMorhen has 30 tiles, Throneroom has 100.
- **Sprites:** 26 PNG drawables in `composeResources/drawable/`. Named by unit/item type.
- **Map dimensions:** KaerMorhen ~1960×2560, Throneroom ~900×1300 (in tile pixels).

In the rewrite, store map metadata (tile count, dimensions, zoom levels, spawn points) as a JSON `MapConfig` alongside the tile assets.

---

## 10. POC Codebase Stats

| Category | Files | Lines |
|---|---|---|
| Data models | 4 | ~380 |
| ViewModels | 7 | ~430 |
| UI components | 11 | ~1,500 |
| Utilities | 4 | ~310 |
| Units (presets) | 2 | ~60 |
| Platform-specific | 7 | ~80 |
| **Total commonMain** | **~35** | **~3,200** |

---

## 11. Quick Reference: Key File Paths (POC)

| What | Path |
|---|---|
| Root composable | `composeApp/src/commonMain/.../App.kt` |
| Unit data models | `.../data/UnitEntity.kt` |
| Skill definitions | `.../data/Skill.kt` |
| Game state machine | `.../vm/StateViewModel.kt` |
| Unit management | `.../vm/UnitViewModel.kt` |
| Map + tile config | `.../vm/MapViewModel.kt` |
| Skill queue | `.../vm/SkillViewModel.kt` |
| Main battle UI | `.../ui/Map.kt` |
| Skill panel UI | `.../ui/SkillSelection.kt` |
| Health bar | `.../ui/HealthBar.kt` |
| Dice utilities | `.../util/DiceRoll.kt` |
| Serialization | `.../util/Serializing.kt` |
| Map animations | `.../util/Map.kt` |
| Build config | `composeApp/build.gradle.kts` |
| Version catalog | `gradle/libs.versions.toml` |
