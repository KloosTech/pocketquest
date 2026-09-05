package de.jackbeback.pocketquest.ui

import androidx.compose.ui.graphics.Color
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Rejection
import de.jackbeback.pocketquest.core.model.RollBreakdown
import de.jackbeback.pocketquest.ui.ink.ACCENT
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.FATAL
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.OK
import de.jackbeback.pocketquest.ui.ink.WARNING

/** doc15's "include the dice" plus the user's explicit color-coding ask: damage red, heal green, buffs/status yellow. */
enum class LogCategory { Damage, Heal, Status, Death, Blocked, Info }

data class LogEntry(val text: String, val category: LogCategory)

fun LogCategory.color(): Color = when (this) {
    LogCategory.Damage -> DANGER
    LogCategory.Heal -> OK
    LogCategory.Status -> ACCENT
    LogCategory.Death -> FATAL
    LogCategory.Blocked -> WARNING
    LogCategory.Info -> INK_FAINT
}

private fun GameState.nameOf(id: EntityId, cat: Catalog): String =
    byId[id]?.let { cat.archetype(it.archetype).name } ?: "someone"

/** "d20:14 +3 Str +1 Weapon" — the raw die plus every labeled [RollBreakdown] term, so a log line shows what actually made up the total instead of just the total itself. */
private fun rollFormula(d20: Int, breakdown: RollBreakdown): String {
    val terms = breakdown.terms.filter { it.flat != 0 }.joinToString(" ") { term ->
        "${if (term.flat > 0) "+" else ""}${term.flat} ${term.label}"
    }
    return if (terms.isEmpty()) "d20:$d20" else "d20:$d20 $terms"
}

/**
 * doc15's battle log: "rendered from the GameEvent list... include the dice." Full sentences, not a
 * raw event dump — every branch here is a deliberate phrasing choice, not a `toString()`. Returns
 * null for events not worth a line: [GameEvent.Died] always fires alongside [GameEvent.Downed] on
 * the same 0-HP transition today (nothing removes an entity from the board yet), so it would be a
 * pure duplicate of Downed's own line — the same reasoning that gave Died no Director beat either.
 */
