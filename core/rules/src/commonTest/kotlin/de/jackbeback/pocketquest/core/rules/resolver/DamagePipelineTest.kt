package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.AbsorbPool
import de.jackbeback.pocketquest.core.model.DamageStep
import de.jackbeback.pocketquest.core.model.DamageTag
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.HealStep
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.StepCondition
import de.jackbeback.pocketquest.core.model.StepRef
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** doc18-damage-pipeline.md's Layer 2 test list, one per step plus ordering. */
class DamagePipelineTest {

    // --- Retarget: the ordering test that matters most ---

    @Test
    fun retargetMovesTheDamageAndTheNewTargetsResistanceApplies() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20; modifier(Modifier.Resist(DamageType.Fire, Resistance.Resistant)) }
            statusDef("ward") {
                damageStep(DamageStep.Retarget(StepRef.StatusSource, StepCondition(refWithinTiles = 3)))
            }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(1, 0); hp(20) }
            entity("goblin") { archetype("ally"); at(2, 0); hp(20) }
            status("ally", "ward", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire, source = s.id("goblin")), emptyMap(), s.catalog)

        assertEquals(GameEvent.DamageRedirected(s.id("ally"), s.id("tank"), s.catalog.statuses.keys.first()), out.events[0])
        val taken = assertIs<GameEvent.DamageTaken>(out.events[1])
        assertEquals(s.id("tank"), taken.target)
        assertEquals(5, taken.amount, "the tank's own Fire resistance applies, not the ally's (the ally has none)")
        assertEquals(20, out.state.byId.getValue(s.id("ally")).health!!.current, "the original target took nothing")
        assertEquals(15, out.state.byId.getValue(s.id("tank")).health!!.current)
    }

    @Test
    fun aWardWithTheTankFourTilesAwayDoesNotFire() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20 }
            statusDef("ward") { damageStep(DamageStep.Retarget(StepRef.StatusSource, StepCondition(refWithinTiles = 3))) }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(4, 0); hp(20) } // 4 tiles away, limit is 3
            status("ally", "ward", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertTrue(out.events.none { it is GameEvent.DamageRedirected })
        assertEquals(10, out.state.byId.getValue(s.id("ally")).health!!.current)
    }

    @Test
    fun aWardWhoseSourceIsDownedDoesNotFire() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20 }
            statusDef("ward") { damageStep(DamageStep.Retarget(StepRef.StatusSource, StepCondition(refWithinTiles = 3))) }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(1, 0); hp(0) } // downed
            status("ally", "ward", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertTrue(out.events.none { it is GameEvent.DamageRedirected })
        assertEquals(10, out.state.byId.getValue(s.id("ally")).health!!.current)
    }

    @Test
    fun aWardExcludingAoeDoesNotFireOnAnAoeHit() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20 }
            statusDef("ward") {
                damageStep(DamageStep.Retarget(StepRef.StatusSource, StepCondition(refWithinTiles = 3, excludesTags = setOf(DamageTag.Aoe))))
            }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(1, 0); hp(20) }
            status("ally", "ward", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire, tags = setOf(DamageTag.Aoe)), emptyMap(), s.catalog)
        assertTrue(out.events.none { it is GameEvent.DamageRedirected })
        assertEquals(10, out.state.byId.getValue(s.id("ally")).health!!.current)
    }

    @Test
    fun twoMutuallyWardingTanksTerminateOnTheSecondHopInsteadOfPingPonging() {
        // A's ward sends damage to B; B's ward would send it back to A, but A is already in `hops`,
        // so that second redirect is skipped and the damage settles on B — the dedup-by-hops check
        // terminates the mutual pair without needing the 4-hop cap at all.
        val s = scenario {
            archetype("tank") { hp = 20 }
            statusDef("wardA") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(1)))) } // points at tankB
            statusDef("wardB") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(0)))) } // points at tankA
            entity("tankA") { archetype("tank"); at(0, 0); hp(20) } // id 0
            entity("tankB") { archetype("tank"); at(1, 0); hp(20) } // id 1
            status("tankA", "wardA") // A's damage redirects to B (id 1)
            status("tankB", "wardB") // B's damage redirects to A (id 0)
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("tankA"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(listOf(GameEvent.DamageRedirected(s.id("tankA"), s.id("tankB"), StatusId("wardA"))), out.events.filterIsInstance<GameEvent.DamageRedirected>())
        assertEquals(20, out.state.byId.getValue(s.id("tankA")).health!!.current)
        assertEquals(10, out.state.byId.getValue(s.id("tankB")).health!!.current)
    }

    @Test
    fun aChainLongerThanFourHopsFizzlesInsteadOfApplyingDamage() {
        // 5 distinct tanks each retargeting to the next (never revisiting a prior hop) exceeds
        // doc18's 4-hop loop-protection cap even though no entity is ever redirected to twice.
        val s = scenario {
            archetype("tank") { hp = 20 }
            statusDef("ward0") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(1)))) }
            statusDef("ward1") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(2)))) }
            statusDef("ward2") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(3)))) }
            statusDef("ward3") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(4)))) }
            statusDef("ward4") { damageStep(DamageStep.Retarget(StepRef.Fixed(EntityId(5)))) }
            entity("t0") { archetype("tank"); at(0, 0); hp(20) }
            entity("t1") { archetype("tank"); at(1, 0); hp(20) }
            entity("t2") { archetype("tank"); at(2, 0); hp(20) }
            entity("t3") { archetype("tank"); at(3, 0); hp(20) }
            entity("t4") { archetype("tank"); at(4, 0); hp(20) }
            entity("t5") { archetype("tank"); at(5, 0); hp(20) }
            status("t0", "ward0")
            status("t1", "ward1")
            status("t2", "ward2")
            status("t3", "ward3")
            status("t4", "ward4")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("t0"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertIs<GameEvent.Fizzled>(out.events.last())
        for (name in listOf("t0", "t1", "t2", "t3", "t4", "t5")) {
            assertEquals(20, out.state.byId.getValue(s.id(name)).health!!.current, "$name took no damage — the whole chain was cancelled")
        }
    }

    // --- Scale then Reduce ---

    @Test
    fun scaleAppliesBeforeReduce() {
        val s = scenario {
            archetype("dummy") { hp = 20; modifier(Modifier.Resist(DamageType.Fire, Resistance.Resistant)) }
            statusDef("armor") { damageStep(DamageStep.Reduce(flat = 3)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "armor")
        }
        // 20 damage, Resistant halves to 10, then Reduce(3) -> 7, not (20-3)/2=8.5->8.
        val out = applyEffect(s.state, Effect.DealDamage(s.id("hero"), 20, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(7, (out.events.single { it is GameEvent.DamageTaken } as GameEvent.DamageTaken).amount)
        assertEquals(13, out.state.byId.getValue(s.id("hero")).health!!.current)
    }

    @Test
    fun multipleReduceStepsSum() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("armor") { damageStep(DamageStep.Reduce(flat = 2)) }
            statusDef("shield") { damageStep(DamageStep.Reduce(flat = 3)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "armor")
            status("hero", "shield")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("hero"), 10, DamageType.Bludgeoning), emptyMap(), s.catalog)
        assertEquals(5, (out.events.single { it is GameEvent.DamageTaken } as GameEvent.DamageTaken).amount, "10 - 2 - 3 = 5")
    }

    // --- Absorb ---

    @Test
    fun absorbConsumesTempBeforeHpAndCarriesTheRemainderThrough() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("shield") { damageStep(DamageStep.Absorb(AbsorbPool.TargetTemp)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "shield")
        }
        val withTemp = s.state.copy(entities = s.state.entities.map { if (it.id == s.id("hero")) it.copy(health = it.health!!.copy(temp = 4)) else it })
        val out = applyEffect(withTemp, Effect.DealDamage(s.id("hero"), 10, DamageType.Bludgeoning), emptyMap(), s.catalog)
        val hero = out.state.byId.getValue(s.id("hero"))
        assertEquals(0, hero.health!!.temp, "the shield is fully consumed")
        assertEquals(14, hero.health!!.current, "20 - (10 - 4) = 14, the remainder after the shield carries through")
    }

    @Test
    fun absorbGreaterThanDamageLeavesTempRemaining() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("shield") { damageStep(DamageStep.Absorb(AbsorbPool.TargetTemp)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "shield")
        }
        val withTemp = s.state.copy(entities = s.state.entities.map { if (it.id == s.id("hero")) it.copy(health = it.health!!.copy(temp = 20)) else it })
        val out = applyEffect(withTemp, Effect.DealDamage(s.id("hero"), 5, DamageType.Bludgeoning), emptyMap(), s.catalog)
        val hero = out.state.byId.getValue(s.id("hero"))
        assertEquals(15, hero.health!!.temp)
        assertEquals(20, hero.health!!.current, "fully absorbed, no real HP lost")
    }

    // --- Reflect ---

    @Test
    fun reflectSpawnsDealDamageBackAtTheSourceThatDoesNotItselfReflect() {
        val s = scenario {
            archetype("attacker") { hp = 20 }
            archetype("thorny") { hp = 20 }
            statusDef("thorns") { damageStep(DamageStep.Reflect(fraction = 0.5f)) }
            entity("attacker") { archetype("attacker"); at(0, 0); hp(20) }
            entity("thorny") { archetype("thorny"); at(1, 0); hp(20) }
            status("thorny", "thorns")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("thorny"), 10, DamageType.Slashing, source = s.id("attacker")), emptyMap(), s.catalog)
        val reflect = out.spawn.single()
        val dealDamage = assertIs<Effect.DealDamage>(reflect)
        assertEquals(s.id("attacker"), dealDamage.target)
        assertEquals(5, dealDamage.amount)
        assertTrue(dealDamage.fromReflect, "must be tagged so applying it doesn't itself spawn another reflect")

        // Route that reflected DealDamage back onto the thorny entity itself (which does have
        // thorns) — if fromReflect didn't suppress step 8, this would spawn an infinite reflect chain.
        val reflectedAgain = applyEffect(s.state, dealDamage.copy(target = s.id("thorny")), emptyMap(), s.catalog)
        assertTrue(reflectedAgain.spawn.none { it is Effect.DealDamage }, "fromReflect=true must suppress a further reflect even back onto a thorny target")
    }

    // --- Prevent ---

    @Test
    fun preventCancelsDamageEntirelyAndEmitsFizzled() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("sanctuary") { damageStep(DamageStep.Prevent()) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "sanctuary")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("hero"), 10, DamageType.Fire), emptyMap(), s.catalog)
        assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(20, out.state.byId.getValue(s.id("hero")).health!!.current)
    }

    // --- Convert ---

    @Test
    fun convertChangesTheDamageTypeBeforeScaleAndReduceSeeIt() {
        val s = scenario {
            archetype("dummy") { hp = 20; modifier(Modifier.Resist(DamageType.Cold, Resistance.Resistant)) }
            statusDef("elementalShift") { damageStep(DamageStep.Convert(from = DamageType.Fire, to = DamageType.Cold)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "elementalShift")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("hero"), 10, DamageType.Fire), emptyMap(), s.catalog)
        val taken = assertIs<GameEvent.DamageTaken>(out.events.single())
        assertEquals(DamageType.Cold, taken.damageType)
        assertEquals(5, taken.amount, "the target's Cold resistance applies post-convert, not Fire's (none)")
    }

    // --- Split ---

    @Test
    fun splitPeelsOffAPortionAsAnIndependentDealDamage() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20 }
            statusDef("sharedPain") { damageStep(DamageStep.Split(StepRef.StatusSource, fraction = 0.5f)) }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(1, 0); hp(20) }
            status("ally", "sharedPain", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire), emptyMap(), s.catalog)
        val spawned = assertIs<Effect.DealDamage>(out.spawn.single())
        assertEquals(s.id("tank"), spawned.target)
        assertEquals(5, spawned.amount)
        // The primary instance keeps the other half, un-retargeted — it stays on the ally: 20 - 5 = 15.
        assertEquals(15, out.state.byId.getValue(s.id("ally")).health!!.current)
    }

    // --- Golden event ordering: DamageRedirected must land before DamageTaken ---

    @Test
    fun damageRedirectedPrecedesDamageTakenInTheEventList() {
        val s = scenario {
            archetype("ally") { hp = 20 }
            archetype("tank") { hp = 20 }
            statusDef("ward") { damageStep(DamageStep.Retarget(StepRef.StatusSource)) }
            entity("ally") { archetype("ally"); at(0, 0); hp(20) }
            entity("tank") { archetype("tank"); at(1, 0); hp(20) }
            status("ally", "ward", source = "tank")
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("ally"), 10, DamageType.Fire), emptyMap(), s.catalog)
        val redirectIndex = out.events.indexOfFirst { it is GameEvent.DamageRedirected }
        val takenIndex = out.events.indexOfFirst { it is GameEvent.DamageTaken }
        assertTrue(redirectIndex in 0 until takenIndex, "the director needs DamageRedirected before DamageTaken to animate the arc correctly")
    }

    // --- HealInstance: Prevent, Scale ---

    @Test
    fun healPreventStepCancelsTheHealEntirely() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("cursedWound") { healStep(HealStep.Prevent()) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            status("hero", "cursedWound")
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("hero"), 5), emptyMap(), s.catalog)
        assertIs<GameEvent.Fizzled>(out.events.single())
        assertEquals(10, out.state.byId.getValue(s.id("hero")).health!!.current)
    }

    @Test
    fun healScaleStepAmplifiesTheHeal() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("blessing") { healStep(HealStep.Scale(factor = 2f)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            status("hero", "blessing")
        }
        val out = applyEffect(s.state, Effect.Heal(s.id("hero"), 5), emptyMap(), s.catalog)
        assertEquals(20, out.state.byId.getValue(s.id("hero")).health!!.current, "10 + (5*2) = 20")
        assertEquals(10, (out.events.single() as GameEvent.Healed).amount)
    }

    // --- No damage steps at all: must behave exactly like the pre-pipeline dealDamage ---

    @Test
    fun anEntityWithNoDamageStepsBehavesExactlyLikeBeforeThePipeline() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
        }
        val out = applyEffect(s.state, Effect.DealDamage(s.id("hero"), 7, DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(listOf(GameEvent.DamageTaken(s.id("hero"), 7, DamageType.Fire)), out.events)
        assertEquals(13, out.state.byId.getValue(s.id("hero")).health!!.current)
    }
}
