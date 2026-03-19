package de.jackbeback.pocketquest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Smoke tests that verify the basic Kotlin test infrastructure is working.
 * These are intentionally trivial — all meaningful logic lives in the dedicated
 * test files (DiceTest, WorldTest, CombatSystemTest, etc.).
 */
class ComposeAppCommonTest {

    @Test
    fun `basic arithmetic works in the test environment`() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun `string operations work in commonTest`() {
        val packageName = "de.jackbeback.pocketquest"
        assertTrue(packageName.startsWith("de.jackbeback"))
    }
}
