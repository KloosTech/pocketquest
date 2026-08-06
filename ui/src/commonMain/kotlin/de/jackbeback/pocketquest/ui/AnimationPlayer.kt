package de.jackbeback.pocketquest.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * doc07's AnimationPlayer, with one correction found by actually implementing
 * it: the doc's sketch flips `isPlaying` to false as soon as the queue is
 * *dequeued*, which for a [Timing.Parallel] beat is right after `launch {}`
 * fires it off — not after it finishes. `awaitDrained()` could then return
 * while a Parallel beat (e.g. a floating damage number) was still running.
 * Fixed with a pending-count instead: every beat increments it on enqueue
 * and decrements it when its own `play()` actually completes, regardless of
 * timing, so "drained" means every beat is truly done, not just dequeued.
 * `pending` is a plain `Int`, not atomic — safe only because this runs on
 * a single-threaded (UI) dispatcher, same assumption Compose's own
 * coroutine scaffolding makes.
 */
class AnimationPlayer(private val world: VisualWorld) {
    private val queue = Channel<Beat>(Channel.UNLIMITED)
    private var pending = 0
    val isPlaying = MutableStateFlow(false)

    suspend fun run() = coroutineScope {
        for (beat in queue) {
            when (beat.timing) {
                Timing.Instant, Timing.Blocking -> {
                    beat.play(world)
                    settleOne()
                }
                Timing.Parallel -> launch {
                    beat.play(world)
                    settleOne()
                }
            }
        }
    }

    private fun settleOne() {
        pending--
        if (pending == 0) isPlaying.value = false
    }

    fun enqueue(beats: List<Beat>) {
        if (beats.isEmpty()) return
        pending += beats.size
        isPlaying.value = true
        beats.forEach { queue.trySend(it) }
    }

    suspend fun awaitDrained() {
        isPlaying.first { !it }
    }

    /**
     * Closes the queue so [run] returns once every already-enqueued beat —
     * including its Parallel children — has actually finished, instead of
     * looping forever. Needed for a bounded "play this batch, then done"
     * caller (like :app's replay-style demo); a persistent player that
     * keeps taking new enqueue() calls across a whole session should not
     * call this.
     */
    fun close() = queue.close()
}
