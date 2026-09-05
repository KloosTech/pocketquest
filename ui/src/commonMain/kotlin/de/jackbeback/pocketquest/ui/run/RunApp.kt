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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
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
import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.EncounterPool
import de.jackbeback.pocketquest.core.model.EventPool
import de.jackbeback.pocketquest.core.model.NodeType
import de.jackbeback.pocketquest.core.model.ShopPool
import de.jackbeback.pocketquest.core.progression.createChampion
import de.jackbeback.pocketquest.core.progression.formParty
import de.jackbeback.pocketquest.core.progression.maxPartySize
import de.jackbeback.pocketquest.core.progression.PartyFormationResult
import de.jackbeback.pocketquest.core.progression.resolveRunOutcome
import de.jackbeback.pocketquest.core.progression.toFreshPartyMember
import de.jackbeback.pocketquest.core.run.RunId
import de.jackbeback.pocketquest.core.run.RunOutcome
import de.jackbeback.pocketquest.core.run.RunState
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
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.nav.PlatformBackHandler
import de.jackbeback.pocketquest.ui.nav.Screen
import de.jackbeback.pocketquest.ui.nav.rememberNavController
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

// Every act/kind combination `generateGraph` can actually place a node as. Boss is forced onto
// the final act's sole node unconditionally (never weighted, doc comment on generateGraph) — it
// still needs backfilling here exactly like Combat/Elite, since resolveEncounterNode has no
// fallback of its own (Pools.kt's `pools.firstOrNull { act && kind } ?: error(...)`, one crash per
// missing combination — this is the fix for "no EncounterPool for act 3/Boss").
private val ENCOUNTER_KINDS = listOf(NodeType.Combat, NodeType.Elite, NodeType.Boss)

/**
 * Prefers whatever's hand-authored in `:designer`'s Pools tab, backfilling only the specific
 * (act, kind) combinations left uncovered — not a whole-category swap like the old all-or-nothing
 * fallback, which let a catalog with *some* encounter pools authored (but no act-3 Boss pool) slip
 * through validation-free and crash the instant a run's forced final Boss node tried to resolve.
 *
 * An authored pool with an EMPTY entries list counts as uncovered too, same as a missing pool —
 * `filter { it.entries.isNotEmpty() }` drops it before backfilling, rather than just appending
 * filler alongside it: `pickUniform`'s `pools.firstOrNull { act && kind }` would otherwise match
 * the empty authored pool first (it's still first in the list) and crash on its own
 * `require(entries.isNotEmpty())` before ever reaching the filler entry right after it. A pool
 * with zero entries can happen as easily as one authored for a whole act never getting authored at
 * all — e.g. a Pools-tab entry created via "+ Add Event Pool" and never populated.
 *
 * Shared by every app entry point (`:app`'s desktop `Main.kt`, `:androidApp`'s bootstrap) rather
 * than each re-deriving pools from a raw [Catalog] independently.
 */
