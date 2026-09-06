package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.model.CampaignDef
import de.jackbeback.pocketquest.core.model.CampaignId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GraphNode
import de.jackbeback.pocketquest.core.model.NodeId
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.PinnedContent
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkSelect
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET

/**
 * docs/49-campaign-authoring.md: hand-author a fixed/branching [CampaignDef] (pinned content per
 * node, per docs/Campain_1's 16-location progression) as an alternative to a procedurally-generated
 * run — plain list-based CRUD, matching every other content tab's style, not a spatial node-graph
 * canvas (`MapEditorPanel` is the one spatial exception in this app, for a reason that doesn't apply
 * here — a campaign graph has no natural 2D geometry to place nodes onto).
 */
@Composable
fun CampaignPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by remember { mutableStateOf<CampaignId?>(catalog.campaigns.keys.firstOrNull()) }

    fun updateCampaign(id: CampaignId, transform: (CampaignDef) -> CampaignDef) {
        val current = catalog.campaigns[id] ?: return
        onCatalogChange(catalog.copy(campaigns = catalog.campaigns + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("CAMPAIGNS")
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.campaigns.values.toList()) { campaign ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = campaign.id }
                            .background(if (campaign.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                    ) {
                        BasicText(campaign.name.ifBlank { campaign.id.raw }, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
            InkButton(
                "+ New Campaign",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    var n = catalog.campaigns.size + 1
                    while (CampaignId("campaign$n") in catalog.campaigns) n++
                    val id = CampaignId("campaign$n")
                    val startId = NodeId("node1")
                    val start = GraphNode(id = startId, act = 1, type = NodeType.Combat)
                    onCatalogChange(catalog.copy(campaigns = catalog.campaigns + (id to CampaignDef(id, "New Campaign $n", listOf(start), startId))))
                    selectedId = id
                },
            )
        }

        val campaign = selectedId?.let { catalog.campaigns[it] }
        if (campaign != null) {
            CampaignEditor(
                campaign = campaign,
                catalog = catalog,
                onChange = { updated -> updateCampaign(campaign.id) { updated } },
                onRemove = {
                    onCatalogChange(catalog.copy(campaigns = catalog.campaigns - campaign.id))
                    selectedId = catalog.campaigns.keys.firstOrNull { it != campaign.id }
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No campaign selected.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

@Composable
private fun CampaignEditor(campaign: CampaignDef, catalog: Catalog, onChange: (CampaignDef) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel("NAME")
            InkButton("Remove Campaign", modifier = Modifier.padding(start = 16.dp), onClick = onRemove)
        }
        InkTextField(campaign.name, onValueChange = { onChange(campaign.copy(name = it)) }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.padding(top = 16.dp)) { InkLabel("NODES") }
        campaign.nodes.forEach { node ->
            NodeRow(
                node = node,
                otherNodeIds = campaign.nodes.map { it.id }.filter { it != node.id },
                isStart = node.id == campaign.start,
                catalog = catalog,
                onChange = { updated -> onChange(campaign.copy(nodes = campaign.nodes.map { if (it.id == node.id) updated else it })) },
                onSetStart = { onChange(campaign.copy(start = node.id)) },
                onRemove = {
                    val remaining = campaign.nodes.filterNot { it.id == node.id }.map { it.copy(next = it.next - node.id) }
                    val newStart = if (campaign.start == node.id) remaining.firstOrNull()?.id else campaign.start
                    if (newStart != null) onChange(campaign.copy(nodes = remaining, start = newStart))
                },
            )
        }
        InkButton(
            "+ Add Node",
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                var n = campaign.nodes.size + 1
                while (campaign.nodes.any { it.id == NodeId("node$n") }) n++
                val fresh = GraphNode(id = NodeId("node$n"), act = 1, type = NodeType.Combat)
                onChange(campaign.copy(nodes = campaign.nodes + fresh))
            },
        )

        // docs/49: surfaced inline, not a blocking save-time dialog — matching this app's own
        // "author sees problems live" style elsewhere (Map editor's tooltips, catalog-wide
        // validate() banner).
        val problems = campaignProblems(campaign)
        if (problems.isNotEmpty()) {
            Box(modifier = Modifier.padding(top = 12.dp)) {
                Column {
                    problems.forEach { BasicText(it, style = TextStyle(color = DANGER, fontSize = 12.sp)) }
                }
            }
        }
    }
}

private fun campaignProblems(campaign: CampaignDef): List<String> {
    val problems = mutableListOf<String>()
    val ids = campaign.nodes.map { it.id }.toSet()
    if (campaign.start !in ids) problems += "Start node '${campaign.start.raw}' doesn't exist."
    for (node in campaign.nodes) {
        for (next in node.next) {
            if (next !in ids) problems += "Node '${node.id.raw}' points at unknown next node '${next.raw}'."
        }
        val needsPin = node.type != NodeType.Rest
        if (needsPin && node.pinned == null) problems += "Node '${node.id.raw}' (${node.type}) has no content picked yet."
        val reachable = node.id == campaign.start || campaign.nodes.any { it.next.contains(node.id) }
        if (!reachable) problems += "Node '${node.id.raw}' is unreachable — nothing points at it and it isn't the start."
    }
    return problems
}

@Composable
private fun NodeRow(
    node: GraphNode,
    otherNodeIds: List<NodeId>,
    isStart: Boolean,
    catalog: Catalog,
    onChange: (GraphNode) -> Unit,
    onSetStart: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().background(PAPER_SHEET).padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InkLabel(node.id.raw, modifier = Modifier.weight(1f).padding(end = 8.dp))
            if (isStart) {
                InkLabel("START", modifier = Modifier.padding(end = 8.dp))
            } else {
                InkButton("Set as start", modifier = Modifier.padding(end = 8.dp), onClick = onSetStart)
            }
            InkButton("Remove", onClick = onRemove)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("ACT", modifier = Modifier.padding(end = 4.dp))
            InkStepper(node.act, min = 1, onValueChange = { onChange(node.copy(act = it)) })
            InkLabel("TYPE", modifier = Modifier.padding(start = 12.dp, end = 4.dp))
            InkSelect(node.type, NodeType.entries, { it.name }, { type -> onChange(node.copy(type = type, pinned = null)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkLabel("CONTENT", modifier = Modifier.padding(end = 4.dp))
            PinnedContentPicker(node = node, catalog = catalog, onChange = onChange)
        }
        if (otherNodeIds.isNotEmpty()) {
            InkLabel("NEXT (branches — player picks among these at runtime)", modifier = Modifier.padding(top = 6.dp))
            Row {
                otherNodeIds.forEach { id ->
                    val selected = id in node.next
                    InkButton(
                        id.raw,
                        modifier = Modifier.padding(end = 4.dp),
                        emphasized = selected,
                        onClick = { onChange(node.copy(next = if (selected) node.next - id else node.next + id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedContentPicker(node: GraphNode, catalog: Catalog, onChange: (GraphNode) -> Unit) {
    when (node.type) {
        NodeType.Rest -> InkLabel("(Rest needs no content)")
        NodeType.Event -> {
            val options = catalog.events.values.toList()
            if (options.isEmpty()) {
                InkLabel("no events in the working catalog yet")
            } else {
                val selected = (node.pinned as? PinnedContent.Event)?.id?.let { id -> options.find { it.id == id } } ?: options.first()
                InkSelect(selected, options, { it.title }, { onChange(node.copy(pinned = PinnedContent.Event(it.id))) })
            }
        }
        NodeType.Shop -> {
            val options = catalog.shops.values.toList()
            if (options.isEmpty()) {
                InkLabel("no shops in the working catalog yet")
            } else {
                val selected = (node.pinned as? PinnedContent.Shop)?.id?.let { id -> options.find { it.id == id } } ?: options.first()
                InkSelect(selected, options, { it.id.raw }, { onChange(node.copy(pinned = PinnedContent.Shop(it.id))) })
            }
        }
        NodeType.Combat, NodeType.Elite, NodeType.Boss -> {
            val options = catalog.encounters.values.toList()
            if (options.isEmpty()) {
                InkLabel("no encounters in the working catalog yet")
            } else {
                val selected = (node.pinned as? PinnedContent.Encounter)?.id?.let { id -> options.find { it.id == id } } ?: options.first()
                InkSelect(selected, options, { it.name }, { onChange(node.copy(pinned = PinnedContent.Encounter(it.id))) })
            }
        }
    }
}
