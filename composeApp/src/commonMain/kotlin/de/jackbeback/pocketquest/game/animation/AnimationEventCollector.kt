package de.jackbeback.pocketquest.game.animation

import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
import de.jackbeback.pocketquest.ecs.components.combat.MissEvent
import de.jackbeback.pocketquest.ecs.components.combat.MoveEvent
import de.jackbeback.pocketquest.ecs.components.combat.SkillUsedEvent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS

/**
 * Subscribes to the world's EventBus once and accumulates [AnimationEvent]s as game logic runs.
 * Call [drain] after a game phase completes to retrieve and clear the collected events.
 *
 * Must be created as a singleton — each instance registers persistent EventBus handlers.
 */
class AnimationEventCollector(
    world: World,
    skillRegistry: SkillRegistry,
) {
    /** Updated by BattleViewModel when the active TileMap changes. */
    var gridCols: Int = BATTLE_COLS
    var gridRows: Int = BATTLE_ROWS

    private val _events = mutableListOf<AnimationEvent>()

    // Held until we know the attack hits (DamageEvent/HealEvent) or misses (MissEvent).
    // This prevents the travel animation from playing when the hit roll fails.
    private var pendingProjectile: AnimationEvent.ProjectileSkill? = null

    init {
        // Unit movement: registered BEFORE MovementSystem so pre-move position is still in world
        world.events().on<MoveEvent> { event ->
            val fromPos = world.get<PositionComponent>(event.entity) ?: return@on
            _events += AnimationEvent.UnitMove(
                entityId  = event.entity,
                fromNormX = gridToNormX(fromPos.col),
                fromNormY = gridToNormY(fromPos.row),
                toNormX   = gridToNormX(event.to.col),
                toNormY   = gridToNormY(event.to.row),
            )
        }

        // Projectile travel: stored as pending — only committed to _events on a confirmed hit.
        // If the hit roll fails, MissEvent discards the pending animation instead.
        world.events().on<SkillUsedEvent> { event ->
            val skill = skillRegistry.find(event.skillId) ?: return@on
            if (skill.animationType != AnimationType.PROJECTILE &&
                skill.animationType != AnimationType.MELEE) return@on
            val fromPos = world.get<PositionComponent>(event.user) ?: return@on
            val toPos = world.get<PositionComponent>(event.target) ?: return@on
            pendingProjectile = AnimationEvent.ProjectileSkill(
                fromX = gridToNormX(fromPos.col),
                fromY = gridToNormY(fromPos.row),
                toX = gridToNormX(toPos.col),
                toY = gridToNormY(toPos.row),
                skillId = event.skillId,
                animationType = skill.animationType,
            )
        }

        // Floating damage number — flush the pending projectile first, then show the number
        world.events().on<DamageEvent> { event ->
            pendingProjectile?.let { _events += it }
            pendingProjectile = null
            val pos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.FloatingDamage(
                x = gridToNormX(pos.col),
                y = gridToNormY(pos.row),
                amount = event.amount,
                damageType = event.damageType,
            )
        }

        // Floating heal number — flush the pending projectile first, then show the number
        world.events().on<HealEvent> { event ->
            pendingProjectile?.let { _events += it }
            pendingProjectile = null
            val pos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.FloatingHeal(
                x = gridToNormX(pos.col),
                y = gridToNormY(pos.row),
                amount = event.amount,
            )
        }

        // Attack missed: discard the pending projectile and show "Miss!" at the target
        world.events().on<MissEvent> { event ->
            pendingProjectile = null
            val pos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.FloatingMiss(
                x = gridToNormX(pos.col),
                y = gridToNormY(pos.row),
            )
        }
    }

    /** Returns all collected events and clears the internal list. */
    fun drain(): List<AnimationEvent> {
        pendingProjectile = null
        return _events.toList().also { _events.clear() }
    }

    private fun gridToNormX(col: Int): Float = (col + 0.5f) / gridCols
    private fun gridToNormY(row: Int): Float = (row + 0.5f) / gridRows
}
