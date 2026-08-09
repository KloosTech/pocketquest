package de.jackbeback.pocketquest.core.ai

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.AiActionCategory
import de.jackbeback.pocketquest.core.model.AiCondition
import de.jackbeback.pocketquest.core.model.AiGoal
import de.jackbeback.pocketquest.core.model.AiProfileDef
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.AiTargetPreference
import de.jackbeback.pocketquest.core.model.AiTier
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.chebyshevDistanceTo
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
import de.jackbeback.pocketquest.core.model.Side
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.model.WallEdge
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
    fun neverAttacksAnAllyEvenWhenItIsTheOnlyLegalTarget() {
        // TargetFilter left at "Any" (faction = null, the model default) — a real content gap found
        // live: an enemy with no true enemy in range but an ally in range would otherwise pick this
        // as its "least bad" legal option since defaultScoredChoice had no floor against negative
        // scores. Passing the turn must beat hurting your own side.
        val cat = baseCatalog(strikeAction("strike", targeting = meleeAutoHit.copy(filter = TargetFilter())), archetypes = mapOf(ArchetypeId("goblin") to archetype("goblin", listOf("strike"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Enemy, "goblin"),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "goblin"),
        )
        assertNull(chooseAction(s, heroId, cat))
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

    // --- docs/21-ai-behavior-spec.md: tiered profiles ---

    private fun healAction(id: String = "heal", amount: Int = 8) = ActionDef(
        id = ActionId(id), name = id,
        cost = Cost(action = ActionCost.Main),
        targeting = Targeting(TargetMode.SingleEntity, Range.Tiles(4), Shape.Single, TargetFilter(faction = Faction.Enemy), requiresLoS = false),
        effects = listOf(EffectTemplate.Heal(Ref.EachTarget, amount)),
    )

    private fun moveAction() = ActionDef(
        id = ActionId("move"), name = "move",
        cost = Cost(action = ActionCost.Movement(6)),
        targeting = Targeting(TargetMode.Path, Range.Tiles(8), Shape.Single, requiresLoS = false),
        effects = emptyList(),
    )

    @Test
    fun aHigherTierHealsAHurtAllyInsteadOfAttacking() {
        val cat = Catalog(
            archetypes = mapOf(
                ArchetypeId("medic") to archetype("medic", listOf("heal", "strike"))
                    .copy(aiProfile = AiProfileId("medic")),
            ),
            actions = mapOf(ActionId("heal") to healAction(), ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            aiProfiles = mapOf(
                AiProfileId("medic") to AiProfileDef(
                    AiProfileId("medic"), "Medic",
                    tiers = listOf(AiTier(AiCondition.AnyAllyHpBelow(50), AiGoal.UseAction(AiActionCategory.Heal, AiTargetPreference.LowestHpPercent))),
                ),
            ),
        )
        val medic = entity(EntityId(3), GridPos(2, 2), 10, Faction.Enemy, "medic")
        val hurtAlly = entity(EntityId(4), GridPos(3, 2), 3, Faction.Enemy, "medic") // 30% HP — below the 50% threshold
        val player = entity(heroId, GridPos(2, 3), 10, Faction.Player, "medic")

        val decision = chooseAction(state(medic, hurtAlly, player), medic.id, cat)
        assertEquals(ActionId("heal"), decision?.actionId, "the hurt-ally tier must win over the default attack-scoring fallback")
        assertEquals(listOf(hurtAlly.id), decision?.ctx?.targets)
    }

    @Test
    fun aTierThatCantResolveFallsThroughToTheNextTierNotToNothing() {
        val cat = Catalog(
            archetypes = mapOf(
                ArchetypeId("fighter") to archetype("fighter", listOf("strike")) // no heal action known — tier 1's goal can never resolve
                    .copy(aiProfile = AiProfileId("brawler")),
            ),
            actions = mapOf(ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            aiProfiles = mapOf(
                AiProfileId("brawler") to AiProfileDef(
                    AiProfileId("brawler"), "Brawler",
                    tiers = listOf(
                        AiTier(AiCondition.Always, AiGoal.UseAction(AiActionCategory.Heal, AiTargetPreference.LowestHpPercent)),
                        AiTier(AiCondition.Always, AiGoal.UseAction(AiActionCategory.Damage, AiTargetPreference.LowestHpPercent)),
                    ),
                ),
            ),
        )
        val brawler = entity(EntityId(3), GridPos(0, 0), 10, Faction.Enemy, "fighter")
        val player = entity(heroId, GridPos(1, 0), 10, Faction.Player, "fighter")

        val decision = chooseAction(state(brawler, player), brawler.id, cat)
        assertEquals(ActionId("strike"), decision?.actionId, "tier 1 (Heal) matched Always but had no legal heal action — must fall through to tier 2, not return null")
    }

    @Test
    fun retreatMovesAwayFromTheNearestEnemyRatherThanScoringAnAttack() {
        val cat = Catalog(
            archetypes = mapOf(
                ArchetypeId("coward") to archetype("coward", listOf("move", "strike"))
                    .copy(aiProfile = AiProfileId("coward")),
            ),
            actions = mapOf(ActionId("move") to moveAction(), ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            aiProfiles = mapOf(
                AiProfileId("coward") to AiProfileDef(
                    AiProfileId("coward"), "Coward",
                    tiers = listOf(AiTier(AiCondition.SelfHpBelow(50), AiGoal.Retreat)),
                ),
            ),
        )
        val coward = entity(EntityId(3), GridPos(5, 5), 2, Faction.Enemy, "coward") // 20% HP — below the 50% threshold
        val player = entity(heroId, GridPos(4, 5), 10, Faction.Player, "coward") // adjacent — a free, guaranteed-hit strike is available

        val decision = chooseAction(state(coward, player), coward.id, cat)
        assertEquals(ActionId("move"), decision?.actionId, "low HP must trigger Retreat instead of the available (and normally-preferred) attack")
        val destination = decision?.ctx?.point
        requireNotNull(destination)
        assertTrue(destination.chebyshevDistanceTo(player.pos!!) > GridPos(5, 5).chebyshevDistanceTo(player.pos!!), "must move farther from the threat, not stay put or move closer")
    }

    @Test
    fun approachMovesTowardTheNearestEnemyWhenNothingIsInRange() {
        val cat = Catalog(
            archetypes = mapOf(
                ArchetypeId("brute") to archetype("brute", listOf("move", "strike"))
                    .copy(aiProfile = AiProfileId("hunter")),
            ),
            actions = mapOf(ActionId("move") to moveAction(), ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            aiProfiles = mapOf(
                AiProfileId("hunter") to AiProfileDef(
                    AiProfileId("hunter"), "Hunter",
                    tiers = listOf(AiTier(AiCondition.Always, AiGoal.Approach)),
                ),
            ),
        )
        val brute = entity(EntityId(3), GridPos(0, 0), 10, Faction.Enemy, "brute")
        val player = entity(heroId, GridPos(9, 9), 10, Faction.Player, "brute") // far outside melee range

        val decision = chooseAction(state(brute, player), brute.id, cat)
        assertEquals(ActionId("move"), decision?.actionId)
        val destination = decision?.ctx?.point
        requireNotNull(destination)
        assertTrue(destination.chebyshevDistanceTo(player.pos!!) < GridPos(0, 0).chebyshevDistanceTo(player.pos!!), "must move closer to the distant enemy, not stay put or move away")
    }

    @Test
    fun approachRoutesAroundAWallInsteadOfOscillatingAgainstIt() {
        // A wall blocks crossing between col 4 and col 5 for rows 0..3 only (a gap lower down) —
        // found live: ranking reachableTiles by straight-line Chebyshev distance to the enemy picked
        // whichever reachable tile LOOKED closest even though the wall made it a dead end, so the AI
        // pinned itself against the wall and alternated one tile down / one tile up forever instead
        // of detouring through the gap.
        val wallEdges = (0..3).map { row -> WallEdge(GridPos(4, row), Side.East) }.toSet()
        val map = BattleMap(10, 10, wallEdges = wallEdges)
        val cat = Catalog(
            archetypes = mapOf(ArchetypeId("hunter") to archetype("hunter", listOf("move", "strike")).copy(aiProfile = AiProfileId("hunter"))),
            actions = mapOf(ActionId("move") to moveAction(), ActionId("strike") to strikeAction("strike", targeting = meleeHitsPlayers)),
            aiProfiles = mapOf(
                AiProfileId("hunter") to AiProfileDef(AiProfileId("hunter"), "Hunter", tiers = listOf(AiTier(AiCondition.Always, AiGoal.Approach))),
            ),
        )
        val enemy = entity(heroId, GridPos(5, 0), 10, Faction.Player, "hunter")
        var hunter = entity(EntityId(3), GridPos(4, 0), 10, Faction.Enemy, "hunter") // already pinned against the wall, mirroring the reported live state

        fun stateWith(hunterPos: GridPos) = GameState(
            entities = listOf(hunter.copy(pos = hunterPos), enemy),
            map = map,
            turn = TurnState(round = 1, order = listOf(hunter.id, enemy.id), activeIndex = 0, phase = TurnPhase.Main),
            rng = RngState(seed = 1, calls = 0),
        )

        val first = chooseAction(stateWith(hunter.pos!!), hunter.id, cat)
        requireNotNull(first?.ctx?.point) { "must find a route through the gap, not give up" }
        assertTrue(first.ctx.point != GridPos(4, 0), "must not stand still at the wall-pinned tile")

        val second = chooseAction(stateWith(first.ctx.point!!), hunter.id, cat)
        requireNotNull(second?.ctx?.point)
        assertTrue(second.ctx.point != GridPos(4, 0), "must not backtrack to the original wall-pinned tile on the very next turn")
    }

    @Test
    fun anArchetypeWithNoAuthoredProfileBehavesExactlyLikeTheOriginalScorer() {
        // "medic" archetype here never sets aiProfile — stays at the zero-config default, and
        // catalog.aiProfiles is empty entirely. Must fall straight to defaultScoredChoice, proving
        // this whole system is additive: existing content is unaffected until it opts in.
        val cat = baseCatalog(strikeAction("strike"), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "hero"),
        )
        val decision = chooseAction(s, heroId, cat)
        assertEquals(ActionId("strike"), decision?.actionId)
    }

    @Test
    fun anEnemyOnAnUnrevealedTileSkipsItsTurnEntirely() {
        // Adjacent (a legal melee target either way) so the only thing that can make this null is
        // the fog check itself, not an out-of-range target. meleeHitsPlayers, not meleeAutoHit — the
        // ENEMY is the caster here, so its filter must accept a Player-faction target.
        val cat = baseCatalog(strikeAction("strike", targeting = meleeHitsPlayers), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        val s = state(
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "hero"),
        ).copy(map = BattleMap(10, 10, fogOfWar = true))
        // No revealedTiles at all — the enemy's own tile was never revealed.
        assertNull(chooseAction(s, enemyAId, cat), "an enemy standing on a never-revealed tile must do nothing")
    }

    @Test
    fun anEnemyOnARevealedTileActsNormally() {
        val cat = baseCatalog(strikeAction("strike", targeting = meleeHitsPlayers), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        val enemyPos = GridPos(1, 0)
        // enemyA listed first — state()'s activeIndex=0 makes whichever entity comes first the
        // active turn-taker, and chooseAction is called for enemyAId here.
        val s = state(
            entity(enemyAId, enemyPos, 10, Faction.Enemy, "hero"),
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
        ).copy(map = BattleMap(10, 10, fogOfWar = true), revealedTiles = setOf(enemyPos))
        assertEquals(ActionId("strike"), chooseAction(s, enemyAId, cat)?.actionId)
    }

    @Test
    fun fogOfWarOffNeverSkipsAnyoneEvenWithNoRevealedTiles() {
        val cat = baseCatalog(strikeAction("strike", targeting = meleeHitsPlayers), archetypes = mapOf(ArchetypeId("hero") to archetype("hero", listOf("strike"))))
        // enemyA listed first — see note above, chooseAction is called for enemyAId.
        val s = state(
            entity(enemyAId, GridPos(1, 0), 10, Faction.Enemy, "hero"),
            entity(heroId, GridPos(0, 0), 20, Faction.Player, "hero"),
        ) // state()'s BattleMap(10, 10) already defaults fogOfWar off
        assertEquals(ActionId("strike"), chooseAction(s, enemyAId, cat)?.actionId)
    }
}
