package de.jackbeback.pocketquest.core.model

import kotlinx.serialization.Serializable

/**
 * Every roll advances [calls]. Same seed + same call count always produces
 * the same outcome. The actual dice algorithm lives in :core:rules, which
 * seeds its own PRNG from this state rather than touching kotlin.random's
 * ambient instance.
 */
@Serializable
data class RngState(val seed: Long, val calls: Long = 0)
