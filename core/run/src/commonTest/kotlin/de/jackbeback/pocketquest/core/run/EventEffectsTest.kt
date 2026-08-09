package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EventCheck
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.RunEffect
import de.jackbeback.pocketquest.core.model.RunEffectTarget
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
import de.jackbeback.pocketquest.core.rules.abilityModifier
import de.jackbeback.pocketquest.core.rules.d20
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EventEffectsTest {

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private val map = BattleMapDef(
        id = MapId("room"), width = 3, height = 3,
        spawns = listOf(SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0)))),
    )

    private fun catalog(encounter: EncounterSpec? = null) = Catalog(
        archetypes = mapOf(hero.id to hero),
        maps = mapOf(map.id to map),
        encounters = encounter?.let { mapOf(it.id to it) } ?: emptyMap(),
    )

    private fun member(id: String, hp: Int, mana: Int = 5) =
        PartyMember(MemberId(id), name = id, archetype = hero.id, hp = hp, mana = mana, controller = Controller.Human)

    private fun run(vararg members: PartyMember) = RunState(
        runId = RunId("run1"), seed = 1L, rng = RngState(seed = 1L), act = 1,
        graph = NodeGraph(mapOf(NodeId("n1") to GraphNode(NodeId("n1"), act = 1, type = NodeType.Event)), start = NodeId("n1")),
        position = NodeId("n1"),
        party = members.toList(),
    )

    @Test
    fun grantCurrencyAddsGold() {
        val result = applyRunEffect(run(member("m1", hp = 20)), RunEffect.GrantCurrency(10), catalog())
        assertEquals(10, result.gold)
    }

    @Test
    fun grantCurrencyToleratesANegativeToll() {
        val result = applyRunEffect(run(member("m1", hp = 20)).copy(gold = 5), RunEffect.GrantCurrency(-3), catalog())
        assertEquals(2, result.gold)
    }

    @Test
    fun grantItemAddsToInventory() {
        val result = applyRunEffect(run(member("m1", hp = 20)), RunEffect.GrantItem(ItemId("potion")), catalog())
        assertEquals(listOf(ItemId("potion")), result.inventory.items)
    }

    @Test
    fun loseItemRemovesOneMatchingItem() {
        val withItem = run(member("m1", hp = 20)).copy(inventory = Inventory(listOf(ItemId("potion"), ItemId("potion"))))
        val result = applyRunEffect(withItem, RunEffect.LoseItem(ItemId("potion")), catalog())
        assertEquals(listOf(ItemId("potion")), result.inventory.items)
    }

    @Test
    fun damagePartyWholePartyClampsAtZeroAndMarksDowned() {
        val result = applyRunEffect(run(member("m1", hp = 5), member("m2", hp = 20)), RunEffect.DamageParty(10, RunEffectTarget.WholeParty), catalog())
        val m1 = result.party.single { it.memberId == MemberId("m1") }
        val m2 = result.party.single { it.memberId == MemberId("m2") }
        assertEquals(0, m1.hp)
        assertEquals(MemberCondition.Downed, m1.condition)
        assertEquals(10, m2.hp)
        assertEquals(MemberCondition.Healthy, m2.condition)
    }

    @Test
    fun healPartyLowestHpMemberOnlyHealsTheLowest() {
        val result = applyRunEffect(run(member("m1", hp = 5), member("m2", hp = 15)), RunEffect.HealParty(5, RunEffectTarget.LowestHpMember), catalog())
        assertEquals(10, result.party.single { it.memberId == MemberId("m1") }.hp)
        assertEquals(15, result.party.single { it.memberId == MemberId("m2") }.hp)
    }

    @Test
    fun healPartyClampsAtDerivedMaxHp() {
        val result = applyRunEffect(run(member("m1", hp = 18)), RunEffect.HealParty(50, RunEffectTarget.WholeParty), catalog())
        assertEquals(hero.baseMaxHp, result.party.single().hp)
    }

    @Test
    fun healCanReviveADownedMember() {
        val downed = member("m1", hp = 0).copy(condition = MemberCondition.Downed)
        val result = applyRunEffect(run(downed), RunEffect.HealParty(5, RunEffectTarget.WholeParty), catalog())
        val revived = result.party.single()
        assertEquals(5, revived.hp)
        assertEquals(MemberCondition.Healthy, revived.condition)
    }

    @Test
    fun randomMemberConsumesRngAndPicksSomeoneInTheParty() {
        val before = run(member("m1", hp = 20), member("m2", hp = 20))
        val result = applyRunEffect(before, RunEffect.DamageParty(5, RunEffectTarget.RandomMember), catalog())
        assertEquals(1, result.party.count { it.hp == 15 })
        assertEquals(1, result.party.count { it.hp == 20 })
        assertEquals(before.rng.calls + 1, result.rng.calls)
    }

    @Test
    fun forceCombatStartsARealEncounter() {
        val encounter = EncounterSpec(id = EncounterId("e1"), name = "E1", mapId = map.id)
        val result = applyRunEffect(run(member("m1", hp = 20)), RunEffect.ForceCombat(encounter.id), catalog(encounter))
        assertNotNull(result.encounter)
    }

    @Test
    fun wholePartyTargetIsAConsistentNoRngOperation() {
        val before = run(member("m1", hp = 20))
        val result = applyRunEffect(before, RunEffect.DamageParty(1, RunEffectTarget.WholeParty), catalog())
        assertEquals(before.rng.calls, result.rng.calls)
        assertNull(result.outcome)
    }

    @Test
    fun checklessChoiceAppliesEffectsUnconditionally() {
        val choice = EventChoice(label = "L", outcomeText = "You found gold.", effects = listOf(RunEffect.GrantCurrency(10)))
        val result = resolveEventChoice(run(member("m1", hp = 20)), choice, catalog())
        assertEquals("You found gold.", result.text)
        assertEquals(10, result.run.gold)
    }

    @Test
    fun checkedChoiceWithATrivialDcAlwaysSucceeds() {
        val choice = EventChoice(
            label = "L", check = EventCheck(Ability.Str, dc = 0),
            successText = "Success!", successEffects = listOf(RunEffect.GrantCurrency(10)),
            failureText = "Failure!", failureEffects = listOf(RunEffect.GrantCurrency(-10)),
        )
        val result = resolveEventChoice(run(member("m1", hp = 20)), choice, catalog())
        assertEquals("Success!", result.text)
        assertEquals(10, result.run.gold)
    }

    @Test
    fun checkedChoiceWithAnImpossibleDcAlwaysFails() {
        val choice = EventChoice(
            label = "L", check = EventCheck(Ability.Str, dc = 999),
            successText = "Success!", successEffects = listOf(RunEffect.GrantCurrency(10)),
            failureText = "Failure!", failureEffects = listOf(RunEffect.GrantCurrency(-10)),
        )
        val result = resolveEventChoice(run(member("m1", hp = 20)), choice, catalog())
        assertEquals("Failure!", result.text)
        assertEquals(-10, result.run.gold)
    }

    @Test
    fun checkedChoiceCanBeOneSidedWithTheOtherBranchEmpty() {
        val choice = EventChoice(label = "L", check = EventCheck(Ability.Str, dc = 999), successEffects = listOf(RunEffect.GrantCurrency(10)))
        val result = resolveEventChoice(run(member("m1", hp = 20)), choice, catalog())
        assertEquals(0, result.run.gold, "the failure branch was left empty on purpose — nothing should happen")
    }

    @Test
    fun checkedChoiceConsumesExactlyOneRngRoll() {
        val before = run(member("m1", hp = 20))
        val choice = EventChoice(label = "L", check = EventCheck(Ability.Str, dc = 10))
        val result = resolveEventChoice(before, choice, catalog())
        assertEquals(before.rng.calls + 1, result.run.rng.calls)
    }

    @Test
    fun checkedChoicePicksThePartysBestScoringMember() {
        val strongHero = hero.copy(id = ArchetypeId("strong"), abilities = AbilityScores(20, 10, 10, 10, 10, 10))
        val cat = Catalog(archetypes = mapOf(hero.id to hero, strongHero.id to strongHero))
        val weak = member("weak", hp = 20)
        val strong = weak.copy(memberId = MemberId("strong"), archetype = strongHero.id)
        val before = run(weak, strong)
        val dc = 12
        val choice = EventChoice(
            label = "L", check = EventCheck(Ability.Str, dc),
            successEffects = listOf(RunEffect.GrantCurrency(1)), failureEffects = listOf(RunEffect.GrantCurrency(-1)),
        )

        val result = resolveEventChoice(before, choice, cat)

        val (_, expectedRoll) = before.rng.d20()
        val expectedSuccess = expectedRoll + abilityModifier(20) >= dc
        assertEquals(if (expectedSuccess) 1 else -1, result.run.gold, "expected the STR-20 member's modifier to decide the roll, not the STR-10 member's")
    }
}
