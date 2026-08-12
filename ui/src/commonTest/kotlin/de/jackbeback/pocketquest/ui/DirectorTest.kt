package de.jackbeback.pocketquest.ui

import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectorTest {

    // choreograph() only needs a real GameState/Catalog for ActionStarted's projectile-travel beat
    // (docs/24-projectile-travel-animation.md) — every other event ignores both, this bare state is
    // enough for those.
    private val state = GameState(
        entities = emptyList(), map = BattleMap(1, 1),
        turn = TurnState(round = 1, order = emptyList(), activeIndex = 0, phase = TurnPhase.Main),
        rng = RngState(seed = 0L),
    )
    private val catalog = Catalog()

    /** KNOWN_ISSUES.md #1 — choreograph() had no MoveStepped branch at all; movement never animated. */
    @Test
    fun moveSteppedProducesABlockingWalkBeat() {
        val event = GameEvent.MoveStepped(EntityId(0), GridPos(0, 0), GridPos(1, 0))
        val beats = choreograph(event, state, catalog)
        assertEquals(1, beats.size)
        assertIs<Timing.Blocking>(beats.single().timing)
    }
}
