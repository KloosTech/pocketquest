package de.jackbeback.pocketquest.content.dsl

import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageResistancesComponent
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.components.combat.SkillSetComponent
import de.jackbeback.pocketquest.ecs.components.core.*
import de.jackbeback.pocketquest.ecs.components.map.MapLocationComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.set

/** Builder for unit templates defined in content/definitions. */
class UnitTemplate(val id: String) {
    var name: String = ""
    var faction: Faction = Faction.NEUTRAL
    var sprite: String = ""
    var maxHealth: Int = 10
    var maxMana: Int = 0
    var maxAp: Int = 2
    /** How many separate move hops the unit gets per turn. */
    var movesPerTurn: Int = 2
    /** Maximum Chebyshev distance allowed per single hop. */
    var moveRange: Int = 3
    var str: Int = 10
    var dex: Int = 10
    var con: Int = 10
    var intelligence: Int = 10
    var wis: Int = 10
    var cha: Int = 10
    var ac: Int = 10
    var initiative: Int = 10
    var aiStrategy: AIStrategy = AIStrategy.Passive
    var preferredSkillId: String = "basic_attack"
    /** Battle grid spawn position (column). */
    var spawnCol: Int = 3
    /** Battle grid spawn position (row). */
    var spawnRow: Int = 4
    val skillIds = mutableListOf<String>()
    val damageResistances = mutableMapOf<DamageType, Float>()
    var mapId: String = ""
    var mapX: Double = 0.5
    var mapY: Double = 0.5

    fun stats(block: UnitTemplate.() -> Unit) = this.block()
    fun health(max: Int) { maxHealth = max }
    fun mana(max: Int) { maxMana = max }
    fun skills(vararg ids: String) { skillIds.addAll(ids) }
    fun resistance(type: DamageType, multiplier: Float) { damageResistances[type] = multiplier }
}

fun unit(id: String, block: UnitTemplate.() -> Unit): UnitTemplate =
    UnitTemplate(id).apply(block)

/** Spawn this template as a live entity in [world] and return the new [EntityId]. */
fun UnitTemplate.spawnIntoWorld(world: World): EntityId {
    val entity = world.createEntity()
    world.set(entity, NameComponent(displayName = name, internalId = id))
    world.set(entity, FactionComponent(faction))
    world.set(entity, PositionComponent(col = spawnCol, row = spawnRow))
    world.set(entity, HealthComponent(current = maxHealth, max = maxHealth))
    world.set(entity, ManaComponent(current = maxMana, max = maxMana))
    world.set(entity, ActionPointsComponent(current = maxAp, max = maxAp))
    world.set(entity, MovementPointsComponent(current = movesPerTurn, max = movesPerTurn, range = moveRange))
    world.set(entity, StatsComponent(str = str, dex = dex, con = con, intelligence = intelligence, wis = wis, cha = cha, ac = ac))
    world.set(entity, InitiativeComponent(value = initiative))
    world.set(entity, TurnStateComponent())
    world.set(entity, RenderComponent(spriteKey = sprite))
    world.set(entity, ConditionsComponent())
    world.set(entity, DamageResistancesComponent(multipliers = damageResistances.toMap()))
    if (skillIds.isNotEmpty()) {
        world.set(entity, SkillSetComponent(skills = skillIds.toList()))
    }
    if (mapId.isNotEmpty()) {
        world.set(entity, MapLocationComponent(mapId, mapX, mapY))
    }
    if (faction == Faction.ENEMY) {
        world.set(entity, AIComponent(strategy = aiStrategy, preferredSkillId = preferredSkillId))
    }
    return entity
}
