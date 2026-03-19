package de.jackbeback.pocketquest

import de.jackbeback.pocketquest.ecs.core.EventBus
import de.jackbeback.pocketquest.ecs.core.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [EventBus] — the two-phase event dispatch mechanism used by all game systems.
 *
 * The bus operates in two stages:
 *  1. **emit** — events are queued but not yet dispatched.
 *  2. **flush** — the queue is drained and each event is delivered to every registered handler.
 *
 * This design prevents infinite loops (systems don't see events they emit during their own tick)
 * and makes event ordering deterministic across systems.
 */
class EventBusTest {

    // Test event types
    private data class TestEvent(val value: Int) : GameEvent
    private data class OtherEvent(val label: String) : GameEvent

    // ── Basic emit → flush cycle ───────────────────────────────────────────────

    @Test
    fun `handler is called after flush`() {
        val bus = EventBus()
        var received: TestEvent? = null
        bus.on<TestEvent> { received = it }

        bus.emit(TestEvent(42))
        assertEquals(null, received, "Handler must not fire before flush")

        bus.flush()
        assertEquals(TestEvent(42), received, "Handler must fire after flush")
    }

    @Test
    fun `multiple events are all dispatched in order`() {
        val bus = EventBus()
        val received = mutableListOf<Int>()
        bus.on<TestEvent> { received += it.value }

        bus.emit(TestEvent(1))
        bus.emit(TestEvent(2))
        bus.emit(TestEvent(3))
        bus.flush()

        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun `queue is empty after flush — second flush is a no-op`() {
        val bus = EventBus()
        var callCount = 0
        bus.on<TestEvent> { callCount++ }

        bus.emit(TestEvent(10))
        bus.flush()
        bus.flush()  // second flush — queue already cleared

        assertEquals(1, callCount, "Handler should have been called exactly once")
    }

    // ── Multiple handlers ──────────────────────────────────────────────────────

    @Test
    fun `multiple handlers all receive the same event`() {
        val bus = EventBus()
        val log = mutableListOf<String>()
        bus.on<TestEvent> { log += "handler-A:${it.value}" }
        bus.on<TestEvent> { log += "handler-B:${it.value}" }

        bus.emit(TestEvent(7))
        bus.flush()

        assertTrue("handler-A:7" in log)
        assertTrue("handler-B:7" in log)
    }

    // ── Type safety ───────────────────────────────────────────────────────────

    @Test
    fun `handler only receives events of its own type`() {
        val bus = EventBus()
        val testEvents = mutableListOf<Int>()
        val otherEvents = mutableListOf<String>()

        bus.on<TestEvent> { testEvents += it.value }
        bus.on<OtherEvent> { otherEvents += it.label }

        bus.emit(TestEvent(1))
        bus.emit(OtherEvent("hello"))
        bus.emit(TestEvent(2))
        bus.flush()

        assertEquals(listOf(1, 2), testEvents)
        assertEquals(listOf("hello"), otherEvents)
    }

    @Test
    fun `no events emitted means handler is never called`() {
        val bus = EventBus()
        var called = false
        bus.on<TestEvent> { called = true }

        bus.flush()

        assertTrue(!called, "Handler must not be called when no events were emitted")
    }

    // ── No-handler edge case ──────────────────────────────────────────────────

    @Test
    fun `emitting event with no matching handler does not throw`() {
        val bus = EventBus()
        // No handler registered — just verify no exception
        bus.emit(TestEvent(99))
        bus.flush()
    }

    // ── Handler registration before emit ─────────────────────────────────────

    @Test
    fun `handler registered after emit still fires on flush`() {
        val bus = EventBus()
        bus.emit(TestEvent(5))

        var received: Int? = null
        bus.on<TestEvent> { received = it.value }

        bus.flush()
        assertEquals(5, received)
    }

    // ── Accumulated events across multiple emit calls ─────────────────────────

    @Test
    fun `events accumulated over several emit calls are all flushed together`() {
        val bus = EventBus()
        val results = mutableListOf<Int>()
        bus.on<TestEvent> { results += it.value }

        for (i in 1..5) bus.emit(TestEvent(i))
        bus.flush()

        assertEquals(listOf(1, 2, 3, 4, 5), results)
    }
}
