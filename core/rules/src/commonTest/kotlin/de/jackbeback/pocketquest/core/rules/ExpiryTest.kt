package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpiryTest {

    private val lyra = EntityId(0)
    private val caster = EntityId(1)

    @Test
    fun endOfTurnOfFiresAtThatEntitysEndOfTurnNotRoundEnd() {
        val expiry = Expiry.EndOfTurnOf(lyra, round = 3)
        assertTrue(expiry.matches(TurnMoment.EndOfTurn(lyra, round = 3)))
        assertFalse(expiry.matches(TurnMoment.EndOfTurn(caster, round = 3)), "wrong entity")
        assertFalse(expiry.matches(TurnMoment.EndOfRound(round = 3)), "end-of-turn is not end-of-round")
    }

    @Test
    fun startOfTurnOfDoesNotFireOnAStaleRound() {
        val expiry = Expiry.StartOfTurnOf(lyra, round = 3)
        assertTrue(expiry.matches(TurnMoment.StartOfTurn(lyra, round = 3)))
        assertFalse(expiry.matches(TurnMoment.StartOfTurn(lyra, round = 4)), "round 3 expiry must not re-fire at round 4")
    }

    @Test
    fun permanentNeverMatchesAnyTurnMoment() {
        assertFalse(Expiry.Permanent.matches(TurnMoment.EndOfTurn(lyra, 1)))
        assertFalse(Expiry.Permanent.matches(TurnMoment.StartOfTurn(lyra, 1)))
        assertFalse(Expiry.Permanent.matches(TurnMoment.EndOfRound(1)))
    }

    @Test
    fun onConcentrationLostFiresForEveryStatusSharingLinkIdIncludingOtherEntities() {
        val link = LinkId(42)
        val other = LinkId(99)
        val s = scenario {
            archetype("dummy") { hp = 10 }
            entity("caster") { archetype("dummy"); at(0, 0) }
            entity("ally") { archetype("dummy"); at(1, 0) }
            entity("bystander") { archetype("dummy"); at(2, 0) }
        }

        val casterId = s.id("caster")
        val allyId = s.id("ally")
        val bystanderId = s.id("bystander")

        val withStatuses = s.state.copy(
            entities = s.state.entities.map { e ->
                when (e.id) {
                    casterId -> e.copy(statuses = listOf(linkedStatus("bless", link, casterId)))
                    allyId -> e.copy(statuses = listOf(linkedStatus("bless", link, casterId)))
                    bystanderId -> e.copy(statuses = listOf(linkedStatus("unrelated", other, casterId)))
                    else -> e
                }
            },
        )

        val linked = withStatuses.statusesLinkedTo(link)
        assertEquals(setOf(casterId, allyId), linked.map { it.first }.toSet())
        assertTrue(linked.none { it.first == bystanderId })
    }

    private fun linkedStatus(name: String, link: LinkId, source: EntityId) = ActiveStatus(
        def = StatusId(name),
        sourceId = source,
        linkId = link,
        expiry = Expiry.OnConcentrationLost,
        appliedAtVersion = 0,
    )
}
