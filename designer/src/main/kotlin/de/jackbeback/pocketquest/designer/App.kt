package de.jackbeback.pocketquest.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jackbeback.pocketquest.core.content.CatalogValidationException
import de.jackbeback.pocketquest.core.content.CatalogValidator
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.TileType
import de.jackbeback.pocketquest.core.model.WallStyle
import de.jackbeback.pocketquest.core.rules.content.expandTerrainRuns
import de.jackbeback.pocketquest.ui.generateWallHatchOsr
import de.jackbeback.pocketquest.ui.ink.DANGER
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.OK
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import kotlinx.coroutines.delay

/**
 * doc16: "live validation against CatalogValidator, showing referential errors as you type" —
 * this is that. Recomputed on every catalog edit (cheap: the catalog stays small for a single
 * encounter-authoring session), never blocks editing — a dangling reference is visible, not fatal.
 */
private fun validate(catalog: Catalog): List<String> =
    try {
        CatalogValidator.validate(catalog)
        emptyList()
    } catch (e: CatalogValidationException) {
        e.problems
    }

private enum class DesignerTab { Archetypes, Actions, Items, Statuses, Features, AiProfiles, Encounters, Events, Shops, Pools, Maps, Dice, Playtest }

/**
 * docs/33-wall-hatch-osr-packing.md: ensures Save never ships a map switched to [WallStyle.Osr]
 * with no baked geometry at all — but does NOT reroll a map that already has some (that would
 * make every unrelated Save silently reshuffle every Osr map's look). Deterministic: uses each
 * map's own stored `wallHatchOsrSeed` (0 by default), not a fresh random one, so re-running this
 * on an already-baked-by-this-exact-call map is a no-op — safe to call on every Save unconditionally.
 */
private fun bakeMissingOsrHatch(catalog: Catalog): Catalog {
    val bakedMaps = catalog.maps.mapValues { (_, map) ->
        if (map.wallStyle != WallStyle.Osr || map.wallHatchOsr.isNotEmpty()) return@mapValues map
        val tiles = expandTerrainRuns(map.terrain)
        val lines = generateWallHatchOsr(
            isWall = { (tiles[it] ?: TileType.Floor) == TileType.Wall },
            cols = 0 until map.width,
            rows = 0 until map.height,
            seed = map.wallHatchOsrSeed,
            params = map.wallHatchOsrParams,
        )
        map.copy(wallHatchOsr = lines)
    }
    return catalog.copy(maps = bakedMaps)
}

/**
 * Auto-loads `content/catalog.json` on startup if it exists — same canonical file
 * [DesignerFileIo]'s "Save" writes to, so a plain restart round-trips without the user ever
 * touching a file picker. [Catalog.ensureMoveAction] runs here too, not just in [freshCatalog] —
 * an existing saved catalog authored before "move" was guaranteed to exist needs the same healing,
 * not just brand-new ones.
 */
private fun startingCatalog(): Pair<Catalog, String?> {
    val file = DesignerFileIo.defaultCatalogFile()
    return if (file.exists()) DesignerFileIo.load(file).ensureMoveAction() to file.absolutePath else freshCatalog() to null
}

@Composable
fun DesignerApp(onPlaytest: (GameState, Catalog) -> Unit) {
    val (initialCatalog, initialPath) = remember { startingCatalog() }
    var catalog by remember { mutableStateOf(initialCatalog) }
    var loadedFilePath by remember { mutableStateOf(initialPath) }
    var tab by remember { mutableStateOf(DesignerTab.Encounters) }
    val problems = validate(catalog)
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(500)
            justSaved = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PAPER)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(PAPER_SHEET).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("PocketQuest Designer", style = TextStyle(color = INK, fontSize = 16.sp), modifier = Modifier.padding(end = 16.dp))
            // 8 tabs no longer fit a fixed-width window alongside the title and Save/Load buttons —
            // a plain Row doesn't wrap or clip gracefully, it just let later buttons draw past the
            // window edge, which is what looked like "overlapping" (Archetypes/Actions/etc were
            // pushed off-screen, not actually gone). Scrolling this cluster on its own means adding
            // more tabs later never breaks the always-visible Save/Save As/Load controls again.
            Row(
                modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignerTab.entries.forEach { t ->
                    InkButton(t.name, modifier = Modifier.padding(end = 8.dp), emphasized = t == tab, onClick = { tab = t })
                }
            }
            InkButton(
                "Save",
                modifier = Modifier.padding(end = 8.dp),
                flashColor = if (justSaved) OK else null,
                onClick = {
                    catalog = bakeMissingOsrHatch(catalog)
                    val file = DesignerFileIo.defaultCatalogFile()
                    DesignerFileIo.save(file, catalog)
                    loadedFilePath = file.absolutePath
                    justSaved = true
                },
            )
            InkButton("Save As…", modifier = Modifier.padding(end = 8.dp), onClick = {
                catalog = bakeMissingOsrHatch(catalog)
                val file = DesignerFileIo.chooseSaveFile() ?: return@InkButton
                DesignerFileIo.save(file, catalog)
                loadedFilePath = file.absolutePath
            })
            InkButton("Load…", modifier = Modifier.padding(end = 8.dp), onClick = {
                val file = DesignerFileIo.chooseLoadFile() ?: return@InkButton
                catalog = DesignerFileIo.load(file).ensureMoveAction()
                loadedFilePath = file.absolutePath
            })
        }
        loadedFilePath?.let {
            BasicText(it, style = TextStyle(color = INK_FAINT, fontSize = 11.sp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth().background(if (problems.isEmpty()) OK.copy(alpha = 0.15f) else DANGER.copy(alpha = 0.15f)).padding(8.dp),
        ) {
            if (problems.isEmpty()) {
                BasicText("Valid — no referential problems.", style = TextStyle(color = OK, fontSize = 12.sp))
            } else {
                Column {
                    BasicText("${problems.size} problem(s):", style = TextStyle(color = DANGER, fontSize = 12.sp))
                    LazyColumn(modifier = Modifier.heightIn(max = 100.dp).padding(top = 4.dp)) {
                        items(problems) { problem -> BasicText("• $problem", style = TextStyle(color = DANGER, fontSize = 11.sp)) }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().border(1.dp, INK_FAINT)) {
            when (tab) {
                DesignerTab.Archetypes -> ArchetypePanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Actions -> ActionPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Items -> ItemPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Statuses -> StatusPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Features -> FeaturePanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.AiProfiles -> AiProfilePanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Encounters -> EncounterPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Events -> EventPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Shops -> ShopPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Pools -> PoolsPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Maps -> MapEditorPanel(catalog, onCatalogChange = { catalog = it })
                DesignerTab.Dice -> DicePanel()
                DesignerTab.Playtest -> PlaytestPanel(catalog, onPlaytest = onPlaytest)
            }
        }
    }
}
