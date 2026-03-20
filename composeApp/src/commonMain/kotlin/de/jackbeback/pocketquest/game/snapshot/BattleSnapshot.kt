package de.jackbeback.pocketquest.game.snapshot

import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.map.TileType
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
import de.jackbeback.pocketquest.game.pathfinding.computeVisibleTiles
import de.jackbeback.pocketquest.game.pathfinding.hasLineOfSight
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
    tileMap: TileMap? = null,
    exploredTiles: Set<Pair<Int, Int>> = emptySet(),
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
                spriteKey = get<RenderComponent>(id)?.spriteKey ?: "",
                stats = get<StatsComponent>(id),
            )
        }.toList()

    // --- Player-specific state ---
    val playerEntry = query<FactionComponent>()
        .filter { (_, f) -> f.faction == Faction.PLAYER }
        .firstOrNull()

    val playerId    = playerEntry?.first
    val playerPos   = playerId?.let { get<PositionComponent>(it) }
    val playerMana  = playerId?.let { get<ManaComponent>(it) } ?: ManaComponent(0, 0)
    val playerMp    = playerId?.let { get<MovementPointsComponent>(it) }

    val visibleTiles: Set<Pair<Int, Int>> = if (playerPos != null) {
        computeVisibleTiles(playerPos, tileMap, tileMap?.cols ?: BATTLE_COLS, tileMap?.rows ?: BATTLE_ROWS)
    } else emptySet()

    // Cells occupied by enemies (can't move onto them)
    val enemyCells = units
        .filter { it.faction == Faction.ENEMY }
        .map { Pair(it.position.col, it.position.row) }
        .toSet()

    // Use tileMap dimensions when available, fall back to hardcoded battle grid size
    val gridCols = tileMap?.cols ?: BATTLE_COLS
    val gridRows = tileMap?.rows ?: BATTLE_ROWS

    // 1-tile ring just outside visibleTiles — rendered dimmed to soften the FOW boundary
    val perimeterTiles: Set<Pair<Int, Int>> = buildSet {
        for ((c, r) in visibleTiles) {
            for (dc in -1..1) for (dr in -1..1) {
                if (dc == 0 && dr == 0) continue
                val nc = c + dc; val nr = r + dr
                if (nc < 0 || nr < 0 || nc >= gridCols || nr >= gridRows) continue
                val cell = nc to nr
                if (cell !in visibleTiles) add(cell)
            }
        }
    }

    // Reachable tiles for one hop: within mp.range, moves remaining > 0, only on player's turn
    val reachableTiles: Set<Pair<Int, Int>> = if (
        phase == TurnPhase.PlayerPhase &&
        playerPos != null &&
        (playerMp?.current ?: 0) > 0
    ) {
        val hopRange = playerMp?.range ?: 0
        val mpLeft = playerMp?.current ?: 0
        buildSet {
            for (c in 0 until gridCols) {
                for (r in 0 until gridRows) {
                    val dist = chebyshevDistance(playerPos, PositionComponent(c, r))
                    if (dist !in 1..hopRange) continue
                    if (Pair(c, r) in enemyCells) continue
                    if (tileMap?.isWalkable(c, r) == false) continue
                    val cost = tileMap?.typeAt(c, r)?.movementCost ?: 1
                    if (mpLeft < cost) continue
                    add(Pair(c, r))
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
            playerMana.current >= skill.manaCost &&
            playerPos != null
        ) {
            if (!skill.needsTarget) {
                // Self-targeting skills (Heal, Block, etc.) highlight the player's own cell
                setOf(Pair(playerPos.col, playerPos.row))
            } else {
                units
                    .filter { it.faction == Faction.ENEMY }
                    .filter { chebyshevDistance(playerPos, it.position) <= skill.range }
                    .filter { hasLineOfSight(playerPos, it.position, tileMap) }
                    .map { Pair(it.position.col, it.position.row) }
                    .toSet()
            }
        } else emptySet()
    } else {
        availableSkills = emptyList()
        attackableTiles = emptySet()
    }

    val noEnemiesLeft = query<FactionComponent>().none { (_, f) -> f.faction == Faction.ENEMY }
    val playerAlive   = playerId != null && isAlive(playerId)
    val isVictory = noEnemiesLeft && playerAlive
    val isDefeat  = !playerAlive

    val selectedTargetTiles = units
        .filter { it.entityId in pendingTargets }
        .map { Pair(it.position.col, it.position.row) }
        .toSet()

    // Build terrain type map for UI rendering
    val tileTypes: Map<Pair<Int, Int>, TileType> = if (tileMap != null) {
        buildMap {
            for (c in 0 until tileMap.cols) {
                for (r in 0 until tileMap.rows) {
                    val type = tileMap.typeAt(c, r)
                    if (type != TileType.FLOOR) put(Pair(c, r), type)
                }
            }
        }
    } else emptyMap()

    return BattleUiState(
        units = units,
        turnPhase = phase,
        log = log,
        availableSkills = availableSkills,
        selectedSkill = selectedSkill,
        isVictory = isVictory,
        isDefeat = isDefeat,
        reachableTiles = reachableTiles,
        attackableTiles = attackableTiles,
        playerMovesRemaining = playerMp?.current ?: 0,
        playerMaxMoves = playerMp?.max ?: 0,
        playerMana = playerMana,
        pendingTargetIds = pendingTargets,
        selectedTargetTiles = selectedTargetTiles,
        tileTypes      = tileTypes,
        gridCols       = gridCols,
        gridRows       = gridRows,
        visibleTiles   = visibleTiles,
        exploredTiles  = exploredTiles + visibleTiles,
        perimeterTiles = perimeterTiles,
    )
}
