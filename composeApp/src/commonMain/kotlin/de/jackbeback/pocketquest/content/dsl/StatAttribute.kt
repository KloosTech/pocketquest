package de.jackbeback.pocketquest.content.dsl

enum class StatAttribute { STR, DEX, CON, INT, WIS, CHA }

/** D&D floor modifier: 10→0, 12→+1, 8→-1, 11→0, 9→-1, etc. */
fun attributeModifier(statValue: Int): Int = (statValue - 10).floorDiv(2)
