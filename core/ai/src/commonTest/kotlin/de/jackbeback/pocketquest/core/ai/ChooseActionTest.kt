package de.jackbeback.pocketquest.core.ai

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Cost
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.DiceSpec
import de.jackbeback.pocketquest.core.model.EffectTemplate
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.Flag
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Ref
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val heroId = EntityId(0)
private val enemyAId = EntityId(1)
private val enemyBId = EntityId(2)

private fun entity(id: EntityId, pos: GridPos, hp: Int, faction: Faction, archetype: String, mana: Int = 0) = Entity(
    id = id, archetype = ArchetypeId(archetype), pos = pos,
    health = Health(hp), resources = Resources(ap = 2, mana = mana),
    actor = Actor(faction, if (faction == Faction.Player) Controller.Human else Controller.Ai(de.jackbeback.pocketquest.core.model.AiProfileId("default"))),
)

private fun state(vararg entities: Entity) = GameState(
    entities = entities.toList(),
    map = BattleMap(10, 10),
    turn = TurnState(round = 1, order = entities.map { it.id }, activeIndex = 0, phase = TurnPhase.Main),
    rng = RngState(seed = 1, calls = 0),
)

private val meleeAutoHit = Targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, TargetFilter(faction = Faction.Enemy))
private val meleeHitsPlayers = Targeting(TargetMode.SingleEntity, Range.Melee, Shape.Single, TargetFilter(faction = Faction.Player))

private val tauntedDef = StatusDef(StatusId("taunted"), "Taunted", StackPolicy.Independent, modifiers = listOf(Modifier.Grant(Flag.Taunted)))

/** [Entity.copy] on the shared `entity()` helper — a taunted-by-[source] status, no expiry (permanent for the test's purposes). */
private fun Entity.taunted(source: EntityId) = copy(
    statuses = statuses + ActiveStatus(def = tauntedDef.id, sourceId = source, linkId = null, expiry = Expiry.Permanent, appliedAtVersion = 0),
)

/** attackBonus=10 against Expected's fixed d20=10.5 always beats any reasonable AC; 1d1 dice always roll exactly 1 — deterministic in both Live and Expected mode. */
private fun strikeAction(id: String, bonus: Int = 10, flatDamage: Int = 5, targeting: Targeting = meleeAutoHit) = ActionDef(
    id = ActionId(id), name = id,
    cost = Cost(action = ActionCost.Main),
    targeting = targeting,
    effects = listOf(
        EffectTemplate.RollAttack(Ref.Caster, Ref.EachTarget, attackBonus = bonus, damage = DiceSpec(1, 1, flatDamage), damageType = DamageType.Slashing),
    ),
)

private fun baseCatalog(vararg actions: ActionDef, archetypes: Map<ArchetypeId, Archetype> = emptyMap()) = Catalog(
    archetypes = archetypes,
    actions = actions.associateBy { it.id },
)

private fun archetype(id: String, actions: List<String>) = Archetype(
    id = ArchetypeId(id), name = id,
    abilities = AbilityScores(10, 10, 10, 10, 10, 10),
    baseMaxHp = 10, baseAc = 12, speedTiles = 6, baseMaxAp = 2, baseMaxMana = 10,
    actions = actions.map { ActionId(it) },
)

class ChooseActionTest {

