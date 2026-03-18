package de.jackbeback.pocketquest.game.animation

import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.ecs.components.combat.DamageType
import de.jackbeback.pocketquest.ecs.core.EntityId

const val ANIM_DURATION_MS = 500L
const val MOVE_ANIM_MS = 280L

/**
 * A single visual event to be played by the battle UI.
 * Positions are normalised [0.0, 1.0] relative to the battle field canvas.
 */
sealed class AnimationEvent {

    /** A skill projectile flying from caster to target. */
    data class ProjectileSkill(
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
        val skillId: String,
        val animationType: AnimationType,
    ) : AnimationEvent()

    /** A damage number floating up at the target's position. */
    data class FloatingDamage(
        val x: Float,
        val y: Float,
        val amount: Int,
        val damageType: DamageType,
    ) : AnimationEvent()

    /** A heal number floating up at the target's position. */
    data class FloatingHeal(
        val x: Float,
        val y: Float,
        val amount: Int,
    ) : AnimationEvent()

    /** A unit sliding from one grid cell to another. */
    data class UnitMove(
        val entityId: EntityId,
        val fromNormX: Float,
        val fromNormY: Float,
        val toNormX: Float,
        val toNormY: Float,
    ) : AnimationEvent()
}
