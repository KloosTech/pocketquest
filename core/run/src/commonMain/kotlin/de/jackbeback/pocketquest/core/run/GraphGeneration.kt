package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.rules.rollRange

private const val DEFAULT_ACTS = 3
private const val DEFAULT_LANES = 2

private data class TypeWeight(val type: NodeType, val weight: Int)

/** Weighted toward Combat, matching doc13's "groups of encounters" framing — Boss is never picked here, it's forced onto the sole final-act node. */
private val NODE_TYPE_WEIGHTS = listOf(
    TypeWeight(NodeType.Combat, 50),
    TypeWeight(NodeType.Elite, 15),
    TypeWeight(NodeType.Event, 20),
    TypeWeight(NodeType.Rest, 10),
    TypeWeight(NodeType.Shop, 5),
)
private val TOTAL_WEIGHT = NODE_TYPE_WEIGHTS.sumOf { it.weight }

private fun pickNodeType(rng: RngState): Pair<RngState, NodeType> {
    val (advanced, roll) = rng.rollRange(0, TOTAL_WEIGHT - 1)
    var acc = 0
    for (w in NODE_TYPE_WEIGHTS) {
        acc += w.weight
        if (roll < acc) return advanced to w.type
    }
    return advanced to NODE_TYPE_WEIGHTS.last().type
}

/**
 * docs/13-encounters-and-events.md: graph shape is generated from `run.rng`, "free to be as simple
 * as a fixed number of parallel paths per act" for v1 — this is that v1. [lanes] parallel
 * single-node choices per act, all fanning into every lane of the next act (real branching without
 * needing multiple layers per act), converging into a single forced [NodeType.Boss] node in the
 * final act.
 *
 * Node *content* (which `EncounterSpec` a `Combat`/`Elite`/`Boss` node resolves to) is deliberately
 * not decided here — see `Pools.kt`'s `resolveEncounterNode` — only node *type* is picked here.
 */
fun generateGraph(rng: RngState, acts: Int = DEFAULT_ACTS, lanes: Int = DEFAULT_LANES): Pair<NodeGraph, RngState> {
    require(acts >= 1) { "a run needs at least one act" }
    require(lanes >= 1) { "a run needs at least one lane" }

    var current = rng
    val nodes = mutableMapOf<NodeId, GraphNode>()
    var previousLaneIds: List<NodeId> = emptyList()
    var start: NodeId? = null

    for (act in 1..acts) {
        val isFinalAct = act == acts
        val laneCount = if (isFinalAct) 1 else lanes
        val laneIds = (0 until laneCount).map { i -> NodeId(if (isFinalAct) "act$act-boss" else "act$act-$i") }

        for (id in laneIds) {
            val type = if (isFinalAct) {
                NodeType.Boss
            } else {
                val (advanced, picked) = pickNodeType(current)
                current = advanced
                picked
            }
            nodes[id] = GraphNode(id = id, act = act, type = type)
        }

        for (prevId in previousLaneIds) {
            nodes[prevId] = nodes.getValue(prevId).copy(next = laneIds)
        }

        if (act == 1) start = laneIds.first()
        previousLaneIds = laneIds
    }

    return NodeGraph(nodes = nodes, start = requireNotNull(start)) to current
}

/**
 * The one place a fresh [RunState] gets built — consumes [seed]'s `RngState` for graph generation
 * up front, so the returned `RunState.rng` is already advanced past those rolls (docs/13:
 * graph shape is "generated per run from run.rng", not a side-channel the rest of the run never
 * touches).
 */
fun createRun(
    runId: RunId,
    seed: Long,
    party: List<PartyMember>,
    acts: Int = DEFAULT_ACTS,
    lanes: Int = DEFAULT_LANES,
): RunState {
    val (graph, rng) = generateGraph(RngState(seed = seed), acts, lanes)
    return RunState(runId = runId, seed = seed, rng = rng, act = 1, graph = graph, position = graph.start, party = party)
}
