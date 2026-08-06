package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
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
        val effect = Effect.DealDamage(s.id("target"), amount = 7, type = DamageType.Fire)
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
        val effect = Effect.DealDamage(s.id("target"), amount = 999, type = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog)

        assertEquals(0, out.state.byId.getValue(s.id("target")).health!!.current)
        assertEquals(GameEvent.DamageTaken(s.id("target"), 999, DamageType.Fire), out.events[0])
        assertEquals(GameEvent.Died(s.id("target")), out.events[1])
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
}
