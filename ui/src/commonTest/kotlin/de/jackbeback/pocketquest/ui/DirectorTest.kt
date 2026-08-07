package de.jackbeback.pocketquest.ui

import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectorTest {

    /** KNOWN_ISSUES.md #1 — choreograph() had no MoveStepped branch at all; movement never animated. */
    @Test
    fun moveSteppedProducesABlockingWalkBeat() {
        val event = GameEvent.MoveStepped(EntityId(0), GridPos(0, 0), GridPos(1, 0))
        val beats = choreograph(event)
        assertEquals(1, beats.size)
        assertIs<Timing.Blocking>(beats.single().timing)
    }
}
