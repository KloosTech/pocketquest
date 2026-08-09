package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Ability
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.AiCondition
import de.jackbeback.pocketquest.core.model.AiGoal
import de.jackbeback.pocketquest.core.model.AiProfileDef
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.AiTargetPreference
import de.jackbeback.pocketquest.core.model.AiTier
import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Cost
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.EncounterId
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EnemySpawn
import de.jackbeback.pocketquest.core.model.EventChoice
import de.jackbeback.pocketquest.core.model.EventDef
import de.jackbeback.pocketquest.core.model.EventId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.FeatureDef
import de.jackbeback.pocketquest.core.model.FeatureId
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone
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
    fun spawnEntityReferencingAnUndefinedArchetypeFailsValidation() {
        val summon = EffectTemplate.SpawnEntity(archetype = ArchetypeId("imp"), faction = Faction.Enemy, controller = Controller.Ai(AiProfileId("standard")))
        val action = strikeAction().copy(effects = listOf(summon))
        val catalog = Catalog(actions = mapOf(action.id to action))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown archetype 'imp'"))
    }

    @Test
    fun archetypeReferencingAnUndefinedAiProfileFailsValidation() {
        val goblin = fighter().copy(id = ArchetypeId("goblin"), aiProfile = AiProfileId("berserker"))
        val catalog = Catalog(archetypes = mapOf(goblin.id to goblin), actions = mapOf(ActionId("strike") to strikeAction()))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown AI profile 'berserker'"))
    }

    @Test
    fun anArchetypeLeftOnTheDefaultAiProfileNeverFailsEvenWithNoProfilesAuthored() {
        // fighter() never sets aiProfile explicitly — stays at the zero-config "standard" default,
        // which must always be valid even though `catalog.aiProfiles` is empty here.
        val catalog = Catalog(archetypes = mapOf(ArchetypeId("fighter") to fighter()), actions = mapOf(ActionId("strike") to strikeAction()))
        CatalogValidator.validate(catalog)
    }

    @Test
    fun aiProfileTierCheckingAnUndefinedStatusFailsValidation() {
        val tier = AiTier(condition = AiCondition.HasStatus(StatusId("poisoned")), goal = AiGoal.UseAction(targetPreference = AiTargetPreference.LowestHpPercent))
        val profile = AiProfileDef(AiProfileId("cautious"), "Cautious", tiers = listOf(tier))
        val catalog = Catalog(aiProfiles = mapOf(profile.id to profile))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown status 'poisoned'"))
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
    fun featureGrantingAnUndefinedActionFailsValidation() {
        val feature = FeatureDef(id = FeatureId("cleaveTraining"), name = "Cleave Training", grantsActions = listOf(ActionId("cleave")))
        val catalog = Catalog(features = mapOf(feature.id to feature))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("Feature 'cleaveTraining' grants unknown action 'cleave'"))
    }

    @Test
    fun featureGrantingAKnownActionPassesValidation() {
        val feature = FeatureDef(id = FeatureId("training"), name = "Training", grantsActions = listOf(ActionId("strike")))
        val catalog = Catalog(actions = mapOf(ActionId("strike") to strikeAction()), features = mapOf(feature.id to feature))
        CatalogValidator.validate(catalog)
    }

    @Test
    fun itemGrantingAnUndefinedFeatureFailsValidation() {
        val item = ItemDef(id = ItemId("wandOfSparks"), name = "Wand of Sparks", grantsFeature = FeatureId("sparkTraining"))
        val catalog = Catalog(items = mapOf(item.id to item))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("Item 'wandOfSparks' grants unknown feature 'sparkTraining'"))
    }

    @Test
    fun itemGrantingAKnownFeaturePassesValidation() {
        val feature = FeatureDef(id = FeatureId("sparkTraining"), name = "Spark Training")
        val item = ItemDef(id = ItemId("wandOfSparks"), name = "Wand of Sparks", grantsFeature = feature.id)
        val catalog = Catalog(items = mapOf(item.id to item), features = mapOf(feature.id to feature))
        CatalogValidator.validate(catalog)
    }

    // --- Encounter / Map (docs/16-art-direction.md's Encounter and Map editors) ---

    private fun mapDef(id: String = "room1", enemyTiles: Int = 1) = BattleMapDef(
        id = MapId(id), width = 10, height = 10,
        spawns = listOf(SpawnZone(SpawnRole.Enemy, List(enemyTiles) { GridPos(it, 0) })),
    )

    @Test
    fun encounterReferencingAnUndefinedMapFailsValidation() {
        val encounter = EncounterSpec(id = EncounterId("goblinAmbush"), name = "Goblin Ambush", mapId = MapId("missing"))
        val catalog = Catalog(encounters = mapOf(encounter.id to encounter))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("unknown map 'missing'"))
    }

    @Test
    fun encounterReferencingAnUndefinedArchetypeFailsValidation() {
        val map = mapDef()
        val encounter = EncounterSpec(
            id = EncounterId("goblinAmbush"), name = "Goblin Ambush", mapId = map.id,
            enemies = listOf(EnemySpawn(ArchetypeId("goblin"))),
        )
        val catalog = Catalog(maps = mapOf(map.id to map), encounters = mapOf(encounter.id to encounter))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.any { it.contains("unknown archetype 'goblin'") }, "expected an unknown-archetype problem, got: ${e.problems}")
    }

    @Test
    fun encounterReferencingAnUndefinedLootItemFailsValidation() {
        val map = mapDef()
        val encounter = EncounterSpec(
            id = EncounterId("goblinAmbush"), name = "Goblin Ambush", mapId = map.id,
            loot = listOf(de.jackbeback.pocketquest.core.model.LootEntry(de.jackbeback.pocketquest.core.model.ItemId("potion"))),
        )
        val catalog = Catalog(maps = mapOf(map.id to map), encounters = mapOf(encounter.id to encounter))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.any { it.contains("unknown loot item 'potion'") }, "expected an unknown-loot-item problem, got: ${e.problems}")
    }

    @Test
    fun eventWithNoChoicesFailsValidation() {
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = emptyList())
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(Catalog(events = mapOf(event.id to event))) }
        assertTrue(e.problems.any { it.contains("has 0 choices, must be 1..4") })
    }

    @Test
    fun eventWithMoreThanFourChoicesFailsValidation() {
        val choice = EventChoice(label = "L", outcomeText = "O")
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = List(5) { choice })
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(Catalog(events = mapOf(event.id to event))) }
        assertTrue(e.problems.any { it.contains("has 5 choices, must be 1..4") })
    }

    @Test
    fun eventChoiceGrantingAnUndefinedItemFailsValidation() {
        val choice = EventChoice(label = "L", outcomeText = "O", effects = listOf(de.jackbeback.pocketquest.core.model.RunEffect.GrantItem(ItemId("potion"))))
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = listOf(choice))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(Catalog(events = mapOf(event.id to event))) }
        assertTrue(e.problems.any { it.contains("unknown item 'potion'") }, "expected an unknown-item problem, got: ${e.problems}")
    }

    @Test
    fun eventChoiceForcingAnUndefinedEncounterFailsValidation() {
        val choice = EventChoice(label = "L", outcomeText = "O", effects = listOf(de.jackbeback.pocketquest.core.model.RunEffect.ForceCombat(EncounterId("ambush"))))
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = listOf(choice))
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(Catalog(events = mapOf(event.id to event))) }
        assertTrue(e.problems.any { it.contains("unknown encounter 'ambush'") }, "expected an unknown-encounter problem, got: ${e.problems}")
    }

    @Test
    fun eventWithValidReferencesPassesValidation() {
        val potion = ItemDef(id = ItemId("potion"), name = "Potion")
        val choice = EventChoice(label = "L", outcomeText = "O", effects = listOf(de.jackbeback.pocketquest.core.model.RunEffect.GrantItem(potion.id)))
        val event = EventDef(id = EventId("e1"), title = "T", body = "B", choices = listOf(choice))
        CatalogValidator.validate(Catalog(items = mapOf(potion.id to potion), events = mapOf(event.id to event)))
    }

    @Test
    fun shopStockingAnUndefinedItemFailsValidation() {
        val shop = de.jackbeback.pocketquest.core.model.ShopDef(
            id = de.jackbeback.pocketquest.core.model.ShopId("s1"), act = 1,
            stock = listOf(de.jackbeback.pocketquest.core.model.ShopEntry(ItemId("potion"), price = 10)),
        )
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(Catalog(shops = mapOf(shop.id to shop))) }
        assertTrue(e.problems.any { it.contains("unknown item 'potion'") }, "expected an unknown-item problem, got: ${e.problems}")
    }

    @Test
    fun shopStockingOnlyDefinedItemsPassesValidation() {
        val potion = ItemDef(id = ItemId("potion"), name = "Potion")
        val shop = de.jackbeback.pocketquest.core.model.ShopDef(
            id = de.jackbeback.pocketquest.core.model.ShopId("s1"), act = 1,
            stock = listOf(de.jackbeback.pocketquest.core.model.ShopEntry(potion.id, price = 10)),
        )
        CatalogValidator.validate(Catalog(items = mapOf(potion.id to potion), shops = mapOf(shop.id to shop)))
    }

    @Test
    fun encounterAskingForMoreEnemiesThanTheMapsSpawnZoneHasFailsValidation() {
        val map = mapDef(enemyTiles = 1)
        val encounter = EncounterSpec(
            id = EncounterId("goblinAmbush"), name = "Goblin Ambush", mapId = map.id,
            enemies = listOf(EnemySpawn(ArchetypeId("goblin"), role = SpawnRole.Enemy, count = 3)),
        )
        val catalog = Catalog(
            archetypes = mapOf(ArchetypeId("goblin") to fighter().copy(id = ArchetypeId("goblin"), actions = emptyList())),
            maps = mapOf(map.id to map),
            encounters = mapOf(encounter.id to encounter),
        )
        val e = assertFailsWith<CatalogValidationException> { CatalogValidator.validate(catalog) }
        assertTrue(e.problems.single().contains("needs 3 Enemy spawn tile(s)") && e.problems.single().contains("only 1 are available"))
    }

    @Test
    fun encounterThatFitsItsMapsSpawnZonesPassesValidation() {
        val map = mapDef(enemyTiles = 3)
        val encounter = EncounterSpec(
            id = EncounterId("goblinAmbush"), name = "Goblin Ambush", mapId = map.id,
            enemies = listOf(EnemySpawn(ArchetypeId("goblin"), role = SpawnRole.Enemy, count = 2)),
        )
        val catalog = Catalog(
            archetypes = mapOf(ArchetypeId("goblin") to fighter().copy(id = ArchetypeId("goblin"), actions = emptyList())),
            maps = mapOf(map.id to map),
            encounters = mapOf(encounter.id to encounter),
        )
        CatalogValidator.validate(catalog)
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
