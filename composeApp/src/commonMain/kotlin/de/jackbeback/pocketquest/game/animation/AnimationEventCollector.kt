package de.jackbeback.pocketquest.game.animation

import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.DamageEvent
import de.jackbeback.pocketquest.ecs.components.combat.HealEvent
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
    private val _events = mutableListOf<AnimationEvent>()

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

        // Projectile travel: only for skills that visually fly to the target
        world.events().on<SkillUsedEvent> { event ->
            val skill = skillRegistry.find(event.skillId) ?: return@on
            if (skill.animationType != AnimationType.PROJECTILE) return@on
            val fromPos = world.get<PositionComponent>(event.user) ?: return@on
            val toPos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.ProjectileSkill(
                fromX = gridToNormX(fromPos.col),
                fromY = gridToNormY(fromPos.row),
                toX = gridToNormX(toPos.col),
                toY = gridToNormY(toPos.row),
                skillId = event.skillId,
                animationType = skill.animationType,
            )
        }

        // Floating damage number at target position
        world.events().on<DamageEvent> { event ->
            val pos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.FloatingDamage(
                x = gridToNormX(pos.col),
                y = gridToNormY(pos.row),
                amount = event.amount,
                damageType = event.damageType,
            )
        }

        // Floating heal number at target position
        world.events().on<HealEvent> { event ->
            val pos = world.get<PositionComponent>(event.target) ?: return@on
            _events += AnimationEvent.FloatingHeal(
                x = gridToNormX(pos.col),
                y = gridToNormY(pos.row),
                amount = event.amount,
            )
        }
    }

    /** Returns all collected events and clears the internal list. */
    fun drain(): List<AnimationEvent> = _events.toList().also { _events.clear() }

    private fun gridToNormX(col: Int): Float = (col + 0.5f) / BATTLE_COLS
    private fun gridToNormY(row: Int): Float = (row + 0.5f) / BATTLE_ROWS
}
