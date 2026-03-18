package de.jackbeback.pocketquest.content.definitions

/**
 * A passive item held for an entire run.
 * Relics are offered as a reward after each won encounter — pick 1 of 3.
 * Effects are applied at the start of every battle by AppModule's resetAndSpawn lambda.
 */
data class Relic(
    val id: String,
    val name: String,
    val description: String,
    val effect: RelicEffect,
)

sealed class RelicEffect {
    /** Adds [amount] to the player's max HP (and current HP) at the start of each battle. */
    data class BonusMaxHp(val amount: Int) : RelicEffect()

    /** Adds [amount] to the player's max mana (and current mana) at the start of each battle. */
    data class BonusMana(val amount: Int) : RelicEffect()

    /** Multiplies all XP earned from a battle by [multiplier]. Applied in App.kt when gainExp is called. */
    data class XpMultiplier(val multiplier: Float) : RelicEffect()

    /** The player's HP is restored to max at the start of every battle, ignoring saved HP. */
    object FullHpOnBattleStart : RelicEffect()

    /** The player's mana is restored to max at the start of every battle, ignoring saved mana. */
    object FullManaOnBattleStart : RelicEffect()
}

val allRelics: List<Relic> = listOf(
    Relic(
        id          = "ancient_tome",
        name        = "Ancient Tome",
        description = "+20 max HP",
        effect      = RelicEffect.BonusMaxHp(20),
    ),
    Relic(
        id          = "iron_will",
        name        = "Iron Will",
        description = "+40 max HP",
        effect      = RelicEffect.BonusMaxHp(40),
    ),
    Relic(
        id          = "war_trophy",
        name        = "War Trophy",
        description = "+30 max HP",
        effect      = RelicEffect.BonusMaxHp(30),
    ),
    Relic(
        id          = "mage_stone",
        name        = "Mage Stone",
        description = "+2 max mana",
        effect      = RelicEffect.BonusMana(2),
    ),
    Relic(
        id          = "well_of_power",
        name        = "Well of Power",
        description = "+4 max mana",
        effect      = RelicEffect.BonusMana(4),
    ),
    Relic(
        id          = "scholars_ring",
        name        = "Scholar's Ring",
        description = "Earn 1.5× XP",
        effect      = RelicEffect.XpMultiplier(1.5f),
    ),
    Relic(
        id          = "blood_pact",
        name        = "Blood Pact",
        description = "Start each battle at full HP",
        effect      = RelicEffect.FullHpOnBattleStart,
    ),
    Relic(
        id          = "arcane_focus",
        name        = "Arcane Focus",
        description = "Start each battle with full mana",
        effect      = RelicEffect.FullManaOnBattleStart,
    ),
)
