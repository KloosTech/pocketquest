package de.jackbeback.pocketquest.game.snapshot

/**
 * Rolling battle log shared between systems and the ViewModel.
 * Systems append via [add]; the ViewModel reads via [snapshot].
 * Max 20 entries — oldest are dropped first.
 */
class BattleLog {
    private val entries = mutableListOf<String>()

    fun add(message: String) {
        if (entries.size >= 20) entries.removeAt(0)
        entries.add(message)
    }

    fun snapshot(): List<String> = entries.toList()

    fun clear() { entries.clear() }
}
