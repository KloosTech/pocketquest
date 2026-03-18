package de.jackbeback.pocketquest.game.loop

import de.jackbeback.pocketquest.ecs.core.EntityId

sealed class PlayerAction {
    data class UseSkill(val skillId: String, val targetId: EntityId) : PlayerAction()
    /** Fire the same skill against multiple targets in a single player phase. */
    data class UseSkillOnTargets(val skillId: String, val targetIds: List<EntityId>) : PlayerAction()
    data class MoveTo(val col: Int, val row: Int) : PlayerAction()
    object EndTurn : PlayerAction()
}
