package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.checkInvariants
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import de.jackbeback.pocketquest.core.rules.fixture.walls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EffectHandlerTest {

    // --- DealDamage ---

    @Test
    fun dealDamageReducesHpAndEmitsDamageTaken() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val effect = Effect.DealDamage(s.id("target"), amount = 7, damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog)

        assertEquals(13, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(listOf(GameEvent.DamageTaken(s.id("target"), 7, DamageType.Fire)), out.events)
    }

    @Test
    fun dealDamageClampsAtZeroAndEmitsDied() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(5) }
        }
        val effect = Effect.DealDamage(s.id("target"), amount = 999, damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog)

        assertEquals(0, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(GameEvent.DamageTaken(s.id("target"), 999, DamageType.Fire), out.events[0])
        assertEquals(GameEvent.Died(s.id("target")), out.events[1])
        assertEquals(GameEvent.Downed(s.id("target")), out.events[2], "docs/17-engine-gaps.md 1.5: 0 HP also fires Downed, alongside Died not instead of it")
    }

    @Test
    fun dealDamageDoesNotEmitDownedWhenTheHitDoesNotReduceHpToZero() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("target"), amount = 7, damageType = DamageType.Fire), emptyMap(), s.catalog)
        assertTrue(out.events.none { it is GameEvent.Downed })
    }

    @Test
    fun dealDamageRespectsResistanceAndImmunity() {
        val s = scenario {
            archetype("dummy") { hp = 20; modifier(Modifier.Resist(DamageType.Fire, Resistance.Resistant)) }
            archetype("immuneDummy") { hp = 20; modifier(Modifier.Resist(DamageType.Fire, Resistance.Immune)) }
            entity("resistant") { archetype("dummy"); at(0, 0); hp(20) }
            entity("immune") { archetype("immuneDummy"); at(1, 0); hp(20) }
        }

        val resistantOut = applyEffect(s.state, Effect.DealDamage(s.id("resistant"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(15, resistantOut.state.byId.getValue(s.id("resistant")).health!!.current) // half of 10, floored

        val immuneOut = applyEffect(s.state, Effect.DealDamage(s.id("immune"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(20, immuneOut.state.byId.getValue(s.id("immune")).health!!.current)
        assertEquals(0, (immuneOut.events[0] as GameEvent.DamageTaken).amount)
    }

    @Test
    fun dealDamageOnMissingTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("alive") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val out = applyEffect(s.state, Effect.DealDamage(EntityId(999), 5, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertIs<Rejection.TargetMissing>(fizzled.reason)
    }

    @Test
    fun dealDamageOnIndestructibleTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("wall") { archetype("dummy"); at(0, 0) } // no hp() -> health == null
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("wall"), 5, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertIs<Rejection.TargetMissing>(fizzled.reason)
    }

    @Test
    fun dealDamageOnAlreadyDeadTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("corpse") { archetype("dummy"); at(0, 0); hp(0) }
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("corpse"), 5, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(s.state, out.state, "damage must re-validate the target is still alive, not just present")
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertIs<Rejection.TargetMissing>(fizzled.reason)
    }

    @Test
    fun dealDamageDoublesOnVulnerable() {
        val s = scenario {
            archetype("dummy") { hp = 20; modifier(Modifier.Resist(DamageType.Fire, Resistance.Vulnerable)) }
            entity("target") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("target"), 6, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(8, out.state.byId.getValue(s.id("target")).health!!.current) // 20 - 12
        assertEquals(12, (out.events.single() as GameEvent.DamageTaken).amount)
    }

    // --- MoveAlong ---

    @Test
    fun moveAlongMovesOneTileAndRePushesWithNextIndex() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val path = listOf(GridPos(1, 0), GridPos(2, 0), GridPos(3, 0))
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("hero"), path, index = 0), emptyMap(), s.catalog)

        assertEquals(GridPos(1, 0), out.state.byId.getValue(s.id("hero")).pos)
        assertEquals(listOf(GameEvent.MoveStepped(s.id("hero"), GridPos(0, 0), GridPos(1, 0))), out.events)
        assertEquals(listOf(Effect.MoveAlong(s.id("hero"), path, index = 1)), out.spawn)
    }

    @Test
    fun moveAlongStopsWithNoContinuationOnFinalStep() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val path = listOf(GridPos(1, 0))
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("hero"), path, index = 0), emptyMap(), s.catalog)
        assertTrue(out.spawn.isEmpty())
    }

    @Test
    fun moveAlongOntoOccupiedTileFizzlesWithoutContinuation() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
            entity("blocker") { archetype("dummy"); at(1, 0) }
        }
        val path = listOf(GridPos(1, 0), GridPos(2, 0))
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("hero"), path, index = 0), emptyMap(), s.catalog)

        assertEquals(GridPos(0, 0), out.state.byId.getValue(s.id("hero")).pos, "blocked move must not change position")
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(Rejection.Blocked(GridPos(1, 0)), fizzled.reason)
        assertTrue(out.spawn.isEmpty())
    }

    @Test
    fun moveAlongOntoOccupiedTileSpawnsOnWallHitWhenPresent() {
        // docs/29-push-on-wall-hit.md: still fizzles (unchanged), but now ALSO spawns onWallHit —
        // empty by default (every test above this one proves that), populated here.
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
            entity("blocker") { archetype("dummy"); at(1, 0) }
        }
        val onWallHit = listOf(Effect.DealDamage(s.id("hero"), 3, DamageType.Bludgeoning))
        val path = listOf(GridPos(1, 0))
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("hero"), path, onWallHit = onWallHit), emptyMap(), s.catalog)

        assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(onWallHit, out.spawn)
    }

    @Test
    fun moveAlongOntoNonWalkableTileFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val blockedState = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(1, 0))))
        val out = applyEffect(blockedState, Effect.MoveAlong(s.id("hero"), listOf(GridPos(1, 0))), emptyMap(), s.catalog)

        assertEquals(GridPos(0, 0), out.state.byId.getValue(s.id("hero")).pos)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(Rejection.Blocked(GridPos(1, 0)), fizzled.reason)
    }

    @Test
    fun moveAlongOnEntityWithNoPositionFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("reserve") { archetype("dummy"); hp(10) } // no at() -> pos == null
        }
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("reserve"), listOf(GridPos(1, 0))), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertIs<Rejection.TargetMissing>(fizzled.reason)
    }

    @Test
    fun moveAlongWithIndexPastPathEndIsANoOp() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.MoveAlong(s.id("hero"), listOf(GridPos(1, 0)), index = 5), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty())
        assertTrue(out.spawn.isEmpty())
    }

    // --- Push ---

    @Test
    fun pushSpawnsAMoveAlongWithTheComputedPath() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(1, 0), distance = 3), emptyMap(), s.catalog)
        assertEquals(s.state, out.state, "push itself moves nothing — it only spawns the MoveAlong that will")
        assertEquals(
            listOf(Effect.MoveAlong(s.id("hero"), listOf(GridPos(1, 0), GridPos(2, 0), GridPos(3, 0)))),
            out.spawn,
        )
    }

    @Test
    fun pushNormalizesADirectionThatIsNotAUnitVector() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        // A raw (5, -5) delta must clamp to a (1, -1) unit step, not push 5x as far.
        val out = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(5, -5), distance = 2), emptyMap(), s.catalog)
        assertEquals(
            listOf(Effect.MoveAlong(s.id("hero"), listOf(GridPos(1, -1), GridPos(2, -2)))),
            out.spawn,
        )
    }

    @Test
    fun pushStopsAtTheFirstBlockedTileAcrossResolverSteps() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
            entity("wall") { archetype("dummy"); at(2, 0) }
        }
        // Push spawns Effect.MoveAlong rather than moving directly — this drives that spawned
        // effect to completion by hand, the same way a real resolver run would, proving the
        // "stop at the first blocked tile, keep whatever ground was already covered" behavior is
        // MoveAlong's own (already-tested) responsibility, not reimplemented here.
        val first = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(1, 0), distance = 3), emptyMap(), s.catalog)
        val moveAlong = assertIs<Effect.MoveAlong>(first.spawn.single())
        var working = first.state
        var current: Effect = moveAlong
        val events = mutableListOf<GameEvent>()
        while (true) {
            val step = applyEffect(working, current, emptyMap(), s.catalog)
            working = step.state
            events += step.events
            current = step.spawn.singleOrNull() ?: break
        }
        assertEquals(GridPos(1, 0), working.byId.getValue(s.id("hero")).pos, "stopped one tile short of the wall at (2,0)")
        assertTrue(events.any { it is GameEvent.Fizzled })
    }

    @Test
    fun pushOnWallHitRidesAlongOntoTheSpawnedMoveAlong() {
        // docs/29-push-on-wall-hit.md: Push itself doesn't spawn onWallHit directly — it threads
        // it onto the MoveAlong it spawns, which is what actually fires it once blocked.
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val onWallHit = listOf(Effect.DealDamage(s.id("hero"), 5, DamageType.Bludgeoning))
        val out = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(1, 0), distance = 3, onWallHit = onWallHit), emptyMap(), s.catalog)
        val moveAlong = assertIs<Effect.MoveAlong>(out.spawn.single())
        assertEquals(onWallHit, moveAlong.onWallHit)
    }

    @Test
    fun pushOnWallHitFiresOnceTheSpawnedMoveAlongActuallyGetsBlocked() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
            entity("wall") { archetype("dummy"); at(2, 0) }
        }
        val onWallHit = listOf(Effect.DealDamage(s.id("hero"), 5, DamageType.Bludgeoning))
        val first = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(1, 0), distance = 3, onWallHit = onWallHit), emptyMap(), s.catalog)
        val moveAlong = assertIs<Effect.MoveAlong>(first.spawn.single())
        // Same "drive the spawned MoveAlong by hand" technique pushStopsAtTheFirstBlockedTile...
        // already uses — this time collecting spawned effects too, not just events.
        var working = first.state
        var current: Effect = moveAlong
        val allSpawned = mutableListOf<Effect>()
        while (true) {
            val step = applyEffect(working, current, emptyMap(), s.catalog)
            working = step.state
            allSpawned += step.spawn
            current = step.spawn.singleOrNull() ?: break
        }
        assertTrue(onWallHit.single() in allSpawned, "the wall-hit damage must actually reach the resolver stack once blocked")
    }

    @Test
    fun pushOnMissingTargetFizzles() {
        val s = scenario { archetype("dummy") { hp = 10 } }
        val out = applyEffect(s.state, Effect.Push(EntityId(999), direction = GridPos(1, 0), distance = 2), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun pushWithZeroDistanceIsANoOp() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.Push(s.id("hero"), direction = GridPos(1, 0), distance = 0), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty())
        assertTrue(out.spawn.isEmpty())
    }

    // --- Teleport ---

    @Test
    fun teleportMovesInstantlyAndEmitsTeleported() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.Teleport(s.id("hero"), GridPos(5, 5)), emptyMap(), s.catalog)
        assertEquals(GridPos(5, 5), out.state.byId.getValue(s.id("hero")).pos)
        assertEquals(listOf(GameEvent.Teleported(s.id("hero"), GridPos(0, 0), GridPos(5, 5))), out.events)
    }

    @Test
    fun teleportOntoAnOccupiedTileFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
            entity("blocker") { archetype("dummy"); at(5, 5) }
        }
        val out = applyEffect(s.state, Effect.Teleport(s.id("hero"), GridPos(5, 5)), emptyMap(), s.catalog)
        assertEquals(GridPos(0, 0), out.state.byId.getValue(s.id("hero")).pos)
        assertEquals(Rejection.Blocked(GridPos(5, 5)), (out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun teleportOntoAWallFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val blockedState = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(5, 5))))
        val out = applyEffect(blockedState, Effect.Teleport(s.id("hero"), GridPos(5, 5)), emptyMap(), s.catalog)
        assertEquals(Rejection.Blocked(GridPos(5, 5)), (out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun teleportOnMissingWhoFizzles() {
        val s = scenario { archetype("dummy") { hp = 10 } }
        val out = applyEffect(s.state, Effect.Teleport(EntityId(999), GridPos(1, 1)), emptyMap(), s.catalog)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    // --- SpawnEntity ---

    @Test
    fun spawnEntityArrivesAtFullDerivedHpApMana() {
        val s = scenario {
            // An innate +5 MaxHp modifier proves the spawn goes through real stats() derivation,
            // not a shortcut straight off Archetype.baseMaxHp/baseMaxAp/baseMaxMana.
            archetype("goblin") { hp = 10; ap = 2; mana = 3; modifier(Modifier.Add(Stat.MaxHp, 5)) }
        }
        val out = applyEffect(s.state, Effect.SpawnEntity(ArchetypeId("goblin"), GridPos(5, 5), Faction.Enemy, Controller.Human), emptyMap(), s.catalog)

        val spawned = out.state.entities.single { it.archetype == ArchetypeId("goblin") }
        assertEquals(15, spawned.health?.current, "must go through stats() — 10 base + 5 from the innate modifier")
        assertEquals(2, spawned.resources?.ap)
        assertEquals(3, spawned.resources?.mana)
        assertEquals(GridPos(5, 5), spawned.pos)
        assertEquals(Faction.Enemy, spawned.actor?.faction)
        assertEquals(listOf(GameEvent.EntitySpawned(spawned.id, ArchetypeId("goblin"), GridPos(5, 5))), out.events)
        assertTrue(checkInvariants(out.state, s.catalog).isEmpty())
    }

    @Test
    fun spawnEntityJoinsTurnOrderAtTheEnd() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
            entity("second") { archetype("dummy"); at(1, 0) }
        }
        val out = applyEffect(s.state, Effect.SpawnEntity(ArchetypeId("dummy"), GridPos(5, 5), Faction.Player, Controller.Human), emptyMap(), s.catalog)
        val spawnedId = out.state.entities.single { it.pos == GridPos(5, 5) }.id
        assertEquals(listOf(s.id("first"), s.id("second"), spawnedId), out.state.turn.order)
    }

    @Test
    fun spawnEntityMintsAFreshNonCollidingId() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.SpawnEntity(ArchetypeId("dummy"), GridPos(5, 5), Faction.Player, Controller.Human), emptyMap(), s.catalog)
        val spawned = out.state.entities.single { it.pos == GridPos(5, 5) }
        assertTrue(spawned.id != s.id("first"))
        assertEquals(s.state.nextEntityId + 1, out.state.nextEntityId)
    }

    @Test
    fun spawnEntityOnAWallFizzles() {
        val s = scenario { archetype("dummy") { hp = 10 } }
        val blockedState = s.state.copy(map = s.state.map.copy(terrain = walls(GridPos(5, 5))))
        val out = applyEffect(blockedState, Effect.SpawnEntity(ArchetypeId("dummy"), GridPos(5, 5), Faction.Player, Controller.Human), emptyMap(), s.catalog)
        assertEquals(blockedState, out.state)
        assertEquals(Rejection.Blocked(GridPos(5, 5)), (out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun spawnEntityOnAnOccupiedTileFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("occupant") { archetype("dummy"); at(5, 5) }
        }
        val out = applyEffect(s.state, Effect.SpawnEntity(ArchetypeId("dummy"), GridPos(5, 5), Faction.Player, Controller.Human), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertEquals(Rejection.Blocked(GridPos(5, 5)), (out.events.single() as GameEvent.Fizzled).reason)
    }

    // --- DestroyEntity ---

    @Test
    fun destroyEntityRemovesFromEntitiesAndTurnOrder() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
            entity("second") { archetype("dummy"); at(1, 0) }
        }
        val out = applyEffect(s.state, Effect.DestroyEntity(s.id("second")), emptyMap(), s.catalog)
        assertTrue(out.state.byId[s.id("second")] == null)
        assertEquals(listOf(s.id("first")), out.state.turn.order)
        assertEquals(listOf(GameEvent.EntityDestroyed(s.id("second"))), out.events)
        assertTrue(checkInvariants(out.state, s.catalog).isEmpty())
    }

    @Test
    fun destroyEntityShiftsActiveIndexDownWhenAnEarlierEntityIsDestroyed() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
            entity("second") { archetype("dummy"); at(1, 0) }
            entity("third") { archetype("dummy"); at(2, 0) }
        }
        // "second" is active (index 1); destroying "first" (index 0, before it) must shift activeIndex to 0
        // so it still points at "second".
        val active = s.state.copy(turn = s.state.turn.copy(activeIndex = 1))
        val out = applyEffect(active, Effect.DestroyEntity(s.id("first")), emptyMap(), s.catalog)
        assertEquals(0, out.state.turn.activeIndex)
        assertEquals(s.id("second"), out.state.turn.order[out.state.turn.activeIndex])
    }

    @Test
    fun destroyEntityKeepsActiveIndexPointingAtTheNextEntityWhenTheActiveEntityDestroysItself() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
            entity("second") { archetype("dummy"); at(1, 0) }
            entity("third") { archetype("dummy"); at(2, 0) }
        }
        val active = s.state.copy(turn = s.state.turn.copy(activeIndex = 1)) // "second" is active
        val out = applyEffect(active, Effect.DestroyEntity(s.id("second")), emptyMap(), s.catalog)
        assertEquals(1, out.state.turn.activeIndex)
        assertEquals(s.id("third"), out.state.turn.order[out.state.turn.activeIndex], "index 1 now holds what was next in line")
    }

    @Test
    fun destroyEntityClampsActiveIndexWhenTheLastActiveEntityDestroysItself() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("first") { archetype("dummy"); at(0, 0) }
            entity("second") { archetype("dummy"); at(1, 0) }
        }
        val active = s.state.copy(turn = s.state.turn.copy(activeIndex = 1)) // "second" (last) is active
        val out = applyEffect(active, Effect.DestroyEntity(s.id("second")), emptyMap(), s.catalog)
        assertEquals(0, out.state.turn.activeIndex, "clamped into bounds — round-wrapping is EndTurn's job, not this primitive's")
        assertTrue(checkInvariants(out.state, s.catalog).isEmpty())
    }

    @Test
    fun destroyingTheOnlyEntityLeavesAnEmptyOrderAtIndexZero() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("only") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.DestroyEntity(s.id("only")), emptyMap(), s.catalog)
        assertTrue(out.state.turn.order.isEmpty())
        assertEquals(0, out.state.turn.activeIndex)
    }

    @Test
    fun destroyingAnAlreadyGoneEntityIsANoOp() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        val out = applyEffect(s.state, Effect.DestroyEntity(EntityId(999)), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty())
    }

    @Test
    fun destroyEntityBreaksItsOwnConcentrationAndClearsTheLinkedStatus() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("caster") { archetype("dummy"); at(0, 0) }
            entity("ally") { archetype("dummy"); at(1, 0) }
        }
        val link = LinkId(1)
        val linked = s.state.copy(
            entities = s.state.entities.map {
                when (it.id) {
                    s.id("caster") -> it.copy(concentrating = link)
                    s.id("ally") -> it.copy(statuses = listOf(ActiveStatus(StatusId("blessed"), sourceId = s.id("caster"), linkId = link, expiry = Expiry.Permanent, appliedAtVersion = 0)))
                    else -> it
                }
            },
        )
        val out = applyEffect(linked, Effect.DestroyEntity(s.id("caster")), emptyMap(), s.catalog)
        assertTrue(out.state.byId[s.id("ally")]?.statuses?.isEmpty() == true, "the linked status on the ally must fall away with the caster's concentration")
        assertTrue(out.events.any { it is GameEvent.ConcentrationBroken })
    }

    // --- SpendCost ---

    @Test
    fun spendCostDeductsApAndManaAndMarksQuickUsed() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 3; mana = 5 }
            entity("hero") { archetype("dummy"); at(0, 0); ap(3); mana(5) }
        }
        val out = applyEffect(s.state, Effect.SpendCost(s.id("hero"), ap = 1, mana = 2, markQuickUsed = true), emptyMap(), s.catalog)

        val resources = out.state.byId.getValue(s.id("hero")).resources!!
        assertEquals(2, resources.ap)
        assertEquals(3, resources.mana)
        assertTrue(resources.quickUsed)
        assertEquals(listOf(GameEvent.ResourcesSpent(s.id("hero"), 1, 2)), out.events)
    }

    @Test
    fun spendCostFailsWithNotEnoughMana() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 3; mana = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); ap(3); mana(2) }
        }
        val out = applyEffect(s.state, Effect.SpendCost(s.id("hero"), mana = 5), emptyMap(), s.catalog)

        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(Rejection.NotEnoughMana(need = 5, have = 2), fizzled.reason)
    }

    @Test
    fun spendCostFailsWithNotEnoughAp() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 1; mana = 5 }
            entity("hero") { archetype("dummy"); at(0, 0); ap(1); mana(5) }
        }
        val out = applyEffect(s.state, Effect.SpendCost(s.id("hero"), ap = 2), emptyMap(), s.catalog)

        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(Rejection.NotEnoughAp(need = 2, have = 1), fizzled.reason)
    }

    @Test
    fun spendCostOnEntityWithNoResourcesFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("puppet") { archetype("dummy"); at(0, 0); hp(10) } // no ap()/mana() -> resources == null
        }
        val out = applyEffect(s.state, Effect.SpendCost(s.id("puppet"), ap = 1), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        val fizzled = assertIs<GameEvent.Fizzled>(out.events.single())
        assertIs<Rejection.TargetMissing>(fizzled.reason)
    }

    // --- ApplyStatus: one test per StackPolicy ---

    @Test
    fun applyStatusRefreshResetsStacksToOneAndUpdatesExpiry() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("bless") { stackPolicy = StackPolicy.Refresh }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "bless", stacks = 1)
        }
        val out = applyEffect(
            s.state,
            Effect.ApplyStatus(s.id("hero"), StatusId("bless"), stacks = 5, expiry = Expiry.EndOfRound(3)),
            emptyMap(),
            s.catalog,
        )
        val statuses = out.state.byId.getValue(s.id("hero")).statuses
        assertEquals(1, statuses.size)
        assertEquals(1, statuses[0].stacks, "Refresh always resets to 1 stack regardless of incoming stacks")
        assertEquals(Expiry.EndOfRound(3), statuses[0].expiry)
    }

    @Test
    fun applyStatusAddStacksAccumulatesAndRefreshesExpiry() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("poison") { stackPolicy = StackPolicy.AddStacks }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "poison", stacks = 2)
        }
        val out = applyEffect(
            s.state,
            Effect.ApplyStatus(s.id("hero"), StatusId("poison"), stacks = 3, expiry = Expiry.EndOfRound(9)),
            emptyMap(),
            s.catalog,
        )
        val statuses = out.state.byId.getValue(s.id("hero")).statuses
        assertEquals(1, statuses.size)
        assertEquals(5, statuses[0].stacks)
        assertEquals(Expiry.EndOfRound(9), statuses[0].expiry)
    }

    @Test
    fun applyStatusKeepStrongestDropsWeakerIncoming() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("rage") { stackPolicy = StackPolicy.KeepStrongest }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "rage", stacks = 5)
        }
        val out = applyEffect(
            s.state,
            Effect.ApplyStatus(s.id("hero"), StatusId("rage"), stacks = 2, expiry = Expiry.Permanent),
            emptyMap(),
            s.catalog,
        )
        assertEquals(s.state, out.state, "weaker incoming status must not change state at all")
        assertTrue(out.events.isEmpty())
    }

    @Test
    fun applyStatusKeepStrongestReplacesWeakerExisting() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("rage") { stackPolicy = StackPolicy.KeepStrongest }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "rage", stacks = 1)
        }
        val out = applyEffect(
            s.state,
            Effect.ApplyStatus(s.id("hero"), StatusId("rage"), stacks = 9, expiry = Expiry.Permanent),
            emptyMap(),
            s.catalog,
        )
        val statuses = out.state.byId.getValue(s.id("hero")).statuses
        assertEquals(1, statuses.size)
        assertEquals(9, statuses[0].stacks)
    }

    @Test
    fun applyStatusIndependentKeepsBothInstances() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("mark") { stackPolicy = StackPolicy.Independent }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            entity("caster1") { archetype("dummy"); at(1, 0); hp(20) }
            status("hero", "mark", stacks = 1)
        }
        val out = applyEffect(
            s.state,
            Effect.ApplyStatus(s.id("hero"), StatusId("mark"), stacks = 1, expiry = Expiry.Permanent, sourceId = s.id("caster1")),
            emptyMap(),
            s.catalog,
        )
        val statuses = out.state.byId.getValue(s.id("hero")).statuses
        assertEquals(2, statuses.size, "Independent must never merge with an existing instance")
    }

    // --- generic re-validation: every handler fizzles rather than throws/no-ops on a missing target ---

    @Test
    fun everyHandlerFizzlesOnMissingTargetRatherThanThrowing() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val missing = EntityId(12345)
        val effects: List<Effect> = listOf(
            Effect.DealDamage(missing, 1, DamageType.Fire),
            Effect.MoveAlong(missing, listOf(GridPos(1, 0))),
            Effect.SpendCost(missing, ap = 1),
            Effect.ApplyStatus(missing, StatusId("whatever"), expiry = Expiry.Permanent),
        )
        for (effect in effects) {
            val out = applyEffect(s.state, effect, emptyMap(), s.catalog)
            assertEquals(s.state, out.state, "effect $effect must not mutate state on a missing target")
            val fizzled = assertIs<GameEvent.Fizzled>(out.events.single(), "effect $effect must emit Fizzled")
            assertIs<Rejection.TargetMissing>(fizzled.reason)
        }
    }

    // --- Heal ---

    @Test
    fun healRestoresHpAndEmitsHealed() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("target"), amount = 7), emptyMap(), s.catalog)
        assertEquals(17, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(listOf(GameEvent.Healed(s.id("target"), 7, null)), out.events)
    }

    @Test
    fun healClampsAtDerivedMaxHpAndReportsOnlyTheActualAmountHealed() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(18) }
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("target"), amount = 10), emptyMap(), s.catalog)
        assertEquals(20, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(2, (out.events.single() as GameEvent.Healed).amount, "overheal must report what actually landed, not the raw amount")
    }

    @Test
    fun healFromZeroHpEmitsRevivedAlongsideHealed() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(0) }
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("target"), amount = 5), emptyMap(), s.catalog)
        assertEquals(5, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(
            listOf(GameEvent.Healed(s.id("target"), 5, null), GameEvent.Revived(s.id("target"))),
            out.events,
        )
    }

    @Test
    fun healAboveZeroHpNeverEmitsRevived() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("target"), amount = 5), emptyMap(), s.catalog)
        assertTrue(out.events.none { it is GameEvent.Revived })
    }

    @Test
    fun healOnIndestructibleTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("wall") { archetype("dummy"); at(0, 0) } // no hp() -> health == null
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("wall"), amount = 5), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    // --- RemoveStatus ---

    @Test
    fun removeStatusStripsItAndEmitsStatusExpired() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            statusDef("burning") {}
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
            status("target", "burning")
        }
        val out = applyEffect(s.state, Effect.RemoveStatus(s.id("target"), StatusId("burning")), emptyMap(), s.catalog)
        assertTrue(out.state.byId.getValue(s.id("target")).statuses.isEmpty())
        assertEquals(listOf(GameEvent.StatusExpired(s.id("target"), StatusId("burning"))), out.events)
    }

    @Test
    fun removeStatusNotPresentIsASilentNoOpNotAFizzle() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val out = applyEffect(s.state, Effect.RemoveStatus(s.id("target"), StatusId("neverApplied")), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty(), "a status that was never there isn't a precondition failure")
    }

    @Test
    fun removeStatusOnMissingTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val out = applyEffect(s.state, Effect.RemoveStatus(EntityId(999), StatusId("burning")), emptyMap(), s.catalog)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    // --- Composite ---

    @Test
    fun compositeUnpacksIntoItsEffectsWithNoStateChangeOrEventOfItsOwn() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("target") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val inner = listOf(
            Effect.DealDamage(s.id("target"), 3, DamageType.Fire),
            Effect.Heal(s.id("target"), 1),
        )
        val out = applyEffect(s.state, Effect.Composite(inner), emptyMap(), s.catalog)
        assertEquals(s.state, out.state, "Composite itself must not change state")
        assertTrue(out.events.isEmpty(), "Composite itself must not emit an event")
        assertEquals(inner, out.spawn)
    }
}
