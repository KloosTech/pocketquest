package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Inventory
import de.jackbeback.pocketquest.core.model.LootId
import de.jackbeback.pocketquest.core.model.NodeId
import de.jackbeback.pocketquest.core.rules.resolver.Resolver
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Schema version for the serialized [RunState] blob — independent of [de.jackbeback.pocketquest.data]'s Room table version and of `:data`'s combat-snapshot `CURRENT_SCHEMA` (docs/11-run-state.md: "combat and run shapes will not change in lockstep"). */
const val CURRENT_RUN_SCHEMA = 1

@JvmInline @Serializable value class RunId(val raw: String)

/**
 * Deliberately **not** `core:meta`'s `ChampionId` — `:core:run` and `:core:meta` are siblings, not
 * parent/child (docs/10-game-loop.md), so this module doesn't depend on that one at all. Whatever
 * orchestrates picking a roster into a run (Pass 7 of this feature's implementation plan) is
 * responsible for keeping `MemberId.raw == ChampionId.raw` by convention when it builds a
 * `PartyMember` from a `ChampionRecord` — the two types are never unified into one.
 */
@JvmInline @Serializable value class MemberId(val raw: String)

/**
 * docs/11-run-state.md — the layer between Meta and an encounter. Everything here outlives a
 * single `GameState`/`Resolver` (created when an encounter starts, discarded when it ends) but is
 * itself discarded when the run ends — `RunOutcome.Success` hands the surviving party back to
 * `:core:meta`'s roster (docs/12-progression.md), `RunOutcome.Failure` triggers permadeath there.
 */
@Serializable
data class RunState(
    val runId: RunId,
    val seed: Long,
    val rng: RngState,
    val act: Int,
    val graph: NodeGraph,
    val position: NodeId,
    val visited: Set<NodeId> = emptySet(),
    val party: List<PartyMember>,
    val inventory: Inventory = Inventory(),
    /** Held run-side only — deposited into `:core:meta`'s permanent bank on `RunOutcome.Success`, forfeited on `Failure` (docs/10-game-loop.md's Permadeath section). */
    val gold: Int = 0,
    val encounter: EncounterHandle? = null,
    val outcome: RunOutcome? = null,
    val schemaVersion: Int = CURRENT_RUN_SCHEMA,
    /**
     * docs/38-loot-reveal-screen.md: set by `finishEncounter` — one [PendingLoot] per opened
     * container, already rolled but not yet granted. Non-empty gates `:ui`'s `RunScreen` into the
     * loot-reveal screen ahead of the node picker; cleared back to empty once every entry is
     * `revealed` and the player continues.
     */
    val pendingLootReveal: List<PendingLoot> = emptyList(),
)

/**
 * docs/38-loot-reveal-screen.md: one opened container's outcome, mid-reveal. [at] (the placement's
 * `GridPos`, already unique per encounter) is the stable key `:ui` taps against — not an index, so
 * partial/out-of-order reveals never point at the wrong row. [item] is fixed the moment
 * `finishEncounter` rolls it; [revealed]/[lost] are the only fields a later `revealLoot` call
 * changes.
 */
@Serializable
data class PendingLoot(val at: GridPos, val loot: LootId, val item: ItemId?, val revealed: Boolean = false, val lost: Boolean = false)

/**
 * The run-layer's authority on a character between encounters — `Entity.health`/`resources` is
 * authoritative only while `encounter != null` (see Invariant 8). No `Progression`/level/XP
 * (docs/10-game-loop.md "No leveling") — `equipment` is the only thing that changes a member
 * beyond their archetype baseline.
 */
@Serializable
data class PartyMember(
    val memberId: MemberId,
    val name: String,
    val archetype: ArchetypeId,
    val hp: Int,
    /** Refilled to max by `finishEncounter` — mana is a per-encounter pool (docs/10-game-loop.md), stored here only so a mid-encounter save round-trips cleanly. */
    val mana: Int,
    val equipment: Equipment = Equipment.EMPTY,
    /** The champion's 2-point ability-score point-buy, spent once at creation — see `ChampionRecord.abilityBonuses`. */
    val abilityBonuses: AbilityScores = AbilityScores.ZERO,
    val controller: Controller,
    val condition: MemberCondition = MemberCondition.Healthy,
)

@Serializable
enum class MemberCondition { Healthy, Downed }

@Serializable
enum class RunOutcome { Success, Failure }

/**
 * Non-null only while a battle is active. Carries [spec] (not just the running [Resolver]) because
 * `finishEncounter(run, final, cat)` takes no `EncounterSpec` of its own (docs/11-run-state.md) —
 * the reward info (gold range, loot table) it needs has to come from the still-active handle.
 */
@Serializable
data class EncounterHandle(
    val resolver: Resolver,
    val memberToEntity: Map<MemberId, EntityId>,
    val spec: EncounterSpec,
)


