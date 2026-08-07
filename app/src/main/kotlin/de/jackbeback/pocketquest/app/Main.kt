package de.jackbeback.pocketquest.app

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.jackbeback.pocketquest.core.content.CatalogLoader
import de.jackbeback.pocketquest.core.content.CatalogValidator
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
import de.jackbeback.pocketquest.data.PocketQuestDatabase
import de.jackbeback.pocketquest.data.SaveRepository
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
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
    val catalog = CatalogLoader.parse(DEMO_CATALOG_JSON)
    CatalogValidator.validate(catalog)
    println("Loaded catalog: ${catalog.archetypes.size} archetypes, ${catalog.actions.size} actions, ${catalog.statuses.size} statuses")

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
    val initialState = GameState(
        entities = listOf(hero, goblin),
        map = BattleMap(10, 10),
        turn = TurnState(round = 1, order = listOf(heroId, goblinId), activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 42, calls = 0),
        nextEntityId = 2, // one past heroId/goblinId — nothing spawns yet, but keeps this correct for when something does
    )

    // Persistence smoke test against the initial state — the interactive session's own state
    // evolves live inside :ui now, so there's no longer a fixed "final" resolver to round-trip.
    val dbPath = File("pocketquest-demo.db").absolutePath
    val db = Room.databaseBuilder<PocketQuestDatabase>(name = dbPath)
        .setDriver(BundledSQLiteDriver())
        .build()
    val repository = SaveRepository(db.saveSlotDao())
    runBlocking {
        val resolver = Resolver(initialState)
        repository.save(id = "demo", campaignId = "demo-campaign", updatedAt = System.currentTimeMillis(), label = "Smoke test", resolver = resolver)
        val reloaded = repository.load("demo")
        println(if (reloaded == resolver) "Saved to $dbPath and reloaded byte-identical ✓" else "MISMATCH after reload — saved != reloaded")
    }
    db.close()

    runDesktopApp(initialState, catalog)
}
