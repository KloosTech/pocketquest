package de.jackbeback.pocketquest.ui.battle

import de.jackbeback.pocketquest.content.definitions.Relic
import de.jackbeback.pocketquest.content.dsl.UnitTemplate
import de.jackbeback.pocketquest.content.map.TileMap
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.PositionComponent
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.game.battle.BATTLE_COLS
import de.jackbeback.pocketquest.game.battle.BATTLE_ROWS
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.game.battle.TileMapRepository
import de.jackbeback.pocketquest.game.run.LevelUpResult
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import de.jackbeback.pocketquest.content.dsl.AnimationType
import de.jackbeback.pocketquest.game.animation.ANIM_DURATION_MS
import de.jackbeback.pocketquest.game.animation.MELEE_ANIM_MS
import de.jackbeback.pocketquest.game.animation.MOVE_ANIM_MS
import de.jackbeback.pocketquest.game.animation.AnimationEvent
import de.jackbeback.pocketquest.game.animation.AnimationEventCollector
import de.jackbeback.pocketquest.game.loop.GameLoop
import de.jackbeback.pocketquest.game.loop.PlayerAction
import de.jackbeback.pocketquest.game.loop.TurnPhase
import de.jackbeback.pocketquest.game.snapshot.BattleLog
import de.jackbeback.pocketquest.game.snapshot.snapshotBattle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** XP awarded per enemy defeated. */
private const val XP_PER_ENEMY = 50

