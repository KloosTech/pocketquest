package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RollEffectTest {

    // --- RollAttack ---

    @Test
    fun rollAttackExpectedModeHitsWhenAverageRollMeetsAc() {
        val s = scenario {
            archetype("attacker") { hp = 10 }
            archetype("lowAc") { hp = 10; ac = 12 } // 10.5 + 4 = 14.5 >= 12 -> hit
            entity("hero") { archetype("attacker"); at(0, 0); hp(10) }
            entity("goblin") { archetype("lowAc"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 4, damage = DiceSpec(1, 6, 3), damageType = DamageType.Slashing)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)

        val rolled = assertIs<GameEvent.AttackRolled>(out.events.single())
        assertTrue(rolled.hit)
        // damage dice average: 1d6 avg 3.5 + modifier 3 = 6.5, rounds half-up to 7
        assertEquals(listOf(Effect.DealDamage(s.id("goblin"), 7, DamageType.Slashing, source = s.id("hero"))), out.spawn)
    }

    @Test
    fun rollAttackExpectedModeMissesWhenAverageRollBelowAc() {
        val s = scenario {
            archetype("attacker") { hp = 10 }
            archetype("highAc") { hp = 10; ac = 25 } // 10.5 + 4 = 14.5 < 25 -> miss
            entity("hero") { archetype("attacker"); at(0, 0); hp(10) }
            entity("goblin") { archetype("highAc"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 4, damage = DiceSpec(1, 6, 3), damageType = DamageType.Slashing)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)

        val rolled = assertIs<GameEvent.AttackRolled>(out.events.single())
        assertTrue(!rolled.hit)
        assertTrue(out.spawn.isEmpty(), "a miss must not spawn DealDamage")
    }

    @Test
    fun rollAttackExpectedModeNeverAdvancesRng() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 20, damage = DiceSpec(2, 6, 0), damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)
        assertEquals(s.state.rng, out.state.rng, "Expected mode must never touch RngState")
    }

    @Test
    fun rollAttackLiveModeIsDeterministicForSameSeed() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 4, damage = DiceSpec(1, 8, 0), damageType = DamageType.Fire)
        val outA = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Live)
        val outB = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Live)

        assertEquals(outA.events, outB.events, "same seed must produce the same roll")
        assertEquals(outA.state.rng, outB.state.rng)
    }

    @Test
    fun rollAttackLiveModeMissConsumesExactlyOneRoll() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            archetype("unhittable") { hp = 10; ac = 999 } // guarantees a miss regardless of the roll
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("unhittable"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 4, damage = DiceSpec(1, 8, 0), damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Live)

        assertTrue(!(out.events.single() as GameEvent.AttackRolled).hit)
        assertEquals(s.state.rng.calls + 1, out.state.rng.calls, "a miss only rolls the d20, never the damage dice")
    }

    @Test
    fun rollAttackOnDeadTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("corpse") { archetype("dummy"); at(1, 0); hp(0) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("corpse"), attackBonus = 4, damage = DiceSpec(1, 6, 0), damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun rollAttackOnMissingAttackerOrTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val missing = EntityId(999)
        val out1 = applyEffect(s.state, Effect.RollAttack(missing, s.id("hero"), 0, damage = DiceSpec(1, 4, 0), damageType = DamageType.Fire), emptyMap(), s.catalog, RngMode.Expected)
        assertIs<GameEvent.Fizzled>(out1.events.single())
        val out2 = applyEffect(s.state, Effect.RollAttack(s.id("hero"), missing, 0, damage = DiceSpec(1, 4, 0), damageType = DamageType.Fire), emptyMap(), s.catalog, RngMode.Expected)
        assertIs<GameEvent.Fizzled>(out2.events.single())
    }

    // --- RollSave ---

    @Test
    fun rollSaveExpectedModeSucceedsWhenAverageRollMeetsDc() {
        val s = scenario {
            archetype("dummy") { hp = 20; abilities(dex = 14) } // dex mod +2; 10.5+2=12.5 >= dc 12 -> success
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val onSuccess = listOf(Effect.ApplyStatus(s.id("hero"), StatusId("half"), expiry = Expiry.Permanent))
        val onFail = listOf(Effect.DealDamage(s.id("hero"), 20, DamageType.Fire))
        val effect = Effect.RollSave(s.id("hero"), Ability.Dex, dc = 12, onSuccess = onSuccess, onFail = onFail)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, RngMode.Expected)

        val rolled = assertIs<GameEvent.SaveRolled>(out.events.single())
        assertTrue(rolled.success)
        assertEquals(onSuccess, out.spawn)
    }

    @Test
    fun rollSaveExpectedModeFailsWhenAverageRollBelowDc() {
        val s = scenario {
            archetype("dummy") { hp = 20; abilities(dex = 8) } // dex mod -1; 10.5-1=9.5 < dc 20 -> fail
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val onSuccess = emptyList<Effect>()
        val onFail = listOf(Effect.DealDamage(s.id("hero"), 20, DamageType.Fire))
        val effect = Effect.RollSave(s.id("hero"), Ability.Dex, dc = 20, onSuccess = onSuccess, onFail = onFail)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, RngMode.Expected)

        val rolled = assertIs<GameEvent.SaveRolled>(out.events.single())
        assertTrue(!rolled.success)
        assertEquals(onFail, out.spawn)
    }

    @Test
    fun rollSaveOnMissingTargetFizzles() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        val out = applyEffect(s.state, Effect.RollSave(EntityId(999), Ability.Con, dc = 10), emptyMap(), s.catalog, RngMode.Expected)
        assertIs<Rejection.TargetMissing>((out.events.single() as GameEvent.Fizzled).reason)
    }

    @Test
    fun advantageAndDisadvantageOnRollAttackCancelOutToNormal() {
        // Regression guard: resolveAdvantage(adv+dis) == Normal, so an attack with both must
        // still use plain d20() semantics rather than one of the advantage/disadvantage branches.
        val s = scenario {
            archetype("dummy") { hp = 10; ac = 12 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(
            s.id("hero"), s.id("goblin"), attackBonus = 4,
            advantage = setOf(AdvSide.Advantage, AdvSide.Disadvantage),
            damage = DiceSpec(1, 6, 0), damageType = DamageType.Fire,
        )
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, RngMode.Expected)
        val rolled = assertIs<GameEvent.AttackRolled>(out.events.single())
        assertEquals(11, rolled.d20) // 10.5 rounds half-up
    }

    // --- KNOWN_ISSUES.md #10 ---

    @Test
    fun expectedModeHitDecisionAgreesWithTheRecordedD20AtTheRoundingBoundary() {
        // Expected mode used to decide hit/success against the unrounded 10.5 while separately
        // rounding the recorded d20 to 11 for the event — an ac of exactly 11 used to come out
        // hit=false (10.5 < 11) even though the recorded d20 (11) + mod (0) >= ac (11) says it
        // should have hit. Both must now agree, because both now come from the same rounded value.
        val s = scenario {
            archetype("dummy") { hp = 10; ac = 11 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 0, damage = DiceSpec(1, 4, 0), damageType = DamageType.Fire)
        val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)

        val rolled = assertIs<GameEvent.AttackRolled>(out.events.single())
        assertEquals(11, rolled.d20)
        assertTrue(rolled.hit, "recorded d20 (11) + mod (0) >= ac (11) must agree with the hit flag")
    }

    @Test
    fun expectedModeReflectsAdvantageAndDisadvantageInsteadOfAlwaysNormal() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            entity("goblin") { archetype("dummy"); at(1, 0); hp(10) }
        }
        fun d20For(advantage: Set<AdvSide>): Int {
            val effect = Effect.RollAttack(s.id("hero"), s.id("goblin"), attackBonus = 0, advantage = advantage, damage = DiceSpec(1, 4, 0), damageType = DamageType.Fire)
            val out = applyEffect(s.state, effect, emptyMap(), s.catalog, mode = RngMode.Expected)
            return (out.events.single() as GameEvent.AttackRolled).d20
        }
        assertEquals(11, d20For(emptySet()), "Normal: E[X]=10.5 -> 11")
        assertEquals(14, d20For(setOf(AdvSide.Advantage)), "Advantage: E[max(X,Y)]=13.825 -> 14, not always 10.5")
        assertEquals(7, d20For(setOf(AdvSide.Disadvantage)), "Disadvantage: E[min(X,Y)]=7.175 -> 7, not always 10.5")
    }
}
