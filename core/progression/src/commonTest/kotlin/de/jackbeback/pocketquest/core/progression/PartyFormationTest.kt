package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionRecord
import de.jackbeback.pocketquest.core.meta.ChampionStatus
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.meta.Unlock
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.run.MemberId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyFormationTest {

    private val hero = Archetype(
        id = ArchetypeId("hero"), name = "Hero",
        abilities = AbilityScores(12, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 5,
    )

    private val cat = Catalog(archetypes = mapOf(hero.id to hero))

    private fun record(id: String, status: ChampionStatus = ChampionStatus.Available) =
        ChampionRecord(id = ChampionId(id), name = id, archetype = hero.id, status = status)

    @Test
    fun maxPartySizeIsOneWithoutPartyModeUnlocked() {
        assertEquals(1, maxPartySize(MetaState()))
    }

    @Test
    fun maxPartySizeIsThreeOncePartyModeIsUnlocked() {
        assertEquals(3, maxPartySize(MetaState(unlocks = setOf(Unlock.PartyMode))))
    }

    @Test
    fun createChampionAddsAnOnRunRecord() {
        val meta = createChampion(MetaState(), ChampionId("m1"), "Lyra", hero.id)
        val record = meta.roster.getValue(ChampionId("m1"))
        assertEquals(ChampionStatus.OnRun, record.status)
        assertEquals("Lyra", record.name)
    }

    @Test
    fun createChampionRejectsAnAlreadyExistingId() {
        val meta = createChampion(MetaState(), ChampionId("m1"), "Lyra", hero.id)
        assertFails { createChampion(meta, ChampionId("m1"), "Kael", hero.id) }
    }

    @Test
    fun formPartyMarksPickedChampionsOnRunAndBuildsFullHealthMembers() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1")), unlocks = setOf(Unlock.PartyMode))
        val result = formParty(meta, listOf(ChampionId("m1")), cat)
        check(result is PartyFormationResult.Formed)
        assertEquals(ChampionStatus.OnRun, result.meta.roster.getValue(ChampionId("m1")).status)
        val member = result.party.single()
        assertEquals(MemberId("m1"), member.memberId)
        assertEquals(hero.baseMaxHp, member.hp)
        assertEquals(hero.baseMaxMana, member.mana)
    }

    @Test
    fun formPartyRejectsExceedingMaxPartySize() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1"), ChampionId("m2") to record("m2")))
        val result = formParty(meta, listOf(ChampionId("m1"), ChampionId("m2")), cat) // no PartyMode -> max 1
        check(result is PartyFormationResult.Rejected)
        assertTrue(result.reasons.any { it is PartyFormationRejection.TooManyChampions })
    }

    @Test
    fun formPartyRejectsAnEmptySelection() {
        val result = formParty(MetaState(), emptyList(), cat)
        check(result is PartyFormationResult.Rejected)
        assertTrue(result.reasons.any { it is PartyFormationRejection.EmptyParty })
    }

    @Test
    fun formPartyRejectsAChampionThatIsNotAvailable() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1", ChampionStatus.OnMission)))
        val result = formParty(meta, listOf(ChampionId("m1")), cat)
        check(result is PartyFormationResult.Rejected)
        assertTrue(result.reasons.any { it is PartyFormationRejection.ChampionNotAvailable })
    }

    @Test
    fun formPartyRejectionDoesNotMutateTheRoster() {
        val meta = MetaState(roster = mapOf(ChampionId("m1") to record("m1", ChampionStatus.OnMission)))
        formParty(meta, listOf(ChampionId("m1")), cat)
        assertFalse(meta.roster.getValue(ChampionId("m1")).status == ChampionStatus.OnRun)
    }
}
