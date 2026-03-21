# Test Coverage Analysis

## Overview

| Metric | Value |
|--------|-------|
| Total Source Files | ~99 |
| Total Test Files | 9 |
| Source LOC | ~14,616 |
| Test LOC | ~3,438 |
| Test-to-Code Ratio | ~23.5% |
| Tested Modules | 4 (ECS core + 4 game systems) |
| Untested Modules | ~25+ |

---

## Well-Tested Areas

The following modules have comprehensive tests:

| File | Coverage | Notes |
|------|----------|-------|
| `CombatSystem` | ✓ | Damage pipeline, resist/block/HP, heal, edge cases |
| `ConditionApplySystem` | ✓ | Stack accumulation, multiple conditions |
| `ConditionTickSystem` | ✓ | DoT mechanics, buff exclusion, logging |
| `DeathSystem` | ✓ | Death detection, events, pending-destroy |
| `World` | ✓ | Entity lifecycle, component CRUD, queries |
| `EventBus` | ✓ | Emit/flush cycle, ordering, type safety |
| `RunStateHolder` | ✓ | Run lifecycle, XP/leveling, persistence |
| `Dice` | ✓ | Roll range, modifiers, edge cases |

---

## Critical Gaps — Proposed Improvements

### Tier 1 — CRITICAL

#### 1. `AStarPathfinder` + `LineOfSight` (~150 LOC, no tests)

These algorithms are at the heart of enemy AI. A bug here breaks all AI navigation and attack targeting without any automated signal.

**Proposed tests:**
- `findPath()` returns correct shortest path on open grid
- `findPath()` routes around walls correctly
- `findPath()` returns empty when no path exists
- `hasLineOfSight()` is unobstructed between two open tiles
- `hasLineOfSight()` is blocked by walls
- Diagonal corner blocking (Bresenham edge case)
- `computeVisibleTiles()` returns all tiles within range on open map
- `computeVisibleTiles()` excludes tiles behind walls

#### 2. `AIDecisionSystem` (~187 LOC, no tests)

Orchestrates all enemy behaviour — move target selection, path walking, attack decisions.

**Proposed tests:**
- Enemy moves toward player when in range
- Enemy stops when movement points are exhausted
- Enemy attacks when adjacent (melee)
- Enemy attacks at range when in LOS
- Enemy does not act when already acted this turn
- Path walking consumes correct movement points per tile

#### 3. `MovementSystem` (~48 LOC, no tests)

Validates and applies all movement in the game.

**Proposed tests:**
- Valid `MoveEvent` updates entity position
- Movement is rejected when beyond Chebyshev distance
- Movement point cost is deducted correctly
- Moving onto a wall tile is rejected
- Moving a destroyed/nonexistent entity is a no-op

#### 4. `SkillResolverSystem` (~100+ LOC, no tests)

The core skill resolution pipeline: mana checks, hit rolls, damage multipliers.

**Proposed tests:**
- Skill fires correctly when entity has sufficient mana
- Skill is rejected when mana is insufficient
- Hit roll below target AC results in a miss
- Hit roll above target AC deals damage
- `StrengthUp` stacks multiply damage correctly
- One-action-per-turn restriction prevents double-firing (enemies)
- Attribute modifier calculation (DEX, STR bonuses)

---

### Tier 2 — HIGH

#### 5. `TurnResetSystem` (~32 LOC, no tests)

Resets per-turn state. Silent failures here cause compounding bugs.

**Proposed tests:**
- Action flag is cleared at turn reset
- Movement points are restored to max
- Mana regeneration is applied (and capped)

#### 6. `HazardSystem` (~34 LOC, no tests)

Applies environmental damage to entities on hazard tiles.

**Proposed tests:**
- Entity on a hazard tile takes correct damage type and amount
- Entity not on a hazard tile is unaffected
- Damage is logged correctly

#### 7. `GameLoop` (~50–100 LOC, no tests)

Orchestrates the full turn sequence. Integration-level tests here catch system interaction bugs.

**Proposed tests:**
- Player action triggers the correct sequence of system updates
- Enemy turns run after the player's turn ends
- Environment phase applies hazards before next player turn
- Dead entities are flushed at the end of each phase

#### 8. `BattleViewModel` (large, no tests)

Bridges UI input to ECS state. Bugs here result in broken player interaction.

**Proposed tests (unit, mocking game loop):**
- `processPlayerAction()` calls the correct game loop method
- After a battle ends, `handleBattleResult()` emits the correct navigation event
- Tile cache is invalidated when the map changes
- Hint manager is queried on each new turn

---

### Tier 3 — MEDIUM

#### 9. Content Registries (`SkillRegistry`, `OverworldEventRegistry`)

**Proposed tests:**
- Every registered skill can be looked up by its ID without throwing
- Every registered overworld event has valid preconditions
- DSL builder produces correct component data for a sample skill/unit

#### 10. Persistence Layer (`PersistenceRepository`)

**Proposed tests (using in-memory Room or fakes):**
- Save followed by load returns the same state
- Missing key returns the defined default value
- Seen hints are persisted across repository instances

---

### Tier 4 — LOW

- Designer model round-trip serialization
- `HintManager` marks hints as seen correctly
- `AnimationEventCollector` captures events in order
- Compose UI component tests (requires Compose test harness)

---

## Test Quality Observations

**Strengths of existing tests:**
- Descriptive backtick test names
- Fresh `World` instance per test (no shared state leaks)
- Edge cases covered (null components, destroyed entities)
- Dice tests use 100 repeated rolls to surface randomness bugs

**Weaknesses to address alongside the new tests:**
- No integration tests connecting multiple systems end-to-end
- No tests for the UI → ECS data flow
- No database/persistence layer tests
- Test framework (`kotlin.test` only) — consider adding a mocking library (e.g. MockK) to enable ViewModel and GameLoop testing without wiring up full ECS state

---

## Suggested Implementation Order

1. `AStarPathfinder` + `LineOfSight` tests — pure algorithms, easy to set up
2. `MovementSystem` tests — small surface area, high value
3. `TurnResetSystem` + `HazardSystem` tests — small, fast wins
4. `SkillResolverSystem` tests — larger but well-defined contracts
5. `AIDecisionSystem` tests — requires pathfinding + movement tests first
6. `GameLoop` integration tests — build on top of all system tests
7. `BattleViewModel` tests — requires MockK or equivalent
8. Content registry + persistence tests
