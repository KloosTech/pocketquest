package de.jackbeback.pocketquest.core.rules.stat

import de.jackbeback.pocketquest.core.model.AdvSide
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.RollContext
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsDerivationTest {

    @Test
    fun addThenMulThenOverrideInThatOrder() {
        val s = scenario {
            archetype("dummy") {
                hp = 10
                ac = 10
                modifier(Modifier.Add(Stat.ArmorClass, 4)) // 10 + 4 = 14
                modifier(Modifier.Mul(Stat.ArmorClass, 2f)) // 14 * 2 = 28
                modifier(Modifier.Override(Stat.ArmorClass, 5)) // -> 5, last stage wins
            }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        assertEquals(5, s.entity("hero").stats(s.catalog).armorClass)
    }

    @Test
    fun addAppliesBeforeMul() {
        val s = scenario {
            archetype("dummy") {
                ac = 10
                modifier(Modifier.Add(Stat.ArmorClass, 5)) // 15
                modifier(Modifier.Mul(Stat.ArmorClass, 2f)) // 30, not (10*2)+5=25
            }
            entity("hero") { archetype("dummy"); at(0, 0) }
        }
        assertEquals(30, s.entity("hero").stats(s.catalog).armorClass)
    }

    @Test
    fun twoOverridesResolveBySourceOrderNotIterationOrder() {
        val s = scenario {
            archetype("dummy") { ac = 10 }
            itemDef("ringA") { modifier(Modifier.Override(Stat.ArmorClass, 11)) }
            itemDef("ringB") { modifier(Modifier.Override(Stat.ArmorClass, 22)) }
            entity("hero") {
                archetype("dummy")
                at(0, 0)
                // Ring1 is earlier than Ring2 in Slot's fixed source order, so ringB (Ring2) wins.
                equip(Slot.Ring1, "ringA")
                equip(Slot.Ring2, "ringB")
            }
        }
        assertEquals(22, s.entity("hero").stats(s.catalog).armorClass)
    }

    @Test
    fun removingStatusRecomputesMaxHpWithoutSilentlyHealing() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("tough") { modifier(Modifier.Add(Stat.MaxHp, 10)) }
            entity("buffed") { archetype("dummy"); at(0, 0); hp(25) }
            entity("unbuffed") { archetype("dummy"); at(1, 0); hp(20) }
            status("buffed", "tough")
        }
        val buffed = s.entity("buffed")
        assertEquals(30, buffed.stats(s.catalog).maxHp)
        assertEquals(25, buffed.health!!.current, "current HP is untouched by stats() — clamping is a separate concern")

        val withoutStatus = buffed.copy(statuses = emptyList())
        assertEquals(20, withoutStatus.stats(s.catalog).maxHp)
        assertEquals(25, withoutStatus.health!!.current, "removing the buff must not silently heal back down/up")
    }

    @Test
    fun ringGrantingAddMaxManaRaisesCeilingOnly() {
        val s = scenario {
            archetype("dummy") { mana = 5 }
            itemDef("manaRing") { modifier(Modifier.Add(Stat.MaxMana, 1)) }
            entity("hero") { archetype("dummy"); at(0, 0); mana(5); equip(Slot.Ring1, "manaRing") }
        }
        val stats = s.entity("hero").stats(s.catalog)
        assertEquals(6, stats.maxMana)
        assertEquals(2, stats.maxAp, "unrelated stats (ap) must be untouched by an Add(MaxMana) modifier")
    }

    @Test
    fun statusStacksScaleAddModifiersLinearly() {
        val s = scenario {
            archetype("dummy") { hp = 20 }
            statusDef("poisonStack") { modifier(Modifier.Add(Stat.ArmorClass, -1)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(20) }
            status("hero", "poisonStack", stacks = 3)
        }
        assertEquals(10 - 3, s.entity("hero").stats(s.catalog).armorClass)
    }

    // --- Modifier.Roll -> Stats.rollGrants — KNOWN_ISSUES.md #11 ---

    @Test
    fun statusGrantingRollAppearsInStatsRollGrants() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            statusDef("blessed") { modifier(Modifier.Roll(RollContext.AttackRoll(vs = null), AdvSide.Advantage)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            status("hero", "blessed")
        }
        assertEquals(
            listOf(Modifier.Roll(RollContext.AttackRoll(vs = null), AdvSide.Advantage)),
            s.entity("hero").stats(s.catalog).rollGrants,
        )
    }

    @Test
    fun rollGrantsAreNotScaledByStatusStacks() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            statusDef("blessed") { modifier(Modifier.Roll(RollContext.AttackRoll(vs = null), AdvSide.Advantage)) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
            status("hero", "blessed", stacks = 3)
        }
        // A boolean grant stacked 3x is still just "granted" once — matches the existing rule that
        // Mul/Override/Grant/Resist (everything but Add) apply once regardless of stack count.
        assertEquals(1, s.entity("hero").stats(s.catalog).rollGrants.size)
    }
}