fun resolvePools(catalog: Catalog): ContentPools {
    val encounterIds = catalog.encounters.keys.toList()
    val encounters = if (encounterIds.isEmpty()) {
        catalog.encounterPools
    } else {
        val authored = catalog.encounterPools.filter { it.entries.isNotEmpty() }
        val filler = (1..RUN_ACTS).flatMap { act ->
            ENCOUNTER_KINDS.filterNot { kind -> authored.any { it.act == act && it.kind == kind } }
                .map { kind -> EncounterPool(act = act, kind = kind, entries = encounterIds) }
        }
        authored + filler
    }
    val eventIds = catalog.events.keys.toList()
    val events = if (eventIds.isEmpty()) {
        catalog.eventPools
    } else {
        val authored = catalog.eventPools.filter { it.entries.isNotEmpty() }
        val filler = (1..RUN_ACTS).filterNot { act -> authored.any { it.act == act } }.map { act -> EventPool(act, eventIds) }
        authored + filler
    }
    val shopIds = catalog.shops.keys.toList()
    val shops = if (shopIds.isEmpty()) {
        catalog.shopPools
    } else {
        val authored = catalog.shopPools.filter { it.entries.isNotEmpty() }
        val filler = (1..RUN_ACTS).filterNot { act -> authored.any { it.act == act } }.map { act -> ShopPool(act, shopIds) }
        authored + filler
    }
    return ContentPools(encounters = encounters, events = events, shops = shops)
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
    val nav = rememberNavController(Screen.Splash)
    var meta by remember { mutableStateOf<MetaState?>(null) }
    var run by remember { mutableStateOf<RunState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        meta = metaRepository.load() ?: MetaState()
        run = runRepository.load(ACTIVE_RUN_ID)
    }

    // Drives the ROOT screen off domain state exactly like the old `when` here used to render
    // directly — Settings (if pushed on top) is untouched by this, since `setRoot` only ever
    // replaces index 0 (see `NavController`'s own doc comment).
    val loadedMeta = meta
    val loadedRun = run
    LaunchedEffect(loadedMeta, loadedRun) {
        if (loadedMeta == null) return@LaunchedEffect
        nav.setRoot(
            when {
                loadedRun != null -> Screen.InRun
                loadedMeta.roster.isEmpty() -> Screen.CharacterCreation
                else -> Screen.Hub
            },
        )
    }

    PlatformBackHandler(enabled = nav.canPop) { nav.pop() }

    if (nav.current == Screen.Splash) {
        SplashScreen()
        return
    }
    // Guaranteed non-null past Splash — nav only ever leaves Splash once `loadedMeta` above was
    // non-null (that's the only thing that calls `setRoot`), so every other Screen renders after it.
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

    // docs: Scaffold's default contentWindowInsets (status/nav bars on Android, zero on
    // desktop/iOS) applied via innerPadding — without it every screen's top-of-content buttons
    // (e.g. TurnOrderStrip, Hub's Settings row) render flush against the very top of the window
    // and sit under Android's edge-to-edge status bar, unreachable by touch. containerColor is
    // PAPER (not transparent): targeting API 35 means the OS always draws edge-to-edge and
    // ignores window.statusBarColor, so this Scaffold container — which paints the full window
    // including behind the status/nav bar cutouts — is the only place left to give those bars a
    // solid app-matching color instead of showing the platform's default white through them.
    Scaffold(containerColor = PAPER) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (nav.current) {
                // docs: End Campaign only shown when there's an active run to end — reached from
                // Hub (no run yet) it'd be a no-op button, so onEndCampaign is null there.
                Screen.Settings -> SettingsScreen(
                    onBack = { nav.pop() },
                    onEndCampaign = run?.let { { saveRun(null); nav.pop() } },
                )
                // docs/47-inventory-screen.md: reachable from both the battle menu (run != null,
                // reads/writes RunState.inventory + PartyMember.equipment) and the Hub (run ==
                // null, reads/writes MetaState.stash + ChampionRecord.equipment) — the screen
                // itself branches on which one is present, no second Screen case needed.
                Screen.Inventory -> InventoryScreen(
                    run = run,
                    meta = currentMeta,
                    catalog = catalog,
                    onRunUpdated = ::saveRun,
                    onMetaUpdated = ::saveMeta,
                    onBack = { nav.pop() },
                )
                Screen.InRun -> run?.let { currentRun ->
                    RunScreen(
                        currentRun, catalog, pools, onRunUpdated = ::saveRun, onRunEnded = { finished ->
                            saveMeta(resolveRunOutcome(currentMeta, finished))
                            saveRun(null)
                        },
                        onOpenSettings = { nav.push(Screen.Settings) },
                        onOpenInventory = { nav.push(Screen.Inventory) },
                    )
                }
                // docs: two ways to land here, told apart by nav.canPop rather than a second Screen
                // case or extra state. setRoot (roster empty, nothing to pop back to) is the
                // original bootstrap: create the first champion and jump straight into a run with
                // just them. push from the Hub (canPop true, there's a Hub to return to) instead
                // adds an Available champion to the roster and pops back — no auto-start.
                Screen.CharacterCreation -> CharacterCreationScreen(catalog) { name, archetype, abilityBonuses ->
                    val id = ChampionId(now().toString())
                    if (nav.canPop) {
                        saveMeta(createChampion(currentMeta, id, name, archetype, abilityBonuses, status = ChampionStatus.Available))
                        nav.pop()
                    } else {
                        val withChampion = createChampion(currentMeta, id, name, archetype, abilityBonuses)
                        saveMeta(withChampion)
                        beginRun(listOf(withChampion.roster.getValue(id).toFreshPartyMember(catalog)))
                    }
                }
                Screen.Hub -> HubScreen(
                    currentMeta,
                    onOpenSettings = { nav.push(Screen.Settings) },
                    onOpenInventory = { nav.push(Screen.Inventory) },
                    onCreateChampion = { nav.push(Screen.CharacterCreation) },
                ) { championIds ->
                    when (val result = formParty(currentMeta, championIds, catalog)) {
                        is PartyFormationResult.Formed -> {
                            saveMeta(result.meta)
                            beginRun(result.party)
                        }
                        is PartyFormationResult.Rejected -> Unit // buttons already only offer valid selections
                    }
                }
                Screen.Splash -> Unit // handled by the early return above
            }
        }
    }
}

