package de.jackbeback.pocketquest.ui.battle

import de.jackbeback.pocketquest.ecs.components.combat.ConditionType
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.game.loop.TurnPhase

/** Immutable snapshot of battle state for Compose to observe. Never reads World directly. */
data class BattleUiState(
    val units: List<UnitUiState>,
    val turnPhase: TurnPhase,
    val log: List<String>,
    val availableSkills: List<SkillUiState> = emptyList(),
    val selectedSkill: String? = null,
    val isBattleOver: Boolean = false,
    /** Tiles the player can move to this turn (col, row pairs). Empty when already moved. */
    val reachableTiles: Set<Pair<Int, Int>> = emptySet(),
    /** Enemy tiles the player can hit with the selected skill. Empty when no skill selected. */
    val attackableTiles: Set<Pair<Int, Int>> = emptySet(),
    /** Move actions the player still has left this turn. */
    val playerMovesRemaining: Int = 0,
    /** Total move actions the player gets per turn. */
    val playerMaxMoves: Int = 0,
    /** Current mana of the player — used to determine which skills are castable. */
    val playerMana: ManaComponent = ManaComponent(0, 0),
    /** Targets already queued for a multi-target skill (shown with a distinct highlight). */
    val pendingTargetIds: List<EntityId> = emptyList(),
    /** Grid positions of [pendingTargetIds] units — highlighted with a distinct colour. */
    val selectedTargetTiles: Set<Pair<Int, Int>> = emptySet(),
) {
    companion object {
        val EMPTY = BattleUiState(emptyList(), TurnPhase.PlayerPhase, emptyList())
    }
}

data class UnitUiState(
    val entityId: EntityId,
    val name: String,
    val faction: Faction,
    val position: PositionComponent,
    val health: HealthComponent,
    val mana: ManaComponent,
    val conditions: Map<ConditionType, Int>,
    val spriteKey: String,
)

data class SkillUiState(
    val id: String,
    val name: String,
    val manaCost: Int,
    val spriteKey: String,
    val range: Int,
    val maxTargets: Int = 1,
)
