package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.Scenario
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TurnBoundaryTest {

    private fun twoEntityScenario() = scenario {
        archetype("dummy") { hp = 10; ap = 2; mana = 3 }
        entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2); mana(3) }
        entity("goblin") { archetype("dummy"); at(1, 0); hp(10); ap(2); mana(3) }
        initiative("hero", "goblin")
        statusDef("heroBuff") {}
        statusDef("goblinBuff") {}
        statusDef("goblinDebuff") {}
        statusDef("roundBuff") {}
    }

    /** DSL's status() can't reference an EntityId before the scenario is built, so attach targeted expiries after. */
    private fun withStatus(s: Scenario, onWhom: String, status: String, expiry: Expiry, source: String? = null) =
        s.state.copy(
            entities = s.state.entities.map {
                if (it.id == s.id(onWhom)) {
                    it.copy(statuses = it.statuses + de.jackbeback.pocketquest.core.model.ActiveStatus(
                        def = StatusId(status), sourceId = source?.let { n -> s.id(n) }, linkId = null,
                        expiry = expiry, appliedAtVersion = 0,
                    ))
                } else it
            },
        )

    @Test
    fun endOfTurnExpiryFiresOnlyForTheNamedEntity() {
        val s = twoEntityScenario()
        // Expiry.EndOfTurnOf's `who` is what decides the match, independent of which entity
        // physically holds the ActiveStatus (e.g. a status on an ally tied to the caster's turn).
        val state = withStatus(s, "hero", "heroBuff", Expiry.EndOfTurnOf(s.id("hero"), 1))
            .let { withStatus(s.copy(state = it), "goblin", "goblinBuff", Expiry.EndOfTurnOf(s.id("goblin"), 1)) }

        val out = applyEffect(state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        val hero = out.state.byId.getValue(s.id("hero"))
        val goblin = out.state.byId.getValue(s.id("goblin"))
        assertTrue(hero.statuses.none { it.def == StatusId("heroBuff") }, "expiry named hero, hero's turn just ended -> must expire")
        assertTrue(goblin.statuses.any { it.def == StatusId("goblinBuff") }, "expiry named goblin, but it's hero's turn that ended -> must not expire yet")
    }

    @Test
    fun endOfRoundExpiryFiresOnlyWhenWrappingToANewRound() {
        val s = twoEntityScenario()
        val state = withStatus(s, "hero", "roundBuff", Expiry.EndOfRound(1))

        // hero -> goblin: index advances 0->1, does NOT wrap, round stays 1 -> should NOT expire yet.
        val afterHero = applyEffect(state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        assertTrue(afterHero.state.byId.getValue(s.id("hero")).statuses.any { it.def == StatusId("roundBuff") })

        // goblin -> hero: index wraps 1->0, round becomes 2 -> EndOfRound(1) fires now.
        val afterGoblin = applyEffect(afterHero.state, Effect.EndTurn(s.id("goblin")), emptyMap(), s.catalog)
        assertTrue(afterGoblin.state.byId.getValue(s.id("hero")).statuses.none { it.def == StatusId("roundBuff") })
        assertEquals(2, afterGoblin.state.turn.round)
    }

    @Test
    fun startOfTurnExpiryFiresForTheNewlyActiveEntity() {
        val s = twoEntityScenario()
        val state = withStatus(s, "goblin", "goblinDebuff", Expiry.StartOfTurnOf(s.id("goblin"), 1))

        val out = applyEffect(state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        assertTrue(out.state.byId.getValue(s.id("goblin")).statuses.none { it.def == StatusId("goblinDebuff") })
    }

    @Test
    fun apResetsToDerivedMaximumButManaPersistsAcrossTurnBoundary() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2; mana = 3 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(0); mana(0) } // spent everything
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10); ap(2); mana(3) }
            initiative("hero", "goblin")
        }
        // hero's turn must end and wrap all the way back around before hero becomes active again.
        val afterHero = applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        val out = applyEffect(afterHero.state, Effect.EndTurn(s.id("goblin")), emptyMap(), s.catalog)
        val hero = out.state.byId.getValue(s.id("hero")).resources!!
        assertEquals(2, hero.ap)
        // mana is a per-encounter pool (doc10) — endTurn must not refill it, only Effect.RefillMana does.
        assertEquals(0, hero.mana)
        assertTrue(out.events.contains(GameEvent.ResourcesReset(s.id("hero"), ap = 2, mana = 0)))
    }

    @Test
    fun expiredApBuffDoesNotInflateTheResetCeiling() {
        // The exact bug docs/09 calls out: a buff granting +1 AP that expires at start of turn
        // must NOT still pay out for the reset — expiry must run before the reset reads Stats.
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(0) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10); ap(2) }
            initiative("hero", "goblin")
            statusDef("hasted") { modifier(Modifier.Add(Stat.MaxAp, 1)) }
        }
        val state = withStatus(s, "hero", "hasted", Expiry.StartOfTurnOf(s.id("hero"), 2))
        val afterHero = applyEffect(state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        val out = applyEffect(afterHero.state, Effect.EndTurn(s.id("goblin")), emptyMap(), s.catalog)
        assertEquals(2, out.state.byId.getValue(s.id("hero")).resources!!.ap, "expired hasted buff must not still grant +1 AP")
    }

    @Test
    fun quickAndReactionUsedResetOnNewTurn() {
        val s = twoEntityScenario()
        val usedUp = s.state.copy(
            entities = s.state.entities.map {
                if (it.id == s.id("hero")) it.copy(resources = it.resources!!.copy(quickUsed = true, reactionUsed = true)) else it
            },
        )
        val afterHero = applyEffect(usedUp, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        val out = applyEffect(afterHero.state, Effect.EndTurn(s.id("goblin")), emptyMap(), s.catalog)
        val hero = out.state.byId.getValue(s.id("hero")).resources!!
        assertFalse(hero.quickUsed)
        assertFalse(hero.reactionUsed)
    }

    @Test
    fun turnStartedAndEndedEventsCarryTheRightRound() {
        val s = twoEntityScenario()
        val out = applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(GameEvent.TurnEnded(s.id("hero")), out.events.first())
        // TurnStarted fires right after TurnEnded — before that turn's own expiry/reset events,
        // not after them (it marks the turn beginning, not everything that happens as a result).
        val started = assertIs<GameEvent.TurnStarted>(out.events[1])
        assertEquals(s.id("goblin"), started.who)
        assertEquals(1, started.round)
    }

    @Test
    fun activeIndexAdvancesToTheNextEntityInOrder() {
        val s = twoEntityScenario()
        val out = applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(1, out.state.turn.activeIndex)
        assertEquals(s.id("goblin"), out.state.turn.order[out.state.turn.activeIndex])
    }

    @Test
    fun onTurnStartTicksTheNewlyActiveEntitysStatusesUsingTheOriginalCaster() {
        val s = scenario {
            archetype("dummy") { hp = 20; ap = 2 }
            entity("healer") { archetype("dummy"); at(0, 0); hp(20); ap(2) }
            entity("hero") { archetype("dummy"); at(1, 0); hp(10); ap(2) }
            initiative("healer", "hero")
            statusDef("regen") {
                // Ref.Caster should resolve to the status's original source (healer), not the
                // ticking entity (hero) — and Ref.EachTarget should resolve to hero, the ticking entity.
                onTurnStart(EffectTemplate.DealDamage(Ref.Caster, amount = 1, damageType = DamageType.Fire))
                onTurnStart(EffectTemplate.DealDamage(Ref.EachTarget, amount = 2, damageType = DamageType.Poison))
            }
        }
        val state = withStatus(s, "hero", "regen", Expiry.Permanent, source = "healer")
        val out = applyEffect(state, Effect.EndTurn(s.id("healer")), emptyMap(), s.catalog)
        assertEquals(
            listOf(
                Effect.DealDamage(s.id("healer"), 1, DamageType.Fire),
                Effect.DealDamage(s.id("hero"), 2, DamageType.Poison),
            ),
            out.spawn,
        )
    }

    // --- KNOWN_ISSUES.md #7: dead entities getting turns, empty/all-dead order ---

    @Test
    fun endTurnSkipsADeadEntityAndGivesTheTurnToTheNextLivingOne() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(2) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(0); ap(2) } // already dead
            entity("orc") { archetype("dummy"); at(2, 0); hp(10); ap(2) }
            initiative("hero", "goblin", "orc")
        }
        val out = applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(s.id("orc"), out.state.turn.order[out.state.turn.activeIndex])
        assertTrue(out.events.contains(GameEvent.TurnStarted(s.id("orc"), 1)), "the dead goblin's turn must be skipped, not started")
        assertTrue(out.events.none { it is GameEvent.ResourcesReset && it.who == s.id("goblin") }, "a dead entity must not get its resources reset either")
    }

    @Test
    fun endTurnThrowsRatherThanDivideByZeroOnEmptyOrder() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            initiative("hero")
        }
        val brokenState = s.state.copy(turn = s.state.turn.copy(order = emptyList()))
        assertFailsWith<IllegalStateException> {
            applyEffect(brokenState, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        }
    }

    @Test
    fun endTurnThrowsWhenEveryEntityInOrderIsDead() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(0) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(0) }
            initiative("hero", "goblin")
        }
        assertFailsWith<IllegalStateException> {
            applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        }
    }

    // --- doc17/doc10 1.1: mana is a per-encounter pool, refilled only by Effect.RefillMana ---

    @Test
    fun refillManaFillsToDerivedMaximumAndEmitsManaRefilled() {
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 9 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(2) }
        }
        val out = applyEffect(s.state, Effect.RefillMana(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(9, out.state.byId.getValue(s.id("hero")).resources!!.mana)
        assertEquals(listOf(GameEvent.ManaRefilled(s.id("hero"), 9)), out.events)
    }

    @Test
    fun refillManaIsANoOpAndEmitsNothingWhenAlreadyFull() {
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 9 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(9) }
        }
        val out = applyEffect(s.state, Effect.RefillMana(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(9, out.state.byId.getValue(s.id("hero")).resources!!.mana)
        assertTrue(out.events.isEmpty())
    }

    @Test
    fun refillManaIsANoOpForAnEntityWithNoResources() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) } // no ap()/mana() -> resources == null
        }
        val out = applyEffect(s.state, Effect.RefillMana(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty())
    }

    @Test
    fun refillManaFizzlesOnAMissingEntity() {
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 9 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(2) }
        }
        val ghost = de.jackbeback.pocketquest.core.model.EntityId(999L)
        val out = applyEffect(s.state, Effect.RefillMana(ghost), emptyMap(), s.catalog)
        assertTrue(out.events.single() is GameEvent.Fizzled)
    }

    @Test
    fun compositeRefillsManaForAWholeParty() {
        val s = scenario {
            archetype("dummy") { hp = 10; mana = 9 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); mana(1) }
            entity("ally") { archetype("dummy"); at(1, 0); hp(10); mana(0) }
        }
        val out = applyEffect(
            s.state,
            Effect.Composite(listOf(Effect.RefillMana(s.id("hero")), Effect.RefillMana(s.id("ally")))),
            emptyMap(),
            s.catalog,
        )
        assertEquals(listOf(Effect.RefillMana(s.id("hero")), Effect.RefillMana(s.id("ally"))), out.spawn)
    }

    @Test
    fun manaIsUnchangedAcrossATurnBoundaryButFullAfterAnExplicitRefill() {
        val s = scenario {
            archetype("dummy") { hp = 10; ap = 2; mana = 9 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); ap(0); mana(4) } // partially spent
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10); ap(2); mana(9) }
            initiative("hero", "goblin")
        }
        val afterHero = applyEffect(s.state, Effect.EndTurn(s.id("hero")), emptyMap(), s.catalog)
        val afterGoblin = applyEffect(afterHero.state, Effect.EndTurn(s.id("goblin")), emptyMap(), s.catalog)
        // wrapped all the way back to hero: the turn boundary must not have touched hero's mana.
        assertEquals(4, afterGoblin.state.byId.getValue(s.id("hero")).resources!!.mana)

        // only an explicit RefillMana (the encounter-end signal) brings it back to max.
        val refilled = applyEffect(afterGoblin.state, Effect.RefillMana(s.id("hero")), emptyMap(), s.catalog)
        assertEquals(9, refilled.state.byId.getValue(s.id("hero")).resources!!.mana)
    }
}
