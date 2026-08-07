package de.jackbeback.pocketquest.ui

import androidx.compose.ui.graphics.Color
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.GameEvent
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.Rejection

/** doc15's "include the dice" plus the user's explicit color-coding ask: damage red, heal green, buffs/status yellow. */
enum class LogCategory { Damage, Heal, Status, Death, Blocked, Info }

data class LogEntry(val text: String, val category: LogCategory)

fun LogCategory.color(): Color = when (this) {
    LogCategory.Damage -> Color(0xFFB71C1C)
    LogCategory.Heal -> Color(0xFF2E7D32)
    LogCategory.Status -> Color(0xFFF9A825)
    LogCategory.Death -> Color(0xFF4E0A0A)
    LogCategory.Blocked -> Color(0xFFEF6C00)
    LogCategory.Info -> Color(0xFF9A8764) // matches App.kt's INK_FAINT
}

private fun GameState.nameOf(id: EntityId, cat: Catalog): String =
    byId[id]?.let { cat.archetype(it.archetype).name } ?: "someone"

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
            val verdict = if (event.hit) "hit" else "miss"
            LogEntry("${name(event.attacker)} attacks ${name(event.target)}: $verdict ($total vs AC ${event.ac})", LogCategory.Info)
        }
        is GameEvent.DamageTaken -> LogEntry("${name(event.target)} takes ${event.amount} ${event.damageType.name.lowercase()} damage.", LogCategory.Damage)
        is GameEvent.Healed -> {
            val by = event.source?.let { if (it != event.target) " by ${name(it)}" else "" } ?: ""
            LogEntry("${name(event.target)} heals ${event.amount} HP$by.", LogCategory.Heal)
        }
        is GameEvent.Died -> null
        is GameEvent.Downed -> LogEntry("${name(event.target)} is downed!", LogCategory.Death)
        is GameEvent.Revived -> LogEntry("${name(event.target)} is revived!", LogCategory.Heal)
        is GameEvent.StatusApplied -> LogEntry("${name(event.target)} gains ${event.status.raw} ×${event.stacks}.", LogCategory.Status)
        is GameEvent.StatusExpired -> LogEntry("${event.status.raw} fades from ${name(event.target)}.", LogCategory.Status)
        is GameEvent.SaveRolled -> {
            val total = event.d20 + event.mod
            val verdict = if (event.success) "succeeds" else "fails"
            LogEntry("${name(event.target)}'s ${event.ability} save $verdict ($total vs DC ${event.dc}).", LogCategory.Info)
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
        is GameEvent.ActionStarted -> LogEntry("${name(event.who)} uses ${event.actionId.raw}.", LogCategory.Info)
        is GameEvent.TurnStarted -> LogEntry("— ${name(event.who)}'s turn (round ${event.round}) —", LogCategory.Info)
        is GameEvent.TurnEnded -> null // TurnStarted already marks the boundary; a matching "ended" line is redundant noise every single turn.
        is GameEvent.ManaRefilled -> LogEntry("${name(event.who)}'s mana is refilled.", LogCategory.Heal)
        is GameEvent.ResourcesSpent -> null // pure bookkeeping — the action's own ActionStarted/AttackRolled line already says what happened.
        is GameEvent.ResourcesReset -> null // fires every single turn boundary; would drown the log in noise doc15 never asks for.
        is GameEvent.MoveStepped -> null // one line per tile stepped would be far noisier than the walk itself is informative.
        is GameEvent.Fizzled -> LogEntry("${event.effect} fizzles: ${describeRejection(event.reason)}", LogCategory.Blocked)
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