class BattleViewModel(
    private val world: World,
    private val gameLoop: GameLoop,
    private val skillRegistry: SkillRegistry,
    private val battleLog: BattleLog,
    private val animCollector: AnimationEventCollector,
    val tileCache: BattleTileCache,
    /**
     * Optional repository for loading terrain maps from bundled resources.
     * If null, or if the encounter has no mapId, battles run without terrain effects.
     */
    val tileMapRepository: TileMapRepository? = null,
    /**
     * Pre-loaded tileMap to use immediately (bypasses repository loading).
     * Used by the desktop designer where the map is already in memory.
     */
    initialTileMap: TileMap? = null,
    /** Destroys all existing entities, re-spawns the player, then spawns [enemies]. */
    private val resetAndSpawn: (enemies: List<UnitTemplate>) -> Unit = {},
    /**
     * Pure preview — returns what [gainExp] would produce for [xpEarned] without modifying state.
     * Used to populate [BattleResult.leveledUp] so the result overlay can show "Level Up!".
     */
    private val levelUpPreview: (xpEarned: Int) -> LevelUpResult = { LevelUpResult(false, 1) },
    /**
     * Returns 3 relic candidates to offer the player on victory.
     * Called once when the battle result is computed; should exclude already-held relics.
     */
    private val pickRelicCandidates: () -> List<Relic> = { emptyList() },
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var selectedSkill: String? = null
    private val pendingTargets = mutableListOf<EntityId>()
    private var initialEnemyCount: Int = 0
    private val exploredTiles = mutableSetOf<Pair<Int, Int>>()

    /**
     * Terrain map for the current battle. Pre-set via [initialTileMap] (designer) or loaded
     * during [prepareBattle] from [tileMapRepository] (main app). Passed to [snapshotBattle]
     * for reachable-tile filtering and terrain overlays.
     */
    private var activeTileMap: TileMap? = initialTileMap

    private val _state = MutableStateFlow(snapshot(TurnPhase.PlayerPhase))
    val state: StateFlow<BattleUiState> = _state

    private val _animationEvent = MutableStateFlow<AnimationEvent?>(null)
    val animationEvent: StateFlow<AnimationEvent?> = _animationEvent

    private val _phaseBanner = MutableStateFlow<String?>(null)
    val phaseBanner: StateFlow<String?> = _phaseBanner

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    /**
     * Emitted once when the battle ends. The UI displays this in a result overlay
     * and calls [onBattleEnd] only when the player dismisses it.
     */
    private val _battleResult = MutableStateFlow<BattleResult?>(null)
    val battleResult: StateFlow<BattleResult?> = _battleResult

    fun prepareBattle(params: BattleParams? = null) {
        selectedSkill = null
        pendingTargets.clear()
        exploredTiles.clear()
        animCollector.drain()
        _animationEvent.value = null
        _phaseBanner.value = null
        _isLocked.value = false
        _battleResult.value = null

        scope.launch {
            // Load terrain map from bundled resources if this encounter specifies one.
            // Only overwrite activeTileMap when a mapId is explicitly provided — this preserves
            // any initialTileMap set via the constructor (e.g. the desktop designer).
            if (params?.mapId != null) {
                activeTileMap = withContext(Dispatchers.Default) { tileMapRepository?.load(params.mapId) }
            }
            // Keep animation normalisation in sync with the active map's grid dimensions
            animCollector.gridCols = activeTileMap?.cols ?: BATTLE_COLS
            animCollector.gridRows = activeTileMap?.rows ?: BATTLE_ROWS
            resetAndSpawn(params?.enemies ?: emptyList())
            _state.value = snapshot(TurnPhase.PlayerPhase)
            initialEnemyCount = _state.value.units.count { it.faction == Faction.ENEMY }
        }
    }

    /** Called when the player picks a skill from the bottom sheet. */
    fun onSkillSelected(skillId: String) {
        pendingTargets.clear()
        selectedSkill = skillId
        _state.value = snapshot(TurnPhase.PlayerPhase)
    }

    /**
     * Called when the player taps a unit status card below the grid.
     * If a skill is armed and the unit is a valid target, behaves identically to tapping
     * that unit's cell on the grid. If no skill is armed, the card handles expand/collapse itself.
     */
    fun onUnitCardTap(entityId: EntityId) {
        if (_isLocked.value) return
        val s = _state.value
        val unit = s.units.find { it.entityId == entityId } ?: return
        val cell = Pair(unit.position.col, unit.position.row)

        val armed = s.selectedSkill
        if (armed != null && cell in s.attackableTiles) {
            val skillInfo  = s.availableSkills.find { it.id == armed }
            val maxTargets = skillInfo?.maxTargets ?: 1
            if (maxTargets <= 1) {
                onPlayerAction(PlayerAction.UseSkill(armed, entityId))
            } else {
                pendingTargets += entityId
                if (pendingTargets.size >= maxTargets) {
                    onPlayerAction(PlayerAction.UseSkillOnTargets(armed, pendingTargets.toList()))
                } else {
                    _state.value = snapshot(TurnPhase.PlayerPhase)
                }
            }
            return
        }

        if (armed != null) {
            // Tapped a card that isn't a valid target — cancel the skill
            onCancelSkill()
        }
        // No skill armed → card handles its own expand/collapse
    }

    /**
     * Moves the player one tile in the given direction (dx, dy must each be -1, 0, or 1).
     * Does nothing if the move is out-of-bounds, blocked, or the player has no moves left.
     */
    fun onMoveDirection(dx: Int, dy: Int) {
        if (_isLocked.value) return
        val s = _state.value
        val player = s.units.find { it.faction == Faction.PLAYER } ?: return
        val target = PositionComponent(player.position.col + dx, player.position.row + dy)
        if (target.col !in 0 until s.gridCols || target.row !in 0 until s.gridRows) return
        if (Pair(target.col, target.row) !in s.reachableTiles) return
        onPlayerAction(PlayerAction.MoveTo(target.col, target.row))
    }

    /** Cancels the active skill selection and returns to idle. */
    fun onCancelSkill() {
        selectedSkill = null
        pendingTargets.clear()
        _state.value = snapshot(TurnPhase.PlayerPhase)
    }

    /**
     * Called when the player taps a grid cell.
     *
     * Routing:
     *  - Skill armed + cell in [BattleUiState.attackableTiles]:
     *      - single-target  → execute immediately
     *      - multi-target   → queue target; auto-execute once maxTargets reached
     *  - Skill armed + cell NOT in attackableTiles → cancel skill
     *  - No skill → attempt movement if cell is reachable
     */
    fun onCellTap(col: Int, row: Int) {
        if (_isLocked.value) return
        val s = _state.value
        val cell = Pair(col, row)

        val armedSkill = s.selectedSkill
        if (armedSkill != null && cell in s.attackableTiles) {
            val target = s.units.find { it.position.col == col && it.position.row == row }
                ?: return
            val skillInfo = s.availableSkills.find { it.id == armedSkill }
            val maxTargets = skillInfo?.maxTargets ?: 1

            if (maxTargets <= 1) {
                // Single-target: fire immediately
                onPlayerAction(PlayerAction.UseSkill(armedSkill, target.entityId))
            } else {
                // Multi-target: accumulate picks — the same target may be selected multiple times
                pendingTargets += target.entityId
                if (pendingTargets.size >= maxTargets) {
                    onPlayerAction(PlayerAction.UseSkillOnTargets(armedSkill, pendingTargets.toList()))
                } else {
                    _state.value = snapshot(TurnPhase.PlayerPhase)
                }
            }
            return
        }

        if (s.selectedSkill != null) {
            // Tapped outside valid targets — cancel
            onCancelSkill()
            return
        }

        if (cell in s.reachableTiles) {
            onPlayerAction(PlayerAction.MoveTo(col, row))
        }
    }

    /** Executes the queued multi-target skill with however many targets are pending (≥ 1). */
    fun onConfirmTargets() {
        val skillId = selectedSkill ?: return
        if (pendingTargets.isEmpty()) return
        onPlayerAction(PlayerAction.UseSkillOnTargets(skillId, pendingTargets.toList()))
    }

    private fun onPlayerAction(action: PlayerAction) {
        if (_isLocked.value) return
        selectedSkill = null
        pendingTargets.clear()
        _isLocked.value = true

        scope.launch {
            val playerAnims = withContext(Dispatchers.Default) {
                animCollector.drain()
                gameLoop.runPlayerTurn(world, action)
                animCollector.drain()
            }

            playAnimations(playerAnims)
            _state.value = snapshot(TurnPhase.PlayerPhase)
            checkAndEmitBattleResult(_state.value)
            _isLocked.value = false
        }
    }

    fun onEndTurn() {
        if (_isLocked.value) return
        selectedSkill = null
        pendingTargets.clear()
        _isLocked.value = true

        scope.launch {
            _phaseBanner.value = "Enemy Turn"
            delay(700L)
            _phaseBanner.value = null

            val enemyAnims = withContext(Dispatchers.Default) {
                gameLoop.runEnvironmentTick(world)
                gameLoop.runEnemyTurns(world)
                animCollector.drain()
            }

            playAnimations(enemyAnims)
            _state.value = snapshot(TurnPhase.PlayerPhase)

            if (_state.value.isBattleOver) {
                checkAndEmitBattleResult(_state.value)
                _isLocked.value = false
                return@launch
            }

            _phaseBanner.value = "Your Turn"
            delay(500L)
            _phaseBanner.value = null

            _isLocked.value = false
        }
    }

    private fun checkAndEmitBattleResult(state: BattleUiState) {
        if (!state.isBattleOver || _battleResult.value != null) return
        val enemiesDefeated = if (state.isVictory) initialEnemyCount else 0
        val xpEarned = enemiesDefeated * XP_PER_ENEMY
        val levelInfo = if (state.isVictory) levelUpPreview(xpEarned) else LevelUpResult(false, 1)
        val relicCandidates = if (state.isVictory) pickRelicCandidates() else emptyList()
        _battleResult.value = BattleResult(
            victory          = state.isVictory,
            enemiesDefeated  = enemiesDefeated,
            xpEarned         = xpEarned,
            leveledUp        = levelInfo.didLevelUp,
            newLevel         = levelInfo.newLevel,
            relicCandidates  = relicCandidates,
        )
    }

    private suspend fun playAnimations(events: List<AnimationEvent>) {
        for (event in events) {
            _animationEvent.value = event
            val durationMs = when (event) {
                is AnimationEvent.UnitMove -> MOVE_ANIM_MS
                is AnimationEvent.ProjectileSkill -> when (event.animationType) {
                    AnimationType.MELEE -> MELEE_ANIM_MS
                    else               -> ANIM_DURATION_MS
                }
                else -> ANIM_DURATION_MS
            }
            delay(durationMs)
            // After each move animation refresh state so the unit sticks at its destination
            // rather than snapping back to the pre-turn snapshot position between moves.
            if (event is AnimationEvent.UnitMove) {
                _state.value = snapshot(TurnPhase.PlayerPhase)
            }
        }
        _animationEvent.value = null
    }

    private fun snapshot(phase: TurnPhase): BattleUiState {
        val state = world.snapshotBattle(
            phase, battleLog.snapshot(), selectedSkill, skillRegistry,
            pendingTargets.toList(), activeTileMap, exploredTiles.toSet()
        )
        exploredTiles += state.visibleTiles
        return state
    }
}
