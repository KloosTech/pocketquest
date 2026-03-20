package de.jackbeback.pocketquest.content.events

/**
 * A single node in the overworld progression graph.
 * [id] either matches an [OverworldEvent.id] or is "start".
 * [nextIds] are the IDs of nodes reachable by moving forward.
 */
data class MapNode(
    val id: String,
    val nextIds: List<String>,
)

/**
 * Directed-acyclic graph describing one area's progression (Slay-the-Spire style).
 * The player starts on [startNodeId] and can only move to nodes reachable via [nextOf].
 */
class MapGraph(
    val mapId: String,
    val startNodeId: String,
    val nodes: List<MapNode>,
) {
    private val nodeMap = nodes.associateBy { it.id }

    fun nextOf(nodeId: String): List<String> = nodeMap[nodeId]?.nextIds ?: emptyList()
}
