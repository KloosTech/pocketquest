package de.jackbeback.pocketquest.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.core.model.ActionCtx
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.model.Effect
import de.jackbeback.pocketquest.core.rules.action.perform
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import de.jackbeback.pocketquest.core.rules.resolver.StepResult
import de.jackbeback.pocketquest.core.rules.resolver.run as runResolver
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.SaveRepository
import de.jackbeback.pocketquest.ui.runDesktopApp
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Same shape as :core:content's SAMPLE_CATALOG_JSON test fixture, kept as
 * an independent copy rather than shared — this one is a demo scenario for
 * a human to look at, that one is a validator test fixture; no reason
 * they must stay byte-identical.
 */
private val DEMO_CATALOG_JSON = """
{
  "archetypes": {
    "fighter": {
      "id": "fighter", "name": "Fighter",
      "abilities": { "str": 16, "dex": 12, "con": 14, "int": 10, "wis": 10, "cha": 8 },
      "baseMaxHp": 20, "baseAc": 14, "speedTiles": 6, "baseMaxAp": 2, "baseMaxMana": 0,
      "actions": ["strike"], "innateModifiers": []
    },
    "mage": {
      "id": "mage", "name": "Mage",
      "abilities": { "str": 8, "dex": 12, "con": 10, "int": 16, "wis": 12, "cha": 10 },
      "baseMaxHp": 12, "baseAc": 11, "speedTiles": 6, "baseMaxAp": 2, "baseMaxMana": 10,
      "actions": ["firebolt"], "innateModifiers": []
    }
  },
  "statuses": {
    "burning": {
      "id": "burning", "name": "Burning", "stackPolicy": "Independent", "modifiers": [],
      "onTurnStart": [{ "type": "dealDamage", "target": { "type": "caster" }, "amount": 2, "damageType": "Fire" }]
    }
  },
  "items": {},
  "actions": {
    "strike": {
      "id": "strike", "name": "Strike",
      "cost": { "action": { "type": "main" }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity", "range": { "type": "melee" }, "shape": { "type": "single" },
        "filter": { "faction": "Enemy", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true, "maxTargets": 1
      },
      "effects": [
        {
          "type": "rollAttack", "attacker": { "type": "caster" }, "target": { "type": "eachTarget" },
          "attackBonus": 5, "advantage": [], "damage": { "count": 1, "sides": 8, "modifier": 3 }, "damageType": "Slashing"
        }
      ],
      "reactionTrigger": null
    },
    "firebolt": {
      "id": "firebolt", "name": "Firebolt",
      "cost": { "action": { "type": "main" }, "mana": 3, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity", "range": { "type": "tiles", "n": 6 }, "shape": { "type": "single" },
        "filter": { "faction": "Player", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true, "maxTargets": 1
      },
      "effects": [
        {
          "type": "rollAttack", "attacker": { "type": "caster" }, "target": { "type": "eachTarget" },
          "attackBonus": 4, "advantage": [], "damage": { "count": 2, "sides": 6, "modifier": 0 }, "damageType": "Fire"
        },
        {
          "type": "applyStatus", "target": { "type": "eachTarget" }, "status": "burning", "stacks": 1,
          "expiry": { "type": "endOfRound", "round": 3 }
        }
      ],
      "reactionTrigger": null
    }
  }
}
""".trimIndent()

fun main() {
    val log = mutableListOf<String>()

    val catalog = CatalogLoader.parse(DEMO_CATALOG_JSON)
    CatalogValidator.validate(catalog)
    log += "Loaded catalog: ${catalog.archetypes.size} archetypes, ${catalog.actions.size} actions, ${catalog.statuses.size} statuses"

    val heroId = EntityId(0)
    val goblinId = EntityId(1)
    val hero = Entity(
        id = heroId, archetype = ArchetypeId("fighter"), pos = GridPos(0, 0),
        health = Health(20), resources = Resources(ap = 2, mana = 0),
        actor = Actor(Faction.Player, Controller.Human),
    )
    val goblin = Entity(
        id = goblinId, archetype = ArchetypeId("mage"), pos = GridPos(1, 0),
        health = Health(12), resources = Resources(ap = 2, mana = 10),
        actor = Actor(Faction.Enemy, Controller.Ai(de.jackbeback.pocketquest.core.model.AiProfileId("default"))),
    )
    var state = GameState(
        entities = listOf(hero, goblin),
        map = BattleMap(10, 10),
        turn = TurnState(round = 1, order = listOf(heroId, goblinId), activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 42, calls = 0),
    )
    log += "Round 1: hero (fighter, ${hero.health?.current} HP) at ${hero.pos} vs goblin (mage, ${goblin.health?.current} HP) at ${goblin.pos}"

    // Records a step's outcome to the log and returns its resulting Resolver, so callers can
    // thread `state` through the whole turn loop the same way a real UI/AI driver would.
    fun record(label: String, result: StepResult): Resolver {
        when (result) {
            is StepResult.Completed -> {
                log += label
                result.resolver.emitted.forEach { log += "  -> $it" }
            }
            is StepResult.Rejected -> log += "$label — REJECTED: ${result.reasons}"
            is StepResult.AwaitingInput -> log += "$label — paused awaiting a decision: ${result.request}"
        }
        return result.resolver
    }

    // Round 1, hero's turn: Strike the goblin.
    state = record(
        "hero uses Strike on goblin:",
        perform(state, heroId, ActionId("strike"), ActionCtx(heroId, listOf(goblinId), point = state.byId.getValue(goblinId).pos), catalog),
    ).state

    // Hero ends their turn -> goblin's turn begins (doc04's 7-step turn boundary).
    state = record(
        "hero ends their turn:",
        runResolver(Resolver(state, stack = listOf(Effect.EndTurn(heroId))), catalog),
    ).state

    // Round 1, goblin's turn: Firebolt the hero. :core:ai is still a placeholder — the enemy's
    // action is chosen by hand here, exactly like the hero's, not by any real AI decision logic.
    state = record(
        "goblin uses Firebolt on hero:",
        perform(state, goblinId, ActionId("firebolt"), ActionCtx(goblinId, listOf(heroId), point = state.byId.getValue(heroId).pos), catalog),
    ).state

    // Goblin ends their turn -> round 2, hero's turn begins. If Firebolt's burn caught, this is
    // where it ticks: onTurnStart effects fire automatically as part of the SAME turn-boundary
    // resolution, before this step returns.
    val resolver = record(
        "goblin ends their turn:",
        runResolver(Resolver(state, stack = listOf(Effect.EndTurn(goblinId))), catalog),
    )
    state = resolver.state

    log += "Round ${state.turn.round}: hero ${state.byId.getValue(heroId).health?.current} HP, goblin ${state.byId.getValue(goblinId).health?.current} HP"

    val dbPath = File("pocketquest-demo.db").absolutePath
    val db = Room.databaseBuilder<PocketQuestDatabase>(name = dbPath)
        .setDriver(BundledSQLiteDriver())
        .build()
    val repository = SaveRepository(db.saveSlotDao())

    runBlocking {
        repository.save(id = "demo", campaignId = "demo-campaign", updatedAt = System.currentTimeMillis(), label = "Smoke test", resolver = resolver)
        val reloaded = repository.load("demo")
        log += if (reloaded == resolver) {
            "Saved to $dbPath and reloaded byte-identical ✓"
        } else {
            "MISMATCH after reload — saved != reloaded"
        }
    }
    db.close()

    log.forEach(::println)
    runDesktopApp(state, log)
}
