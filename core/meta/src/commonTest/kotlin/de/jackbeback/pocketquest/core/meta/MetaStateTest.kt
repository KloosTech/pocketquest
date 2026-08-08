package de.jackbeback.pocketquest.core.meta

import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.Slot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json

private fun championRecord(id: String, status: ChampionStatus = ChampionStatus.Available) = ChampionRecord(
    id = ChampionId(id), name = id, archetype = ArchetypeId("fighter"), status = status,
)

class MetaStateTest {

    @Test
    fun metaStateRoundTripsThroughJson() {
        val champion = championRecord("champ1").copy(
            equipment = Equipment(mapOf(Slot.MainHand to ItemInstance(ItemId("sword"), enchantment = 1))),
        )
        val state = MetaState(roster = mapOf(champion.id to champion), bank = 42, unlocks = setOf(Unlock.PartyMode))
        val encoded = json.encodeToString(MetaState.serializer(), state)
        val decoded = json.decodeFromString(MetaState.serializer(), encoded)
        assertEquals(state, decoded)
    }

    @Test
    fun defaultMetaStateHasNoViolations() {
        assertEquals(emptyList(), checkMetaInvariants(MetaState()))
    }

    @Test
    fun negativeBankIsAViolation() {
        val violations = checkMetaInvariants(MetaState(bank = -1))
        assertTrue(violations.single().contains("bank -1 is negative"))
    }

    @Test
    fun rosterKeyMismatchIsAViolation() {
        val champion = championRecord("champ1")
        val state = MetaState(roster = mapOf(ChampionId("wrongKey") to champion))
        val violations = checkMetaInvariants(state)
        assertTrue(violations.single().contains("roster key wrongKey maps to a record with mismatched id champ1"))
    }

    @Test
    fun revokingAnUnlockIsAViolation() {
        val before = MetaState(unlocks = setOf(Unlock.PartyMode))
        val after = MetaState(unlocks = emptySet())
        val violations = checkUnlockMonotonicity(before, after)
        assertTrue(violations.single().contains("PartyMode"))
    }

    @Test
    fun grantingAnUnlockIsNeverAViolation() {
        val before = MetaState(unlocks = emptySet())
        val after = MetaState(unlocks = setOf(Unlock.PartyMode))
        assertEquals(emptyList(), checkUnlockMonotonicity(before, after))
    }
}