fun formatEvent(event: GameEvent, state: GameState, cat: Catalog): LogEntry? {
    fun name(id: EntityId) = state.nameOf(id, cat)
    return when (event) {
        is GameEvent.AttackRolled -> {
            val total = event.d20 + event.mod
            val verdict = when {
                event.critical -> "critical hit!"
                event.hit -> "hit"
                else -> "miss"
            }
            LogEntry("${name(event.attacker)} attacks ${name(event.target)}: $verdict (${rollFormula(event.d20, event.breakdown)} = $total vs AC ${event.ac})", LogCategory.Info)
        }
        is GameEvent.DamageTaken -> LogEntry("${name(event.target)} takes ${event.amount} ${event.damageType.name.lowercase()} damage.", LogCategory.Damage)
        is GameEvent.DamageRolled -> {
            val dice = event.rolls.joinToString("+")
            val mod = if (event.modifier != 0) (if (event.modifier > 0) " +${event.modifier}" else " ${event.modifier}") else ""
            LogEntry("Damage roll: [$dice]$mod = ${event.rolls.sum() + event.modifier}.", LogCategory.Info)
        }
        is GameEvent.Healed -> {
            val by = event.source?.let { if (it != event.target) " by ${name(it)}" else "" } ?: ""
            LogEntry("${name(event.target)} heals ${event.amount} HP$by.", LogCategory.Heal)
        }
        is GameEvent.Died -> null
        is GameEvent.Downed -> LogEntry("${name(event.target)} is downed!", LogCategory.Death)
        is GameEvent.Revived -> LogEntry("${name(event.target)} is revived!", LogCategory.Heal)
        is GameEvent.StatusApplied -> LogEntry("${name(event.target)} gains ${cat.statusDef(event.status).name} ×${event.stacks}.", LogCategory.Status)
        is GameEvent.StatusExpired -> LogEntry("${cat.statusDef(event.status).name} fades from ${name(event.target)}.", LogCategory.Status)
        is GameEvent.SaveRolled -> {
            val total = event.d20 + event.mod
            val verdict = if (event.success) "succeeds" else "fails"
            LogEntry("${name(event.target)}'s ${event.ability} save $verdict (${rollFormula(event.d20, event.breakdown)} = $total vs DC ${event.dc}).", LogCategory.Info)
        }
        is GameEvent.ConcentrationCheckRolled -> {
            val total = event.roll + event.mod
            val verdict = if (event.success) "holds" else "breaks"
            LogEntry("${name(event.who)}'s concentration $verdict ($total vs DC ${event.dc}).", LogCategory.Info)
        }
        is GameEvent.ConcentrationStarted -> LogEntry("${name(event.who)} begins concentrating.", LogCategory.Info)
        is GameEvent.ConcentrationBroken -> LogEntry("${name(event.who)}'s concentration breaks.", LogCategory.Info)
        is GameEvent.DamageRedirected -> LogEntry("Damage is redirected from ${name(event.from)} to ${name(event.to)}!", LogCategory.Info)
        is GameEvent.ReactionTriggered -> LogEntry("${name(event.who)} reacts!", LogCategory.Info)
        is GameEvent.ActionStarted -> LogEntry("${name(event.who)} uses ${cat.actionDef(event.actionId).name}.", LogCategory.Info)
        is GameEvent.TurnStarted -> LogEntry("— ${name(event.who)}'s turn (round ${event.round}) —", LogCategory.Info)
        is GameEvent.TurnEnded -> null // TurnStarted already marks the boundary; a matching "ended" line is redundant noise every single turn.
        is GameEvent.ManaRefilled -> LogEntry("${name(event.who)}'s mana is refilled.", LogCategory.Heal)
        is GameEvent.ResourcesSpent -> null // pure bookkeeping — the action's own ActionStarted/AttackRolled line already says what happened.
        is GameEvent.ResourcesReset -> null // fires every single turn boundary; would drown the log in noise doc15 never asks for.
        is GameEvent.MoveStepped -> null // one line per tile stepped would be far noisier than the walk itself is informative.
        is GameEvent.Teleported -> LogEntry("${name(event.who)} teleports away!", LogCategory.Info)
        is GameEvent.EntitySpawned -> LogEntry("${cat.archetype(event.archetype).name} arrives.", LogCategory.Info)
        is GameEvent.EntityDestroyed -> LogEntry("${name(event.target)} is destroyed.", LogCategory.Death)
        is GameEvent.Fizzled -> LogEntry("${event.effect} fizzles: ${describeRejection(event.reason)}", LogCategory.Blocked)
        // docs/36-map-triggers.md: the text box itself is the primary UI (TextBoxOverlay) — this is
        // just a log trail so it's still readable after being dismissed.
        is GameEvent.MessageShown -> LogEntry(event.text, LogCategory.Info)
        // docs/37-lootable-containers.md: exploration's own openLootIfAny call site logs directly
        // (no resolver step to hook a formatEvent call off of) — this covers the combat call site.
        is GameEvent.LootOpened -> LogEntry("Found ${cat.lootDef(event.loot).name.ifBlank { event.loot.raw }}.", LogCategory.Info)
    }
}

private fun describeRejection(reason: Rejection): String = when (reason) {
    is Rejection.NotEnoughMana -> "not enough mana (need ${reason.need}, have ${reason.have})"
    is Rejection.NotEnoughAp -> "not enough AP (need ${reason.need}, have ${reason.have})"
    is Rejection.TargetMissing -> "target is gone"
    is Rejection.Blocked -> "the path is blocked"
    Rejection.NotYourTurn -> "it isn't their turn"
    Rejection.QuickAlreadyUsed -> "quick action already used"
    is Rejection.OutOfRange -> "out of range (${reason.distance}/${reason.max})"
    Rejection.NoLineOfSight -> "no line of sight"
    Rejection.NoLegalTarget -> "no legal target"
    Rejection.Downed -> "they're downed"
    Rejection.Prevented -> "prevented"
}
