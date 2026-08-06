package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Cost
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogValidatorTest {

    private val meleeTargeting = Targeting(mode = TargetMode.SingleEntity, range = Range.Melee, shape = Shape.Single)

    private fun fighter() = Archetype(
        id = ArchetypeId("fighter"), name = "Fighter",
        abilities = AbilityScores(10, 10, 10, 10, 10, 10),
        baseMaxHp = 20, baseAc = 14, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 0,
        actions = listOf(ActionId("strike")),
    )

    private fun strikeAction() = ActionDef(
        id = ActionId("strike"), name = "Strike",
        cost = Cost(action = ActionCost.Main),
        targeting = meleeTargeting,
        effects = emptyList(),
    )

    @Test
    fun validCatalogPassesWithoutThrowing() {
        val catalog = Catalog(
            archetypes = mapOf(ArchetypeId("fighter") to fighter()),
            actions = mapOf(ActionId("strike") to strikeAction()),
        )
        CatalogValidator.validate(catalog)
    }

    @Test
    fun archetypeReferencingAnUndefinedActionFailsValidation() {
        val catalog = Catalog(archetypes = mapOf(ArchetypeId("fighter") to fighter()))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown action 'strike'"))
    }

    @Test
    fun applyStatusReferencingAnUndefinedStatusFailsValidation() {
        val poison = EffectTemplate.ApplyStatus(target = Ref.EachTarget, status = StatusId("poisoned"), expiry = Expiry.Permanent)
        val action = strikeAction().copy(effects = listOf(poison))
        val catalog = Catalog(actions = mapOf(action.id to action))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown status 'poisoned'"))
    }

    @Test
    fun applyStatusNestedInsideARollSaveOnFailIsStillChecked() {
        val poison = EffectTemplate.ApplyStatus(target = Ref.EachTarget, status = StatusId("poisoned"), expiry = Expiry.Permanent)
        val save = EffectTemplate.RollSave(target = Ref.EachTarget, ability = Ability.Con, dc = 12, onFail = listOf(poison))
        val action = strikeAction().copy(effects = listOf(save))
        val catalog = Catalog(actions = mapOf(action.id to action))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("onFail references unknown status 'poisoned'"))
    }

    @Test
    fun actionCostingUnknownItemChargesFailsValidation() {
        val action = strikeAction().copy(cost = Cost(action = ActionCost.Main, charges = ItemId("wand")))
        val catalog = Catalog(actions = mapOf(action.id to action))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown item charges 'wand'"))
    }

    @Test
    fun actionFilteringOnUnknownStatusFailsValidation() {
        val action = strikeAction().copy(
            targeting = meleeTargeting.copy(filter = TargetFilter(hasStatus = StatusId("prone-marker"))),
        )
        val catalog = Catalog(actions = mapOf(action.id to action))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown status 'prone-marker'"))
    }

    @Test
    fun statusOnTurnStartReferencingAnUndefinedStatusFailsValidation() {
        val stack = EffectTemplate.ApplyStatus(target = Ref.Caster, status = StatusId("stacked"), expiry = Expiry.Permanent)
        val status = StatusDef(id = StatusId("burning"), name = "Burning", stackPolicy = StackPolicy.Independent, onTurnStart = listOf(stack))
        val catalog = Catalog(statuses = mapOf(status.id to status))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("status 'burning'.onTurnStart references unknown status 'stacked'"))
    }

    @Test
    fun everyProblemIsReportedNotJustTheFirst() {
        val catalog = Catalog(
            archetypes = mapOf(
                ArchetypeId("fighter") to fighter(),
                ArchetypeId("mage") to fighter().copy(id = ArchetypeId("mage"), actions = listOf(ActionId("firebolt"))),
            ),
        )
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.size == 2, "expected 2 problems, got: ${e.problems}")
    }
}
