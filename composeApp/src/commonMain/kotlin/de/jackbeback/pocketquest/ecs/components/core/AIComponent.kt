package de.jackbeback.pocketquest.ecs.components.core

enum class AIStrategy { Aggressive, Defensive, Passive }

data class AIComponent(
    val strategy: AIStrategy,
    /** Skill the AI prefers to use when in range. */
    val preferredSkillId: String = "basic_attack",
)
