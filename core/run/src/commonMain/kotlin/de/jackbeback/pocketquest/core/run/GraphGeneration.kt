package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.CampaignDef
import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeGraph
import de.jackbeback.pocketquest.core.model.NodeId

import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.rules.rollRange

private const val DEFAULT_ACTS = 3
private const val DEFAULT_LANES = 2

/** Weighted toward Combat, matching doc13's "groups of encounters" framing — Boss is never picked from this, it's forced onto the sole final-act node. Callers whose catalog doesn't have every content kind authored yet (no `EventPool`/`ShopPool`) should pass a restricted list rather than let `resolveEventNode`/`resolveShopNode` fail loudly on a node type nothing can resolve. */
val DEFAULT_NODE_TYPE_WEIGHTS: List<Pair<NodeType, Int>> = listOf(
    NodeType.Combat to 50,
    NodeType.Elite to 15,
    NodeType.Event to 20,
    NodeType.Rest to 10,
    NodeType.Shop to 5,
)

private fun pickNodeType(rng: RngState, weights: List<Pair<NodeType, Int>>): Pair<RngState, NodeType> {
    val total = weights.sumOf { it.second }
    val (advanced, roll) = rng.rollRange(0, total - 1)
    var acc = 0
    for ((type, weight) in weights) {
        acc += weight
        if (roll < acc) return advanced to type
    }
    return advanced to weights.last().first
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
fun generateGraph(
    rng: RngState,
    acts: Int = DEFAULT_ACTS,
    lanes: Int = DEFAULT_LANES,
    nodeTypeWeights: List<Pair<NodeType, Int>> = DEFAULT_NODE_TYPE_WEIGHTS,
): Pair<NodeGraph, RngState> {
    require(acts >= 1) { "a run needs at least one act" }
    require(lanes >= 1) { "a run needs at least one lane" }
    require(nodeTypeWeights.isNotEmpty()) { "at least one non-Boss node type must be weighted" }

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
                val (advanced, picked) = pickNodeType(current, nodeTypeWeights)
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
    nodeTypeWeights: List<Pair<NodeType, Int>> = DEFAULT_NODE_TYPE_WEIGHTS,
): RunState {
    val (graph, rng) = generateGraph(RngState(seed = seed), acts, lanes, nodeTypeWeights)
    return RunState(runId = runId, seed = seed, rng = rng, act = 1, graph = graph, position = graph.start, party = party)
}

/**
 * docs/49-campaign-authoring.md: the hand-authored sibling of [createRun] — no graph generation
 * step at all, so [RunState.rng] starts fresh at [seed] (unadvanced) rather than post-generation
 * like [createRun]'s does; there was no shape roll to advance past. It still gets consumed normally
 * by anything inside an encounter (dice rolls, `:core:ai`'s Wander goal's version-seeded pick,
 * etc.) — only the graph-shape roll is absent, matching "pinned content consumes no randomness."
 */
fun createCampaignRun(runId: RunId, seed: Long, party: List<PartyMember>, campaign: CampaignDef): RunState {
    val graph = NodeGraph(nodes = campaign.nodes.associateBy { it.id }, start = campaign.start)
    return RunState(runId = runId, seed = seed, rng = RngState(seed = seed), act = graph.nodes.getValue(graph.start).act, graph = graph, position = graph.start, party = party)
}
