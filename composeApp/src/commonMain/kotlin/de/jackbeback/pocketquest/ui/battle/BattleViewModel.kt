package de.jackbeback.pocketquest.ui.battle

import de.jackbeback.pocketquest.content.dsl.UnitTemplate
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.core.EntityId
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.game.battle.BattleTileCache
import de.jackbeback.pocketquest.ui.navigation.BattleParams
import de.jackbeback.pocketquest.game.animation.ANIM_DURATION_MS
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

class BattleViewModel(
    private val world: World,
    private val gameLoop: GameLoop,
    private val skillRegistry: SkillRegistry,
    private val battleLog: BattleLog,
    private val animCollector: AnimationEventCollector,
    val tileCache: BattleTileCache,
    /** Destroys all existing entities, re-spawns the player, then spawns [enemies]. */
    private val resetAndSpawn: (enemies: List<UnitTemplate>) -> Unit = {},
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var selectedSkill: String? = null
    private val pendingTargets = mutableListOf<EntityId>()

    private val _state = MutableStateFlow(snapshot(TurnPhase.PlayerPhase))
    val state: StateFlow<BattleUiState> = _state

    private val _animationEvent = MutableStateFlow<AnimationEvent?>(null)
    val animationEvent: StateFlow<AnimationEvent?> = _animationEvent

    private val _phaseBanner = MutableStateFlow<String?>(null)
    val phaseBanner: StateFlow<String?> = _phaseBanner

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked

    fun prepareBattle(params: BattleParams? = null) {
        resetAndSpawn(params?.enemies ?: emptyList())
        selectedSkill = null
        pendingTargets.clear()
        animCollector.drain()
        _animationEvent.value = null
        _phaseBanner.value = null
        _isLocked.value = false
        _state.value = snapshot(TurnPhase.PlayerPhase)
    }

    /** Called when the player picks a skill from the bottom sheet. */
    fun onSkillSelected(skillId: String) {
        pendingTargets.clear()
        selectedSkill = skillId
        _state.value = snapshot(TurnPhase.PlayerPhase)
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

        if (s.selectedSkill != null && cell in s.attackableTiles) {
            val target = s.units.find { it.position.col == col && it.position.row == row }
                ?: return
            val skillInfo = s.availableSkills.find { it.id == s.selectedSkill }
            val maxTargets = skillInfo?.maxTargets ?: 1

            if (maxTargets <= 1) {
                // Single-target: fire immediately
                onPlayerAction(PlayerAction.UseSkill(s.selectedSkill!!, target.entityId))
            } else {
                // Multi-target: accumulate picks — the same target may be selected multiple times
                pendingTargets += target.entityId
                if (pendingTargets.size >= maxTargets) {
                    onPlayerAction(PlayerAction.UseSkillOnTargets(s.selectedSkill!!, pendingTargets.toList()))
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
                gameLoop.runEnemyTurns(world)
                gameLoop.runEnvironmentTick(world)
                animCollector.drain()
            }

            playAnimations(enemyAnims)
            _state.value = snapshot(TurnPhase.PlayerPhase)

            if (_state.value.isBattleOver) {
                _isLocked.value = false
                return@launch
            }

            _phaseBanner.value = "Your Turn"
            delay(500L)
            _phaseBanner.value = null

            _isLocked.value = false
        }
    }

    private suspend fun playAnimations(events: List<AnimationEvent>) {
        for (event in events) {
            _animationEvent.value = event
            val durationMs = if (event is AnimationEvent.UnitMove) MOVE_ANIM_MS else ANIM_DURATION_MS
            delay(durationMs)
        }
        _animationEvent.value = null
    }

    private fun snapshot(phase: TurnPhase): BattleUiState =
        world.snapshotBattle(phase, battleLog.snapshot(), selectedSkill, skillRegistry, pendingTargets.toList())
}
