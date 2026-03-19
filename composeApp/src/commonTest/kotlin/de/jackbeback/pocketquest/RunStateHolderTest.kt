package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.game.run.RunStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [RunStateHolder] — the single source of truth for all roguelike progression.
 *
 * The holder manages two layers of state:
 *  - **RunScopedState** — active only during a run; wiped on death
 *  - **PersistentState** — survives across all runs (unlocked characters, inventory)
 *
 * Key progression rules:
 *  - XP threshold for level N: N × 100  (level 1→2 = 100 XP, 2→3 = 200 XP, …)
 *  - One level-up per [gainExp] call; surplus XP carries over
 *  - [previewGainExp] reads state without modifying it
 *  - Difficulty counter is incremented once per won encounter
 */
class RunStateHolderTest {

    // ── Run lifecycle ─────────────────────────────────────────────────────────

    @Test
    fun `run is null before startRun is called`() {
        val holder = RunStateHolder()
        assertNull(holder.run.value)
    }

    @Test
    fun `startRun initialises run state with correct character id`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val run = holder.run.value
        assertNotNull(run)
        assertEquals("wizard", run.characterTemplateId)
    }

    @Test
    fun `startRun sets default level 1 and zero XP`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        assertEquals(1, holder.run.value?.level)
        assertEquals(0, holder.run.value?.exp)
    }

    @Test
    fun `startRun sets default areaNumber of 1`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        assertEquals(1, holder.run.value?.areaNumber)
    }

    @Test
    fun `startRun initialises with empty relics list`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        assertTrue(holder.run.value?.relics?.isEmpty() == true)
    }

    @Test
    fun `resetRun sets run to null`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        holder.resetRun()
        assertNull(holder.run.value)
    }

    // ── XP and levelling ──────────────────────────────────────────────────────

    @Test
    fun `gainExp accumulates XP without levelling up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val result = holder.gainExp(50)

        assertFalse(result.didLevelUp)
        assertEquals(1, result.newLevel)
        assertEquals(50, holder.run.value?.exp)
    }

    @Test
    fun `gainExp triggers level-up at 100 XP threshold for level 1`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val result = holder.gainExp(100)

        assertTrue(result.didLevelUp)
        assertEquals(2, result.newLevel)
        assertEquals(2, holder.run.value?.level)
    }

    @Test
    fun `surplus XP above threshold carries over after level-up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.gainExp(130)   // 100 threshold + 30 surplus

        assertEquals(30, holder.run.value?.exp, "30 surplus XP must carry over to next level")
        assertEquals(2, holder.run.value?.level)
    }

    @Test
    fun `level 2 threshold is 200 XP`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        holder.gainExp(100)   // level 1→2
        assertEquals(2, holder.run.value?.level)

        val result = holder.gainExp(199)   // not enough for 2→3

        assertFalse(result.didLevelUp)
        assertEquals(2, result.newLevel)
    }

    @Test
    fun `gainExp at exactly level 2 threshold triggers second level-up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        holder.gainExp(100)   // → level 2, exp = 0

        val result = holder.gainExp(200)   // exactly level 2 threshold

        assertTrue(result.didLevelUp)
        assertEquals(3, result.newLevel)
    }

    @Test
    fun `gainExp accumulates across multiple calls before level-up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.gainExp(40)
        holder.gainExp(30)
        val result = holder.gainExp(30)   // total = 100 → level up

        assertTrue(result.didLevelUp)
        assertEquals(2, result.newLevel)
    }

    @Test
    fun `gainExp with no active run returns no level-up at level 1`() {
        val holder = RunStateHolder()
        // No startRun called
        val result = holder.gainExp(999)
        assertFalse(result.didLevelUp)
        assertEquals(1, result.newLevel)
    }

    // ── previewGainExp ────────────────────────────────────────────────────────

    @Test
    fun `previewGainExp does not modify state`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val before = holder.run.value?.exp
        holder.previewGainExp(80)
        val after = holder.run.value?.exp

        assertEquals(before, after, "previewGainExp must not change XP state")
    }

    @Test
    fun `previewGainExp correctly predicts level-up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val result = holder.previewGainExp(100)

        assertTrue(result.didLevelUp)
        assertEquals(2, result.newLevel)
    }

    @Test
    fun `previewGainExp correctly predicts no level-up`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        val result = holder.previewGainExp(50)

        assertFalse(result.didLevelUp)
        assertEquals(1, result.newLevel)
    }

    // ── Difficulty counter ────────────────────────────────────────────────────

    @Test
    fun `incrementDifficulty increases counter by 1`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.incrementDifficulty()

        assertEquals(1, holder.run.value?.difficultyCounter)
    }

    @Test
    fun `incrementDifficulty accumulates over multiple calls`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        repeat(5) { holder.incrementDifficulty() }

        assertEquals(5, holder.run.value?.difficultyCounter)
    }

    // ── Player state persistence ──────────────────────────────────────────────

    @Test
    fun `savePlayerState stores HP and mana`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.savePlayerState(hp = 12, mana = 8)

        assertEquals(12, holder.run.value?.playerHp)
        assertEquals(8, holder.run.value?.playerMana)
    }

    @Test
    fun `savePlayerState overwrites previous saved state`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.savePlayerState(hp = 20, mana = 15)
        holder.savePlayerState(hp = 7, mana = 3)

        assertEquals(7, holder.run.value?.playerHp)
        assertEquals(3, holder.run.value?.playerMana)
    }

    @Test
    fun `initial playerHp and playerMana are null`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        assertNull(holder.run.value?.playerHp)
        assertNull(holder.run.value?.playerMana)
    }

    // ── Relics ────────────────────────────────────────────────────────────────

    @Test
    fun `addRelic appends relic id to the list`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.addRelic("ancient_tome")

        assertEquals(listOf("ancient_tome"), holder.run.value?.relics)
    }

    @Test
    fun `addRelic accumulates multiple relics`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.addRelic("ancient_tome")
        holder.addRelic("mage_stone")
        holder.addRelic("scholars_ring")

        assertEquals(
            listOf("ancient_tome", "mage_stone", "scholars_ring"),
            holder.run.value?.relics
        )
    }

    // ── Area progression ──────────────────────────────────────────────────────

    @Test
    fun `startNextArea increments areaNumber`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        holder.startNextArea()

        assertEquals(2, holder.run.value?.areaNumber)
    }

    @Test
    fun `startNextArea accumulates correctly across multiple areas`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")

        repeat(4) { holder.startNextArea() }

        assertEquals(5, holder.run.value?.areaNumber)
    }

    // ── Persistent state ──────────────────────────────────────────────────────

    @Test
    fun `persistent state has wizard unlocked by default`() {
        val holder = RunStateHolder()
        assertTrue("wizard" in holder.persistent.unlockedCharacterIds)
    }

    @Test
    fun `persistent state survives a run reset`() {
        val holder = RunStateHolder()
        holder.startRun("wizard")
        holder.resetRun()

        assertNull(holder.run.value)
        assertTrue("wizard" in holder.persistent.unlockedCharacterIds)
    }
}
