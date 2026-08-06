package de.jackbeback.pocketquest.core.rules

import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertTrue

class InvariantsTest {

    private fun validScenario() = scenario {
        map(10, 10)
        seed(1)
        archetype("goblin") { hp = 7; ac = 15; ap = 2 }
        entity("gobA") { archetype("goblin"); at(1, 1); hp(7); ap(2) }
        entity("gobB") { archetype("goblin"); at(2, 1); hp(7); ap(2) }
        initiative("gobA", "gobB")
    }

    @Test
    fun validScenarioHasNoViolations() {
        val s = validScenario()
        assertTrue(checkInvariants(s.state, s.catalog).isEmpty())
    }

    @Test
    fun detectsOrderReferencingMissingEntity() {
        val s = validScenario()
        val broken = s.state.copy(turn = s.state.turn.copy(order = s.state.turn.order + EntityId(999)))
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("turn.order") })
    }

    @Test
    fun detectsTwoEntitiesSharingATile() {
        val s = validScenario()
        val gobB = s.entity("gobB")
        val broken = s.state.copy(
            entities = s.state.entities.map { if (it.id == gobB.id) it.copy(pos = s.entity("gobA").pos) else it },
        )
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("both occupy") })
    }

    @Test
    fun detectsPositionOutsideMapBounds() {
        val s = validScenario()
        val gobA = s.entity("gobA")
        val broken = s.state.copy(
            entities = s.state.entities.map { if (it.id == gobA.id) it.copy(pos = GridPos(99, 99)) else it },
        )
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("walkable") })
    }

    @Test
    fun detectsHealthOutsideDerivedBounds() {
        val s = validScenario()
        val gobA = s.entity("gobA")
        val broken = s.state.copy(
            entities = s.state.entities.map { if (it.id == gobA.id) it.copy(health = Health(current = 999)) else it },
        )
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("health") })
    }

    @Test
    fun detectsResourcesOutsideDerivedBounds() {
        val s = validScenario()
        val gobA = s.entity("gobA")
        val broken = s.state.copy(
            entities = s.state.entities.map { if (it.id == gobA.id) it.copy(resources = Resources(ap = 999, mana = 0)) else it },
        )
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("ap") })
    }

    @Test
    fun detectsStatusSourcedFromMissingEntity() {
        val s = validScenario()
        val gobA = s.entity("gobA")
        val danglingStatus = ActiveStatus(
            def = StatusId("marked"),
            sourceId = EntityId(999),
            linkId = null,
            expiry = Expiry.Permanent,
            appliedAtVersion = 0,
        )
        val broken = s.state.copy(
            entities = s.state.entities.map { if (it.id == gobA.id) it.copy(statuses = listOf(danglingStatus)) else it },
        )
        val markedDef = StatusId("marked") to StatusDef(StatusId("marked"), "marked", StackPolicy.Refresh)
        val catalogWithMarked = s.catalog.copy(statuses = s.catalog.statuses + markedDef)
        val violations = checkInvariants(broken, catalogWithMarked)
        assertTrue(violations.any { it.contains("missing entity") })
    }

    @Test
    fun detectsActiveIndexOutsideOrderBounds() {
        val s = validScenario()
        val broken = s.state.copy(turn = s.state.turn.copy(activeIndex = 5))
        val violations = checkInvariants(broken, s.catalog)
        assertTrue(violations.any { it.contains("activeIndex") })
    }
}
