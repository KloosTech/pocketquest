package de.jackbeback.pocketquest.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.meta.ChampionId
import de.jackbeback.pocketquest.core.meta.ChampionStatus
import de.jackbeback.pocketquest.core.meta.MetaState
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.progression.createChampion
import de.jackbeback.pocketquest.core.progression.formParty
import de.jackbeback.pocketquest.core.progression.maxPartySize
import de.jackbeback.pocketquest.core.progression.PartyFormationResult
import de.jackbeback.pocketquest.core.progression.resolveRunOutcome
import de.jackbeback.pocketquest.core.progression.toFreshPartyMember
import de.jackbeback.pocketquest.core.run.EncounterPool
import de.jackbeback.pocketquest.core.run.EventPool
import de.jackbeback.pocketquest.core.run.NodeType
import de.jackbeback.pocketquest.core.run.RunId
import de.jackbeback.pocketquest.core.run.RunOutcome
import de.jackbeback.pocketquest.core.run.RunState
import de.jackbeback.pocketquest.core.run.ShopPool
import de.jackbeback.pocketquest.core.run.applyRest
import de.jackbeback.pocketquest.core.run.createRun
import de.jackbeback.pocketquest.core.run.finishEncounter
import de.jackbeback.pocketquest.core.run.markVisited
import de.jackbeback.pocketquest.core.run.resolveEncounterNode
import de.jackbeback.pocketquest.core.run.startEncounter
import de.jackbeback.pocketquest.data.MetaRepository
import de.jackbeback.pocketquest.data.RunRepository
import de.jackbeback.pocketquest.ui.App
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.PAPER
import kotlinx.coroutines.launch

/**
 * Every content pool a generated [de.jackbeback.pocketquest.core.run.NodeGraph] can draw from —
 * docs/13-encounters-and-events.md's "content pools" bundled together for convenience. `:designer`
 * authoring tools for these (Pass 9) don't exist yet; until then a caller (`:app`'s composition
 * root) builds placeholder pools from whatever a catalog happens to have.
 */
data class ContentPools(
    val encounters: List<EncounterPool> = emptyList(),
    val events: List<EventPool> = emptyList(),
    val shops: List<ShopPool> = emptyList(),
)

/** Only the node types a real pool exists for get weighted into [de.jackbeback.pocketquest.core.run.generateGraph] — Rest needs no catalog content, so it's always available. */
fun ContentPools.availableNodeTypeWeights(): List<Pair<NodeType, Int>> = buildList {
    if (encounters.any { it.kind == NodeType.Combat }) add(NodeType.Combat to 50)
    if (encounters.any { it.kind == NodeType.Elite }) add(NodeType.Elite to 15)
    if (events.isNotEmpty()) add(NodeType.Event to 20)
    add(NodeType.Rest to 10)
    if (shops.isNotEmpty()) add(NodeType.Shop to 5)
}

private const val ACTIVE_RUN_ID = "active"
private const val RUN_ACTS = 3

/**
 * The Pass 8 composition root: loads [MetaState]/[RunState] once, then routes between character
 * creation, the roster hub, and whatever the run's current node needs — the plain, functional (not
 * pretty, per the implementation plan) shell around the already-built pieces from Pass 0-7.
 * [now] is an injected wall-clock reader (`:core:meta`'s own rule: only ever read at a boundary like
 * this, never touched from inside a deterministic layer) — `:app`'s desktop entry point supplies
 * `System::currentTimeMillis`; this module stays platform-agnostic.
 */
@Composable
fun RunApp(catalog: Catalog, metaRepository: MetaRepository, runRepository: RunRepository, pools: ContentPools, now: () -> Long) {
    var meta by remember { mutableStateOf<MetaState?>(null) }
    var run by remember { mutableStateOf<RunState?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        meta = metaRepository.load() ?: MetaState()
        run = runRepository.load(ACTIVE_RUN_ID)
        loaded = true
    }

    if (!loaded) {
        CenteredMessage("Loading…")
        return
    }
    val currentMeta = meta ?: return

    fun saveMeta(next: MetaState) {
        meta = next
        scope.launch { metaRepository.save(now(), next) }
    }

    fun saveRun(next: RunState?) {
        run = next
        scope.launch {
            if (next != null) {
                val partySummary = next.party.joinToString(", ") { it.name }
                runRepository.save(ACTIVE_RUN_ID, now(), partySummary, next)
            } else {
                runRepository.delete(ACTIVE_RUN_ID)
            }
        }
    }

    fun beginRun(party: List<de.jackbeback.pocketquest.core.run.PartyMember>) {
        saveRun(createRun(RunId(now().toString()), seed = now(), party = party, acts = RUN_ACTS, nodeTypeWeights = pools.availableNodeTypeWeights()))
    }

    val currentRun = run
    when {
        currentRun != null -> RunScreen(currentRun, catalog, pools, onRunUpdated = ::saveRun, onRunEnded = { finished ->
            saveMeta(resolveRunOutcome(currentMeta, finished))
            saveRun(null)
        })
        currentMeta.roster.isEmpty() -> CharacterCreationScreen(catalog) { name, archetype ->
            val id = ChampionId(now().toString())
            val withChampion = createChampion(currentMeta, id, name, archetype)
            saveMeta(withChampion)
            beginRun(listOf(withChampion.roster.getValue(id).toFreshPartyMember(catalog)))
        }
        else -> HubScreen(currentMeta) { championIds ->
            when (val result = formParty(currentMeta, championIds, catalog)) {
                is PartyFormationResult.Formed -> {
                    saveMeta(result.meta)
                    beginRun(result.party)
                }
                is PartyFormationResult.Rejected -> Unit // buttons already only offer valid selections
            }
        }
    }
}

