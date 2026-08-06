package de.jackbeback.pocketquest.core.rules.resolver

import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcentrationTest {

    private fun scenarioWithLinkedStatus(concentratorConMod: Int = 0) = scenario {
        archetype("caster") { hp = 50; abilities(con = 10 + concentratorConMod * 2) }
        archetype("ally") { hp = 20 }
        entity("caster") { archetype("caster"); at(0, 0); hp(50) }
        entity("ally") { archetype("ally"); at(1, 0); hp(20) }
        statusDef("bless") {}
    }

    private fun linked(state: de.jackbeback.pocketquest.core.model.GameState, casterId: de.jackbeback.pocketquest.core.model.EntityId, allyId: de.jackbeback.pocketquest.core.model.EntityId, link: LinkId): de.jackbeback.pocketquest.core.model.GameState =
        state.copy(
            entities = state.entities.map {
                when (it.id) {
                    casterId -> it.copy(concentrating = link)
                    allyId -> it.copy(statuses = it.statuses + ActiveStatus(StatusId("bless"), sourceId = casterId, linkId = link, expiry = Expiry.OnConcentrationLost, appliedAtVersion = 0))
                    else -> it
                }
            },
        )

    // --- StartConcentration ---

    @Test
    fun startConcentrationSetsTheLinkAndEmitsTheEvent() {
        val s = scenarioWithLinkedStatus()
        val link = LinkId(1)
        val out = applyEffect(s.state, Effect.StartConcentration(s.id("caster"), link), emptyMap(), s.catalog)
        assertEquals(link, out.state.byId.getValue(s.id("caster")).concentrating)
        assertEquals(listOf(GameEvent.ConcentrationStarted(s.id("caster"), link)), out.events)
    }

    @Test
    fun startingANewConcentrationEndsThePreviousOneFirst() {
        val s = scenarioWithLinkedStatus()
        val oldLink = LinkId(1)
        val newLink = LinkId(2)
        val state = linked(s.state, s.id("caster"), s.id("ally"), oldLink)

        val out = applyEffect(state, Effect.StartConcentration(s.id("caster"), newLink), emptyMap(), s.catalog)

        assertEquals(newLink, out.state.byId.getValue(s.id("caster")).concentrating)
        assertTrue(out.state.byId.getValue(s.id("ally")).statuses.none { it.linkId == oldLink }, "the old link's statuses must be stripped")
        assertTrue(out.events.any { it == GameEvent.ConcentrationBroken(s.id("caster"), oldLink) })
        assertTrue(out.events.any { it == GameEvent.ConcentrationStarted(s.id("caster"), newLink) })
    }

    // --- DealDamage auto-triggering a concentration check ---

    @Test
    fun damageOnAConcentratingEntityTriggersAConcentrationCheckWithCorrectDc() {
        val s = scenarioWithLinkedStatus()
        val link = LinkId(1)
        val state = linked(s.state, s.id("caster"), s.id("ally"), link)

        val out = applyEffect(state, Effect.DealDamage(s.id("caster"), amount = 12, type = DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(listOf(Effect.ConcentrationCheck(s.id("caster"), dc = 10)), out.spawn) // max(10, 12/2) = 10
    }

    @Test
    fun highDamageRaisesTheConcentrationDcAboveTheFloor() {
        val s = scenarioWithLinkedStatus()
        val link = LinkId(1)
        val state = linked(s.state, s.id("caster"), s.id("ally"), link)

        val out = applyEffect(state, Effect.DealDamage(s.id("caster"), amount = 30, type = DamageType.Fire), emptyMap(), s.catalog)
        assertEquals(listOf(Effect.ConcentrationCheck(s.id("caster"), dc = 15)), out.spawn) // max(10, 30/2) = 15
    }

    @Test
    fun damageOnANonConcentratingEntityNeverSpawnsAConcentrationCheck() {
        val s = scenarioWithLinkedStatus()
        val out = applyEffect(s.state, Effect.DealDamage(s.id("caster"), amount = 12, type = DamageType.Fire), emptyMap(), s.catalog)
        assertTrue(out.spawn.isEmpty())
    }

    @Test
    fun lethalDamageBreaksConcentrationUnconditionallyWithoutRollingASave() {
        val s = scenarioWithLinkedStatus()
        val link = LinkId(1)
        val state = linked(s.state.copy(entities = s.state.entities.map { if (it.id == s.id("caster")) it.copy(health = it.health!!.copy(current = 5)) else it }), s.id("caster"), s.id("ally"), link)

        val out = applyEffect(state, Effect.DealDamage(s.id("caster"), amount = 999, type = DamageType.Fire), emptyMap(), s.catalog)
        assertTrue(out.spawn.isEmpty(), "death breaks concentration directly, no ConcentrationCheck roll")
        assertNull(out.state.byId.getValue(s.id("caster")).concentrating)
        assertTrue(out.state.byId.getValue(s.id("ally")).statuses.none { it.linkId == link })
        assertTrue(out.events.any { it == GameEvent.ConcentrationBroken(s.id("caster"), link) })
    }

    // --- ConcentrationCheck itself ---

    @Test
    fun concentrationCheckExpectedModeSucceedsWhenAverageRollMeetsDc() {
        val s = scenarioWithLinkedStatus(concentratorConMod = 3) // CON 16, mod +3; 10.5+3=13.5 >= dc 10
        val link = LinkId(1)
        val state = linked(s.state, s.id("caster"), s.id("ally"), link)

        val out = applyEffect(state, Effect.ConcentrationCheck(s.id("caster"), dc = 10), emptyMap(), s.catalog, RngMode.Expected)
        val rolled = out.events.single() as GameEvent.ConcentrationCheckRolled
        assertTrue(rolled.success)
        assertEquals(link, out.state.byId.getValue(s.id("caster")).concentrating, "successful check must not break concentration")
        assertTrue(out.state.byId.getValue(s.id("ally")).statuses.any { it.linkId == link })
    }

    @Test
    fun concentrationCheckFailureBreaksConcentrationAndStripsLinkedStatusesFromOtherEntities() {
        val s = scenarioWithLinkedStatus(concentratorConMod = -4) // CON 2, mod -4; 10.5-4=6.5 < dc 20
        val link = LinkId(1)
        val state = linked(s.state, s.id("caster"), s.id("ally"), link)

        val out = applyEffect(state, Effect.ConcentrationCheck(s.id("caster"), dc = 20), emptyMap(), s.catalog, RngMode.Expected)
        val rolled = out.events.single { it is GameEvent.ConcentrationCheckRolled } as GameEvent.ConcentrationCheckRolled
        assertTrue(!rolled.success)
        assertNull(out.state.byId.getValue(s.id("caster")).concentrating)
        assertTrue(out.state.byId.getValue(s.id("ally")).statuses.none { it.linkId == link }, "bless on the ally must be stripped even though the ally didn't take damage")
        assertTrue(out.events.any { it == GameEvent.StatusExpired(s.id("ally"), StatusId("bless")) })
        assertTrue(out.events.any { it == GameEvent.ConcentrationBroken(s.id("caster"), link) })
    }

    @Test
    fun concentrationCheckOnAlreadyBrokenLinkIsANoOp() {
        val s = scenarioWithLinkedStatus()
        val out = applyEffect(s.state, Effect.ConcentrationCheck(s.id("caster"), dc = 10), emptyMap(), s.catalog)
        assertEquals(s.state, out.state)
        assertTrue(out.events.isEmpty())
    }
}
