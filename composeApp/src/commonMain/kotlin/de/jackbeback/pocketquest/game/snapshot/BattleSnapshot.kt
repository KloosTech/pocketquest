package de.jackbeback.pocketquest.game.snapshot

import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.ConditionsComponent
import de.jackbeback.pocketquest.ecs.components.combat.SkillSetComponent
import de.jackbeback.pocketquest.ecs.components.core.*
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.battle.chebyshevDistance
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.ui.battle.BattleUiState
import de.jackbeback.pocketquest.ui.battle.SkillUiState
import de.jackbeback.pocketquest.ui.battle.UnitUiState

/**
 * Queries the [World] into an immutable [BattleUiState] for Compose.
 * Called after each phase completes — Compose never touches the World directly.
 */
fun World.snapshotBattle(
    phase: TurnPhase,
    log: List<String> = emptyList(),
    selectedSkill: String? = null,
    skillRegistry: SkillRegistry? = null,
    pendingTargets: List<EntityId> = emptyList(),
): BattleUiState {
    val units = query<NameComponent, FactionComponent>()
        .map { (id, name, faction) ->
            UnitUiState(
                entityId = id,
                name = name.displayName,
                faction = faction.faction,
                position = get<PositionComponent>(id) ?: PositionComponent(0, 0),
                health = get<HealthComponent>(id) ?: HealthComponent(0, 0),
                mana = get<ManaComponent>(id) ?: ManaComponent(0, 0),
                conditions = get<ConditionsComponent>(id)?.active ?: emptyMap(),
                spriteKey = get<RenderComponent>(id)?.spriteKey ?: ""
            )
        }.toList()

    // --- Player-specific state ---
    val playerEntry = query<FactionComponent>()
        .filter { (_, f) -> f.faction == Faction.PLAYER }
        .firstOrNull()

    val playerId    = playerEntry?.first
    val playerPos   = playerId?.let { get<PositionComponent>(it) }
    val playerTurn  = playerId?.let { get<TurnStateComponent>(it) }
    val playerMp    = playerId?.let { get<MovementPointsComponent>(it) }

    // Cells occupied by enemies (can't move onto them)
    val enemyCells = units
        .filter { it.faction == Faction.ENEMY }
        .map { Pair(it.position.col, it.position.row) }
        .toSet()

    // Reachable tiles for one hop: within mp.range, moves remaining > 0, only on player's turn
    val reachableTiles: Set<Pair<Int, Int>> = if (
        phase == TurnPhase.PlayerPhase &&
        playerPos != null &&
        (playerMp?.current ?: 0) > 0
    ) {
        val hopRange = playerMp?.range ?: 0
        buildSet {
            for (c in 0 until BATTLE_COLS) {
                for (r in 0 until BATTLE_ROWS) {
                    val dist = chebyshevDistance(playerPos, PositionComponent(c, r))
                    if (dist in 1..hopRange && Pair(c, r) !in enemyCells) add(Pair(c, r))
                }
            }
        }
    } else emptySet()

    // Resolve available skills and selected skill's range highlight
    val availableSkills: List<SkillUiState>
    val attackableTiles: Set<Pair<Int, Int>>

    if (skillRegistry != null && playerId != null) {
        availableSkills = get<SkillSetComponent>(playerId)
            ?.skills
            ?.mapNotNull { id ->
                skillRegistry.find(id)?.let { tpl ->
                    SkillUiState(
                        id = tpl.id, name = tpl.name, manaCost = tpl.manaCost,
                        spriteKey = tpl.spriteKey, range = tpl.range, maxTargets = tpl.maxTargets,
                    )
                }
            } ?: emptyList()

        val skill = selectedSkill?.let { skillRegistry.find(it) }
        attackableTiles = if (
            phase == TurnPhase.PlayerPhase &&
            skill != null &&
            playerTurn?.hasActed == false &&
            playerPos != null
        ) {
            if (skill.animationType == AnimationType.HEAL) {
                // Heal targets the player themselves
                setOf(Pair(playerPos.col, playerPos.row))
            } else {
                units
                    .filter { it.faction == Faction.ENEMY }
                    .filter { chebyshevDistance(playerPos, it.position) <= skill.range }
                    .map { Pair(it.position.col, it.position.row) }
                    .toSet()
            }
        } else emptySet()
    } else {
        availableSkills = emptyList()
        attackableTiles = emptySet()
    }

    val isBattleOver = query<FactionComponent>().none { (_, f) -> f.faction == Faction.ENEMY }

    val selectedTargetTiles = units
        .filter { it.entityId in pendingTargets }
        .map { Pair(it.position.col, it.position.row) }
        .toSet()

    return BattleUiState(
        units = units,
        turnPhase = phase,
        log = log,
        availableSkills = availableSkills,
        selectedSkill = selectedSkill,
        isBattleOver = isBattleOver,
        reachableTiles = reachableTiles,
        attackableTiles = attackableTiles,
        playerMovesRemaining = playerMp?.current ?: 0,
        playerMaxMoves = playerMp?.max ?: 0,
        playerHasActed = playerTurn?.hasActed ?: false,
        pendingTargetIds = pendingTargets,
        selectedTargetTiles = selectedTargetTiles,
    )
}