    @Test
    fun picksTheOnlyLegalActionAndTarget() {
        val cat = baseCatalog(strikeAction("strike"), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "hero"),
        )
        val decision = chooseAction(s, heroId, cat)
        assertEquals(ActionId("strike"), decision?.actionId)
        assertEquals(listOf(enemyAId), decision?.ctx?.targets)
    }

    @Test
    fun returnsNullWhenArchetypeHasNoActions() {
        val cat = baseCatalog(archetypes = mapOf(ArchetypeId("passive") to archetype("passive", emptyList())))
        val s = state(entity(heroId, GridPos(0, 0), 20, Faction.Player, "passive"))
        assertNull(chooseAction(s, heroId, cat))
    }

    @Test
    fun returnsNullWhenTheOnlyTargetIsOutOfRange() {
        val cat = baseCatalog(strikeAction("strike"), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
            entity(enemyAId, GridPos(9, 9), 10, Faction.Enemy, "hero"), // far outside melee range
        )
        assertNull(chooseAction(s, heroId, cat))
    }

    @Test
    fun prefersAGuaranteedHitTargetOverAGuaranteedMiss() {
        val cat = baseCatalog(
            strikeAction("strike"),
            archetypes = mapOf(
                ArchetypeId("hero") to archetype("hero", listOf("strike")),
                ArchetypeId("squishy") to archetype("squishy", emptyList()).copy(baseAc = 1),
                ArchetypeId("armored") to archetype("armored", emptyList()).copy(baseAc = 40),
            ),
        )
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "armored"), // Expected 10.5+10 < 40 -> miss
            entity(enemyBId, GridPos(0, 1), 10, Faction.Enemy, "squishy"), // Expected 10.5+10 > 1 -> hit
        )
        val decision = chooseAction(s, heroId, cat)
        assertEquals(listOf(enemyBId), decision?.ctx?.targets, "should target the enemy it can actually hit, not the one it can't")
        assertTrue((decision?.score ?: 0) > 0)
    }

    @Test
    fun prefersTheHigherScoringOfTwoAvailableActions() {
        val cat = baseCatalog(
            strikeAction("weakStrike", flatDamage = 2),
            strikeAction("strongStrike", flatDamage = 11),
            archetypes = mapOf(ArchetypeId("duelist") to archetype("duelist", listOf("weakStrike", "strongStrike"))),
        )
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "duelist"),
            entity(enemyAId, GridPos(1, 0), 20, Faction.Enemy, "duelist"),
        )
        val decision = chooseAction(s, heroId, cat)
        assertEquals(ActionId("strongStrike"), decision?.actionId)
    }

    @Test
    fun buildsASelfOnlyContextWhenThatIsTheOnlyOption() {
        val brace = ActionDef(
            id = ActionId("brace"), name = "brace",
            cost = Cost(action = ActionCost.Main),
            targeting = Targeting(TargetMode.SelfOnly, Range.SelfRange, Shape.Single),
            effects = listOf(EffectTemplate.ApplyStatus(Ref.Caster, StatusId("braced"), expiry = Expiry.Permanent)),
        )
        val cat = Catalog(
            archetypes = mapOf(ArchetypeId("brave") to archetype("brave", listOf("brace"))),
            actions = mapOf(brace.id to brace),
            statuses = mapOf(StatusId("braced") to StatusDef(StatusId("braced"), "Braced", StackPolicy.Independent)),
        )
        val s = state(entity(heroId, GridPos(0, 0), 20, Faction.Player, "brave"))
        val decision = chooseAction(s, heroId, cat)
        assertEquals(ActionId("brace"), decision?.actionId)
        assertEquals(listOf(heroId), decision?.ctx?.targets)
        assertEquals(GridPos(0, 0), decision?.ctx?.point)
    }

    @Test
    fun tauntOverridesTheNormalScoringAndForcesTheTaunterAsTarget() {
        val cat = Catalog(
            archetypes = mapOf(ArchetypeId("goblin") to archetype("goblin", listOf("strike"))),
            actions = mapOf(ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            statuses = mapOf(tauntedDef.id to tauntedDef),
        )
        val goblin = entity(EntityId(3), GridPos(0, 0), 10, Faction.Enemy, "goblin")
        val tank = entity(heroId, GridPos(1, 0), 20, Faction.Player, "goblin") // survives the hit — no Died bonus
        val squishy = entity(enemyAId, GridPos(0, 1), 3, Faction.Player, "goblin") // 3 HP: the strike kills it, +1000 score

        // Baseline, no taunt: the AI goes for the kill, not the tank.
        val untaunted = chooseAction(state(goblin, tank, squishy), goblin.id, cat)
        assertEquals(listOf(squishy.id), untaunted?.ctx?.targets, "without taunt the AI should prefer the kill over the tankier target")

        // Same board, goblin now Taunted by the tank — must attack the tank instead, despite it scoring worse.
        val taunted = chooseAction(state(goblin.taunted(tank.id), tank, squishy), goblin.id, cat)
        assertEquals(listOf(tank.id), taunted?.ctx?.targets, "taunt must override the scoring heuristic entirely")
    }

    @Test
    fun tauntDoesNotForceAnUnreachableTaunterAndFallsBackToNormalTargeting() {
        val cat = Catalog(
            archetypes = mapOf(ArchetypeId("goblin") to archetype("goblin", listOf("strike"))),
            actions = mapOf(ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            statuses = mapOf(tauntedDef.id to tauntedDef),
        )
        val goblin = entity(EntityId(3), GridPos(0, 0), 10, Faction.Enemy, "goblin").taunted(heroId)
        val tank = entity(heroId, GridPos(9, 9), 20, Faction.Player, "goblin") // the taunter, but out of melee range
        val squishy = entity(enemyAId, GridPos(0, 1), 10, Faction.Player, "goblin") // in range — the only reachable option

        val decision = chooseAction(state(goblin, tank, squishy), goblin.id, cat)
        assertEquals(listOf(squishy.id), decision?.ctx?.targets, "the taunter is unreachable for this action, so taunt must not force an impossible target")
    }

    @Test
    fun returnsNullWhenTheOnlyActionCostsMoreManaThanAvailable() {
        val pricey = strikeAction("pricey").let { it.copy(cost = it.cost.copy(mana = 5)) }
        val cat = baseCatalog(pricey, archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("pricey"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero", mana = 0),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "hero"),
        )
        assertNull(chooseAction(s, heroId, cat))
    }
}