@Composable
private fun RunScreen(run: RunState, catalog: Catalog, pools: ContentPools, onRunUpdated: (RunState) -> Unit, onRunEnded: (RunState) -> Unit, onOpenSettings: () -> Unit, onOpenInventory: () -> Unit) {
    // docs/38-loot-reveal-screen.md: ahead of even the outcome check — the boss fight that ends the
    // run can still drop loot, and the player should see what they found before "Victory!", not have
    // it silently skipped because run.outcome was set the same finishEncounter call that rolled it.
    if (run.pendingLootReveal.isNotEmpty()) {
        LootRevealScreen(run, catalog) { updated -> onRunUpdated(updated) }
        return
    }

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
                    onOpenSettings = onOpenSettings,
                    onOpenInventory = onOpenInventory,
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

private const val ABILITY_POINT_BUY_BUDGET = 2

@Composable
private fun CharacterCreationScreen(catalog: Catalog, onCreate: (String, ArchetypeId, AbilityScores) -> Unit) {
    // Only archetypes authored as playable show up here — the rest of the catalog (monsters) is
    // still reachable through EncounterSpec, just never offered as a champion (Archetype.kt's
    // isPlayerCharacter doc comment).
    val playable = remember(catalog) { catalog.archetypes.values.filter { it.isPlayerCharacter } }
    var name by remember { mutableStateOf("") }
    var archetype by remember { mutableStateOf(playable.firstOrNull()?.id) }
    var bonuses by remember { mutableStateOf(AbilityScores.ZERO) }
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        BasicText("Create a Champion", style = TextStyle(color = INK, fontSize = 20.sp))
        Spacer(modifier = Modifier.size(16.dp))
        de.jackbeback.pocketquest.ui.ink.InkTextField(name, { name = it }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.size(12.dp))
        if (playable.isEmpty()) {
            BasicText("No player-playable archetypes authored yet.", style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
            return@Column
        }
        Row {
            playable.forEach { def ->
                InkButton(
                    def.name,
                    modifier = Modifier.padding(end = 8.dp),
                    emphasized = archetype == def.id,
                    onClick = { archetype = def.id; bonuses = AbilityScores.ZERO },
                )
            }
        }
        val chosenArchetype = playable.firstOrNull { it.id == archetype }
        if (chosenArchetype != null) {
            Spacer(modifier = Modifier.size(16.dp))
            val spent = bonuses.str + bonuses.dex + bonuses.con + bonuses.int + bonuses.wis + bonuses.cha
            BasicText("Ability points: ${ABILITY_POINT_BUY_BUDGET - spent} / $ABILITY_POINT_BUY_BUDGET left", style = TextStyle(color = INK, fontSize = 14.sp))
            Spacer(modifier = Modifier.size(8.dp))
            AbilityPointBuyRow("Str", chosenArchetype.abilities.str, bonuses.str, spent) { bonuses = bonuses.copy(str = it) }
            AbilityPointBuyRow("Dex", chosenArchetype.abilities.dex, bonuses.dex, spent) { bonuses = bonuses.copy(dex = it) }
            AbilityPointBuyRow("Con", chosenArchetype.abilities.con, bonuses.con, spent) { bonuses = bonuses.copy(con = it) }
            AbilityPointBuyRow("Int", chosenArchetype.abilities.int, bonuses.int, spent) { bonuses = bonuses.copy(int = it) }
            AbilityPointBuyRow("Wis", chosenArchetype.abilities.wis, bonuses.wis, spent) { bonuses = bonuses.copy(wis = it) }
            AbilityPointBuyRow("Cha", chosenArchetype.abilities.cha, bonuses.cha, spent) { bonuses = bonuses.copy(cha = it) }
        }
        Spacer(modifier = Modifier.size(16.dp))
        val chosen = archetype
        InkButton("Begin", onClick = { if (name.isNotBlank() && chosen != null) onCreate(name, chosen, bonuses) })
    }
}

/** One ability's point-buy row: base score from the archetype, +/- stepper spending out of the shared 2-point [totalSpent] budget — a stat can take both points (a +2), decrementing is always allowed regardless of the budget. */
@Composable
private fun AbilityPointBuyRow(label: String, base: Int, ownBonus: Int, totalSpent: Int, onBonusChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        BasicText("$label ${base + ownBonus}", style = TextStyle(color = INK, fontSize = 14.sp), modifier = Modifier.width(70.dp))
        InkStepper(
            value = ownBonus,
            min = 0,
            onValueChange = { next -> if (next < ownBonus || totalSpent < ABILITY_POINT_BUY_BUDGET) onBonusChange(next) },
        )
    }
}

