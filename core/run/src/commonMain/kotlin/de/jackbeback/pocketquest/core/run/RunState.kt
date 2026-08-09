package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.EncounterSpec
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
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

@JvmInline @Serializable value class NodeId(val raw: String)

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
)

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

/**
 * docs/13-encounters-and-events.md: graph *shape* is generated per run from [RunState.rng]; graph
 * *content* (which `EncounterSpec`/`EventDef`/`ShopDef` a node resolves to) is picked from a
 * hand-authored pool, not generated — kept as a separate concern, not modeled by this shape.
 */
@Serializable
data class NodeGraph(
    val nodes: Map<NodeId, GraphNode>,
    val start: NodeId,
)

@Serializable
data class GraphNode(
    val id: NodeId,
    val act: Int,
    val type: NodeType,
    /** Empty only for the Act 3 Boss node — the run's one and only success condition. */
    val next: List<NodeId> = emptyList(),
)

/** One shared pool per run, not per-member (docs/13-encounters-and-events.md). Capacity (STR-bound, exact formula still open) is enforced at the call site that adds an item, not modeled as a stored field here. */
@Serializable
data class Inventory(val items: List<ItemId> = emptyList())
