package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.rules.fixture.scenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** doc17-engine-gaps.md 1.6: Archetype.actions stops being the only action source once features exist. */
class GrantedActionsTest {

    @Test
    fun anEntityWithNoFeaturesGrantsNoExtraActions() {
        val s = scenario {
            archetype("dummy") { hp = 10; actions("strike") }
            actionDef("strike") { cost(ActionCost.Main) }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10) }
        }
        assertEquals(emptyList<ActionId>(), s.entity("hero").grantedActions(s.catalog))
        assertEquals(listOf(ActionId("strike")), s.entity("hero").allActions(s.catalog))
    }

    @Test
    fun aFeatureGrantsItsActionsOnTopOfTheArchetypes() {
        val s = scenario {
            archetype("dummy") { hp = 10; actions("strike") }
            actionDef("strike") { cost(ActionCost.Main) }
            actionDef("cleave") { cost(ActionCost.Main) }
            featureDef("cleaveTraining") { grantsAction("cleave") }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); feature("cleaveTraining") }
        }
        assertEquals(listOf(ActionId("cleave")), s.entity("hero").grantedActions(s.catalog))
        assertEquals(listOf(ActionId("strike"), ActionId("cleave")), s.entity("hero").allActions(s.catalog))
    }

    @Test
    fun multipleFeaturesEachContributeTheirOwnGrants() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            actionDef("cleave") { cost(ActionCost.Main) }
            actionDef("parry") { cost(ActionCost.Reaction) }
            featureDef("cleaveTraining") { grantsAction("cleave") }
            featureDef("parryTraining") { grantsAction("parry") }
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); feature("cleaveTraining"); feature("parryTraining") }
        }
        assertEquals(listOf(ActionId("cleave"), ActionId("parry")), s.entity("hero").grantedActions(s.catalog))
    }

    @Test
    fun aFeatureWithNoGrantedActionsOnlyContributesModifiers() {
        val s = scenario {
            archetype("dummy") { hp = 10 }
            featureDef("toughening") {} // modifiers only, no grantsAction call
            entity("hero") { archetype("dummy"); at(0, 0); hp(10); feature("toughening") }
        }
        assertEquals(emptyList<ActionId>(), s.entity("hero").grantedActions(s.catalog))
    }
}