@Composable
private fun HubScreen(meta: MetaState, onOpenSettings: () -> Unit, onOpenInventory: () -> Unit, onCreateChampion: () -> Unit, onStartRun: (List<ChampionId>) -> Unit) {
    var selected by remember { mutableStateOf(setOf<ChampionId>()) }
    val available = meta.roster.values.filter { it.status == ChampionStatus.Available }
    val max = maxPartySize(meta)
    Column(modifier = Modifier.fillMaxSize().background(PAPER).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Choose your party (up to $max)", style = TextStyle(color = INK, fontSize = 20.sp), modifier = Modifier.weight(1f))
            // docs/47-inventory-screen.md: same "☰" dropdown pattern as the in-run battle menu
            // (TurnOrderStrip, App.kt) — Settings' single button grew a second destination.
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                InkButton("☰", onClick = { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { BasicText("Inventory", style = TextStyle(color = INK, fontSize = 14.sp)) }, onClick = { menuOpen = false; onOpenInventory() })
                    DropdownMenuItem(text = { BasicText("Settings", style = TextStyle(color = INK, fontSize = 14.sp)) }, onClick = { menuOpen = false; onOpenSettings() })
                }
            }
        }
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
        InkButton("Create New Champion", modifier = Modifier.padding(bottom = 8.dp), onClick = onCreateChampion)
        Spacer(modifier = Modifier.size(8.dp))
        InkButton("Start Run", onClick = { if (selected.isNotEmpty()) onStartRun(selected.toList()) })
    }
}

@Composable
internal fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().background(PAPER), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = INK_FAINT, fontSize = 14.sp))
    }
}
