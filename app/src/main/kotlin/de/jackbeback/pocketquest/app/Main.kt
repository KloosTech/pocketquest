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
      "actions": ["strike", "move"], "innateModifiers": []
    },
    "guardian": {
      "id": "guardian", "name": "Guardian",
      "abilities": { "str": 15, "dex": 10, "con": 16, "int": 8, "wis": 12, "cha": 10 },
      "baseMaxHp": 30, "baseAc": 16, "speedTiles": 5, "baseMaxAp": 2, "baseMaxMana": 0,
      "actions": ["taunt", "move"], "innateModifiers": []
    },
    "cleric": {
      "id": "cleric", "name": "Cleric",
      "abilities": { "str": 10, "dex": 10, "con": 12, "int": 10, "wis": 16, "cha": 12 },
      "baseMaxHp": 14, "baseAc": 12, "speedTiles": 6, "baseMaxAp": 2, "baseMaxMana": 8,
      "actions": ["heal", "move"], "innateModifiers": []
    },
    "mage": {
      "id": "mage", "name": "Mage",
      "abilities": { "str": 8, "dex": 12, "con": 10, "int": 16, "wis": 12, "cha": 10 },
      "baseMaxHp": 12, "baseAc": 11, "speedTiles": 6, "baseMaxAp": 2, "baseMaxMana": 10,
      "actions": ["firebolt", "move"], "innateModifiers": []
    },
    "grunt": {
      "id": "grunt", "name": "Grunt",
      "abilities": { "str": 13, "dex": 12, "con": 12, "int": 8, "wis": 8, "cha": 8 },
      "baseMaxHp": 14, "baseAc": 12, "speedTiles": 6, "baseMaxAp": 2, "baseMaxMana": 0,
      "actions": ["clobber", "move"], "innateModifiers": []
    }
  },
  "statuses": {
    "burning": {
      "id": "burning", "name": "Burning", "stackPolicy": "Independent", "modifiers": [],
      "onTurnStart": [{ "type": "dealDamage", "target": { "type": "caster" }, "amount": 2, "damageType": "Fire" }]
    },
    "taunted": {
      "id": "taunted", "name": "Taunted", "stackPolicy": "Refresh",
      "modifiers": [{ "type": "grant", "flag": "Taunted" }]
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
    "move": {
      "id": "move", "name": "Move",
      "cost": { "action": { "type": "movement", "tiles": 6 }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "Path", "range": { "type": "tiles", "n": 8 }, "shape": { "type": "single" },
        "filter": { "faction": null, "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": false, "maxTargets": 1
      },
      "effects": [],
      "reactionTrigger": null
    },
    "taunt": {
      "id": "taunt", "name": "Taunt",
      "cost": { "action": { "type": "main" }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity", "range": { "type": "tiles", "n": 4 }, "shape": { "type": "single" },
        "filter": { "faction": "Enemy", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true, "maxTargets": 1
      },
      "effects": [
        {
          "type": "applyStatus", "target": { "type": "eachTarget" }, "caster": { "type": "caster" },
          "status": "taunted", "stacks": 1, "expiry": { "type": "permanent" }
        }
      ],
      "reactionTrigger": null
    },
    "heal": {
      "id": "heal", "name": "Heal",
      "cost": { "action": { "type": "main" }, "mana": 3, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity", "range": { "type": "tiles", "n": 5 }, "shape": { "type": "single" },
        "filter": { "faction": "Player", "requireAlive": false, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true, "maxTargets": 1
      },
      "effects": [
        { "type": "heal", "target": { "type": "eachTarget" }, "amount": 8, "source": { "type": "caster" } }
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
    },
    "clobber": {
      "id": "clobber", "name": "Clobber",
      "cost": { "action": { "type": "main" }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity", "range": { "type": "melee" }, "shape": { "type": "single" },
        "filter": { "faction": "Player", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true, "maxTargets": 1
      },
      "effects": [
        {
          "type": "rollAttack", "attacker": { "type": "caster" }, "target": { "type": "eachTarget" },
          "attackBonus": 4, "advantage": [], "damage": { "count": 1, "sides": 6, "modifier": 2 }, "damageType": "Bludgeoning"
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

    // doc10's party trinity, filled for real: fighter (damage, existing), guardian (tank — its
    // Taunt is the first thing in this whole project to ever exercise doc17 2.3 live), cleric
    // (healer — first live use of Effect.Heal via real content too). 2 enemies (existing ranged
    // mage + a new melee grunt) so the party actually has something to triage between.
    val heroId = EntityId(0)
    val guardianId = EntityId(1)
    val clericId = EntityId(2)
    val mageId = EntityId(3)
    val gruntId = EntityId(4)
    val aiProfile = de.jackbeback.pocketquest.core.model.AiProfileId("default")
    val hero = Entity(
        id = heroId, archetype = ArchetypeId("fighter"), pos = GridPos(0, 0),
        health = Health(20), resources = Resources(ap = 2, mana = 0),
        actor = Actor(Faction.Player, Controller.Human),
    )
    val guardian = Entity(
        id = guardianId, archetype = ArchetypeId("guardian"), pos = GridPos(1, 0),
        health = Health(30), resources = Resources(ap = 2, mana = 0),
        actor = Actor(Faction.Player, Controller.Human),
    )
    val cleric = Entity(
        id = clericId, archetype = ArchetypeId("cleric"), pos = GridPos(0, 1),
        health = Health(14), resources = Resources(ap = 2, mana = 8),
        actor = Actor(Faction.Player, Controller.Human),
    )
    val mage = Entity(
        id = mageId, archetype = ArchetypeId("mage"), pos = GridPos(4, 3),
        health = Health(12), resources = Resources(ap = 2, mana = 10),
        actor = Actor(Faction.Enemy, Controller.Ai(aiProfile)),
    )
    val grunt = Entity(
        id = gruntId, archetype = ArchetypeId("grunt"), pos = GridPos(3, 4),
        health = Health(14), resources = Resources(ap = 2, mana = 0),
        actor = Actor(Faction.Enemy, Controller.Ai(aiProfile)),
    )
    val initialState = GameState(
        entities = listOf(hero, guardian, cleric, mage, grunt),
        map = BattleMap(10, 10),
        turn = TurnState(round = 1, order = listOf(heroId, guardianId, clericId, mageId, gruntId), activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 42, calls = 0),
        nextEntityId = 5, // one past every hand-assigned id above — nothing spawns yet, but keeps this correct for when something does
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
