package de.jackbeback.pocketquest.content.registry

import de.jackbeback.pocketquest.content.dsl.SkillTemplate

/** Provides skill lookup by ID. Registered as a Koin singleton. */
class SkillRegistry(skills: List<SkillTemplate>) {
    private val map = skills.associateBy { it.id }
    fun find(id: String): SkillTemplate? = map[id]
}
