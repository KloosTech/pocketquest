package de.jackbeback.pocketquest.core.progression

import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionRecord
import de.jackbeback.pocketquest.core.meta.ChampionStatus
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.meta.Unlock
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.run.MemberId
import de.jackbeback.pocketquest.core.run.PartyMember
import de.jackbeback.pocketquest.core.run.atFullHealth

/** docs/10-game-loop.md's solo-gate: a party stays capped at 1 until `Unlock.PartyMode` is earned. */
fun maxPartySize(meta: MetaState): Int = if (Unlock.PartyMode in meta.unlocks) 3 else 1

/**
 * docs/12-progression.md's "Bootstrapping the roster: the first character" — the one case a
 * `ChampionRecord` is created before any run rather than picked from an existing roster, entering
 * directly as `OnRun` ("not Available first, since it has nowhere to be available yet").
 */
fun createChampion(meta: MetaState, id: ChampionId, name: String, archetype: ArchetypeId): MetaState {
    require(id !in meta.roster) { "Champion ${id.raw} already exists" }
    val record = ChampionRecord(id = id, name = name, archetype = archetype, status = ChampionStatus.OnRun)
    return meta.copy(roster = meta.roster + (id to record))
}

sealed interface PartyFormationRejection {
    data class TooManyChampions(val requested: Int, val max: Int) : PartyFormationRejection
    data object EmptyParty : PartyFormationRejection
    data class ChampionNotAvailable(val id: ChampionId, val status: ChampionStatus) : PartyFormationRejection
}

sealed interface PartyFormationResult {
    data class Formed(val meta: MetaState, val party: List<PartyMember>) : PartyFormationResult
    data class Rejected(val reasons: List<PartyFormationRejection>) : PartyFormationResult
}

/**
 * Picks up to [maxPartySize] `Available` Champions off the roster for a new run, per doc10's "pick
 * up to 3 Champions from the roster upfront, no mid-run recruitment" — marks them `OnRun` and hands
 * back fresh, full-health `PartyMember`s built from their persisted equipment.
 */
fun formParty(meta: MetaState, championIds: List<ChampionId>, cat: Catalog): PartyFormationResult {
    val rejections = mutableListOf<PartyFormationRejection>()
    if (championIds.isEmpty()) rejections += PartyFormationRejection.EmptyParty
    val max = maxPartySize(meta)
    if (championIds.size > max) rejections += PartyFormationRejection.TooManyChampions(championIds.size, max)
    val records = championIds.map { meta.roster.getValue(it) }
    for (record in records) {
        if (record.status != ChampionStatus.Available) {
            rejections += PartyFormationRejection.ChampionNotAvailable(record.id, record.status)
        }
    }
    if (rejections.isNotEmpty()) return PartyFormationResult.Rejected(rejections)

    val updatedRoster = meta.roster + records.associate { it.id to it.copy(status = ChampionStatus.OnRun) }
    val party = records.map { record ->
        val member = PartyMember(
            memberId = MemberId(record.id.raw), name = record.name, archetype = record.archetype,
            hp = 0, mana = 0, equipment = record.equipment, controller = Controller.Human,
        )
        member.atFullHealth(cat)
    }
    return PartyFormationResult.Formed(meta.copy(roster = updatedRoster), party)
}