@Composable
private fun RunScreen(run: RunState, catalog: Catalog, pools: ContentPools, onRunUpdated: (RunState) -> Unit, onRunEnded: (RunState) -> Unit) {
    val outcome = run.outcome
    if (outcome != null) {
        RunEndScreen(outcome) { onRunEnded(run) }
        return
    }

    val node = run.graph.nodes.getValue(run.position)
    if (run.position in run.visited) {
        NodeChoiceScreen(node) { nextId ->
            onRunUpdated(run.copy(position = nextId, act = run.graph.nodes.getValue(nextId).act))
        }
        return
    }

    when (node.type) {
        NodeType.Combat, NodeType.Elite, NodeType.Boss -> {
            val handle = run.encounter
            if (handle == null) {
                LaunchedEffect(run.position) {
                    val (spec, advancedRng) = resolveEncounterNode(run, node, pools.encounters, catalog)
                    onRunUpdated(startEncounter(run.copy(rng = advancedRng), spec, catalog))
                }
                CenteredMessage("Entering battle…")
            } else {
                App(
                    initialState = handle.resolver.state,
                    catalog = catalog,
                    onEncounterEnd = { final -> onRunUpdated(finishEncounter(run, final, catalog).markVisited()) },
                )
            }
        }
        NodeType.Event -> EventNodeScreen(run, node, catalog, pools.events) { onRunUpdated(it.markVisited()) }
        NodeType.Shop -> ShopNodeScreen(run, catalog, node, pools.shops) { onRunUpdated(it.markVisited()) }
        NodeType.Rest -> RestNodeScreen { onRunUpdated(applyRest(run, catalog).markVisited()) }
    }
}

@Composable
private fun NodeChoiceScreen(node: de.jackbeback.pocketquest.core.run.GraphNode, onPick: (de.jackbeback.pocketquest.core.run.NodeId) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Where to next?", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        node.next.forEach { nextId ->
            InkButton("Head onward", modifier = Modifier.padding(bottom = 8.dp), onClick = { onPick(nextId) })
        }
    }
}

@Composable
private fun RestNodeScreen(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Rest", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(12.dp))
        BasicText("The party makes camp and recovers.", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
        Spacer(modifier = Modifier.size(16.dp))
        InkButton("Continue", onClick = onContinue)
    }
}

@Composable
private fun RunEndScreen(outcome: RunOutcome, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText(
            if (outcome == RunOutcome.Success) "Victory!" else "The party has fallen…",
            style = TextStyle(color = INK, fontSize = 22.sp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        InkButton("Continue", onClick = onContinue)
    }
}

@Composable
private fun CharacterCreationScreen(catalog: Catalog, onCreate: (String, ArchetypeId) -> Unit) {
    var name by remember { mutableStateOf("") }
    var archetype by remember { mutableStateOf(catalog.archetypes.keys.firstOrNull()) }
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Create your first Champion", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        de.jackbeback.pocketquest.ui.ink.InkTextField(name, { name = it }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.size(12.dp))
        Row {
            catalog.archetypes.keys.forEach { id ->
                InkButton(
                    catalog.archetype(id).name,
                    modifier = Modifier.padding(end = 8.dp),
                    emphasized = archetype == id,
                    onClick = { archetype = id },
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        val chosen = archetype
        InkButton("Begin", onClick = { if (name.isNotBlank() && chosen != null) onCreate(name, chosen) })
    }
}

@Composable
private fun HubScreen(meta: MetaState, onStartRun: (List<ChampionId>) -> Unit) {
    var selected by remember { mutableStateOf(setOf<ChampionId>()) }
    val available = meta.roster.values.filter { it.status == ChampionStatus.Available }
    val max = maxPartySize(meta)
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Choose your party (up to $max)", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        available.forEach { record ->
            InkButton(
                record.name,
                modifier = Modifier.padding(bottom = 8.dp),
                emphasized = record.id in selected,
                onClick = {
                    selected = when {
                        record.id in selected -> selected - record.id
                        selected.size < max -> selected + record.id
                        else -> selected
                    }
                },
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        InkButton("Start Run", onClick = { if (selected.isNotEmpty()) onStartRun(selected.toList()) })
    }
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().background(PAPER), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
    }
}
