package de.jackbeback.pocketquest.content.definitions

import de.jackbeback.pocketquest.content.dsl.unit
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.core.AIStrategy
import de.jackbeback.pocketquest.ecs.components.core.Faction

val wizardTemplate = unit("wizard") {
    name = "Wizard"
    faction = Faction.PLAYER
    sprite = "wizard"
    stats {
        str = 8; dex = 14; con = 10; intelligence = 18; wis = 14; cha = 12; ac = 12
    }
    health(max = 25)
    mana(max = 20)
    maxAp = 2
    movesPerTurn = 1
    moveRange = 3
    initiative = 14
    spawnCol = 2; spawnRow = 4
    skills("magic_missile", "basic_heal", "thorn_whip", "fireball")
    mapId = "KaerMorhen"; mapX = 0.3; mapY = 0.4
}

val gruntTemplate = unit("grunt") {
    name = "Grunt"
    faction = Faction.ENEMY
    sprite = "grunt"
    stats {
        str = 12; dex = 8; con = 14; intelligence = 6; wis = 8; cha = 6; ac = 13
    }
    health(max = 20)
    mana(max = 0)
    maxAp = 1
    movesPerTurn = 1
    moveRange = 3
    initiative = 8
    aiStrategy = AIStrategy.Aggressive
    preferredSkillId = "basic_attack"
    spawnCol = 11; spawnRow = 4
    skills("basic_attack")
    resistance(DamageType.FIRE, 0.5f)
    mapId = "KaerMorhen"; mapX = 0.6; mapY = 0.5
}
