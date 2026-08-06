package de.jackbeback.pocketquest.core.content

/**
 * A small, hand-authored catalog exercising every id cross-reference kind
 * [CatalogValidator] checks (archetype -> action, action effect -> status).
 * Not a port of any real content — nothing to port from yet, see
 * doc01-modules.md's migration step 3, which assumes a JSON catalog v1
 * never actually had (its units/skills/items are authored as Kotlin DSL,
 * not JSON).
 */
internal val SAMPLE_CATALOG_JSON = """
{
  "archetypes": {
    "fighter": {
      "id": "fighter",
      "name": "Fighter",
      "abilities": { "str": 16, "dex": 12, "con": 14, "int": 10, "wis": 10, "cha": 8 },
      "baseMaxHp": 20,
      "baseAc": 14,
      "speedTiles": 6,
      "baseMaxAp": 2,
      "baseMaxMana": 0,
      "actions": ["strike"],
      "innateModifiers": []
    },
    "mage": {
      "id": "mage",
      "name": "Mage",
      "abilities": { "str": 8, "dex": 12, "con": 10, "int": 16, "wis": 12, "cha": 10 },
      "baseMaxHp": 12,
      "baseAc": 11,
      "speedTiles": 6,
      "baseMaxAp": 2,
      "baseMaxMana": 10,
      "actions": ["firebolt"],
      "innateModifiers": []
    }
  },
  "statuses": {
    "burning": {
      "id": "burning",
      "name": "Burning",
      "stackPolicy": "Independent",
      "modifiers": [],
      "onTurnStart": [
        { "type": "dealDamage", "target": { "type": "caster" }, "amount": 2, "damageType": "Fire" }
      ]
    }
  },
  "items": {},
  "actions": {
    "strike": {
      "id": "strike",
      "name": "Strike",
      "cost": { "action": { "type": "main" }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity",
        "range": { "type": "melee" },
        "shape": { "type": "single" },
        "filter": { "faction": "Enemy", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true,
        "maxTargets": 1
      },
      "effects": [
        {
          "type": "rollAttack",
          "attacker": { "type": "caster" },
          "target": { "type": "eachTarget" },
          "attackBonus": 5,
          "advantage": [],
          "damage": { "count": 1, "sides": 8, "modifier": 3 },
          "damageType": "Slashing"
        }
      ],
      "reactionTrigger": null
    },
    "firebolt": {
      "id": "firebolt",
      "name": "Firebolt",
      "cost": { "action": { "type": "main" }, "mana": 3, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity",
        "range": { "type": "tiles", "n": 6 },
        "shape": { "type": "single" },
        "filter": { "faction": "Enemy", "requireAlive": true, "hasStatus": null, "excludeSelf": false },
        "requiresLoS": true,
        "maxTargets": 1
      },
      "effects": [
        {
          "type": "rollAttack",
          "attacker": { "type": "caster" },
          "target": { "type": "eachTarget" },
          "attackBonus": 4,
          "advantage": [],
          "damage": { "count": 2, "sides": 6, "modifier": 0 },
          "damageType": "Fire"
        },
        {
          "type": "applyStatus",
          "target": { "type": "eachTarget" },
          "status": "burning",
          "stacks": 1,
          "expiry": { "type": "endOfRound", "round": 3 }
        }
      ],
      "reactionTrigger": null
    }
  }
}
""".trimIndent()
