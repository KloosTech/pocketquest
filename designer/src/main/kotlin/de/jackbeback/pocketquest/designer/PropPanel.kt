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
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.PropDef
import de.jackbeback.pocketquest.core.model.PropId
import de.jackbeback.pocketquest.ui.ink.INK
import de.jackbeback.pocketquest.ui.ink.INK_FAINT
import de.jackbeback.pocketquest.ui.ink.InkButton
import de.jackbeback.pocketquest.ui.ink.InkLabel
import de.jackbeback.pocketquest.ui.ink.InkStepper
import de.jackbeback.pocketquest.ui.ink.InkTextField
import de.jackbeback.pocketquest.ui.ink.PAPER
import de.jackbeback.pocketquest.ui.ink.PAPER_SHEET
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * docs/51-props-catalog-and-placement.md: props as real `Catalog` content. One [PropDef] per
 * placeable manifest sprite, auto-migrated in (`Catalog.ensurePropDefs`, called at startup AND
 * reactively here via [LaunchedEffect] so importing a new sprite mid-session still gets a `PropDef`
 * without a restart) — plus, per docs/53, a `+ Add Prop` button that imports a brand-new sprite
 * file (via the same `chooseImageFile`/`AssetManifest.importSprite` mechanism `ArchetypePanel`'s
 * sprite picker already uses) and creates its matching `PropDef` in one step, since a prop can't
 * exist without a real image file behind it. [PropDef.id] stays permanently paired to its sprite
 * once created (never re-pointed at a different image here) — but its footprint, unlike that
 * pairing, IS editable afterward (see [PropDef]'s own doc comment).
 */
@Composable
fun PropPanel(catalog: Catalog, onCatalogChange: (Catalog) -> Unit, modifier: Modifier = Modifier) {
    val healed = remember(catalog.props, AssetManifest.placeableProps) { catalog.ensurePropDefs() }
    LaunchedEffect(healed) { if (healed != catalog) onCatalogChange(healed) }

    var selectedId by remember { mutableStateOf<PropId?>(catalog.props.keys.firstOrNull()) }

    fun updateProp(id: PropId, transform: (PropDef) -> PropDef) {
        val current = catalog.props[id] ?: return
        onCatalogChange(catalog.copy(props = catalog.props + (id to transform(current))))
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.width(240.dp).fillMaxHeight().background(PAPER_SHEET).padding(8.dp)) {
            InkLabel("PROPS")
            // docs/53: creates the underlying ManifestAsset AND its PropDef in one step — a prop
            // can't exist without a real sprite file behind it (docs/28's import mechanism always
            // needs a source image), so "add a new prop" and "pick its sprite" are the same action,
            // not two. Starts at a 1x1 footprint; adjust it below once it's selected — most newly
            // imported art (a big scattered-rubble or whole-set-piece image, docs/Campain_1) will
            // want a bigger one than the default.
            InkButton(
                "+ Add Prop (pick image file)",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    val source = chooseImageFile() ?: return@InkButton
                    val (tilesW, tilesH) = inferAspectRatioFootprint(source)
                    val asset = AssetManifest.importSprite(source, kind = "prop", tilesW = tilesW, tilesH = tilesH) ?: return@InkButton
                    val def = PropDef(id = PropId(asset.id), name = asset.id, footprintTilesW = tilesW, footprintTilesH = tilesH)
                    onCatalogChange(catalog.copy(props = catalog.props + (def.id to def)))
                    selectedId = def.id
                },
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(catalog.props.values.sortedBy { it.id.raw }) { prop ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { selectedId = prop.id }
                            .background(if (prop.id == selectedId) PAPER else PAPER_SHEET)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val meta = remember(prop.id) { AssetManifest.prop(prop.id.raw) }
                        val bmp = meta?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
                        if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 6.dp))
                        BasicText(prop.name.ifBlank { prop.id.raw }, style = TextStyle(color = INK, fontSize = 13.sp))
                    }
                }
            }
        }

        val prop = selectedId?.let { catalog.props[it] }
        if (prop != null) {
            PropEditor(prop = prop, onChange = { updated -> updateProp(prop.id) { updated } })
        } else {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                BasicText("No prop selected — place a sprite in the Map editor's Prop tool, or import one there, to see it here.", style = TextStyle(color = INK_FAINT, fontSize = 13.sp))
            }
        }
    }
}

/**
 * docs/53: a freshly imported image's `tilesW`/`tilesH` used to always default to a hardcoded 1x1
 * — since rendering scales both axes by the SAME per-tile scalar, any non-square image got
 * silently squished into a square (found live: "the aspect ratio got changed, squished into a
 * square"). Reads the real pixel dimensions and picks the closest small integer ratio instead — a
 * 10-unit base on the shorter side keeps reasonable precision (a 16:9 image lands on 18x10, ~1%
 * off) without producing absurdly large tile counts, then reduced by GCD for tidiness (an exact
 * square still comes back as a clean 1x1). Falls back to 1x1 only if the file can't be read as an
 * image at all — same "never crash on a bad asset" contract every sprite lookup in this app has.
 */
private fun inferAspectRatioFootprint(file: File): Pair<Int, Int> {
    val image = runCatching { ImageIO.read(file) }.getOrNull() ?: return 1 to 1
    val w = image.width
    val h = image.height
    if (w <= 0 || h <= 0) return 1 to 1
    val base = 10
    val (rawW, rawH) = if (w >= h) (w.toFloat() / h * base).roundToInt().coerceAtLeast(1) to base else base to (h.toFloat() / w * base).roundToInt().coerceAtLeast(1)
    val divisor = gcd(rawW, rawH)
    return (rawW / divisor) to (rawH / divisor)
}

/** Same relative-path candidate resolution [SpriteLoader.load] already uses internally, exposed here so [inferAspectRatioFootprint] can be re-run against an already-imported prop's real file (the "Recompute from image" button — fixes a `PropDef` whose footprint was set wrong before this bug fix existed). */
private fun resolvePropSourceFile(relativePath: String): File? =
    listOf(File(relativePath), File("../$relativePath"), File("../../$relativePath")).firstOrNull { it.exists() }

private fun gcd(a: Int, b: Int): Int {
    var x = a
    var y = b
    while (y != 0) {
        val t = y
        y = x % y
        x = t
    }
    return if (x == 0) 1 else x
}

@Composable
private fun PropEditor(prop: PropDef, onChange: (PropDef) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Not remembered on prop.id alone anymore — recomputeFromImage below changes this same
        // manifest entry's tilesW/tilesH in place (same id), and the panel needs to show the
        // result immediately rather than the pre-fix cached value.
        val meta = AssetManifest.prop(prop.id.raw)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val bmp = meta?.let { remember(it.file) { SpriteLoader.load(PROPS_DIR + it.file) } }
            if (bmp != null) PropThumbnail(bmp, modifier = Modifier.padding(end = 8.dp))
            Column {
                InkLabel("NAME")
                InkTextField(prop.name, onValueChange = { onChange(prop.copy(name = it)) }, modifier = Modifier.width(220.dp))
            }
        }
        BasicText("Sprite: ${prop.id.raw} (fixed — see this tab's own doc comment)", style = TextStyle(color = INK_FAINT, fontSize = 12.sp), modifier = Modifier.padding(top = 8.dp))

        Box(modifier = Modifier.padding(top = 12.dp)) {
            InkLabel("RENDER SIZE: ${meta?.tilesW ?: 1}x${meta?.tilesH ?: 1} tiles — how big this sprite actually draws, for a grid prop, a decoration, or a gate sprite alike")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkButton(
                "Recompute from image (fixes stretched/squished art)",
                onClick = {
                    val relFile = meta?.file ?: return@InkButton
                    val sourceFile = resolvePropSourceFile(PROPS_DIR + relFile) ?: return@InkButton
                    val (w, h) = inferAspectRatioFootprint(sourceFile)
                    if (AssetManifest.updateFootprint(prop.id.raw, w, h)) {
                        onChange(prop.copy(footprintTilesW = w, footprintTilesH = h))
                    }
                },
            )
        }

        Box(modifier = Modifier.padding(top = 12.dp)) {
            InkLabel("OBSTRUCTION FOOTPRINT (tiles blocked when this prop blocks movement/LoS as a grid placement — independent of render size above; a decoration ignores this entirely)")
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkStepper(prop.footprintTilesW, min = 1, onValueChange = { onChange(prop.copy(footprintTilesW = it)) })
            InkLabel("x", modifier = Modifier.padding(horizontal = 4.dp))
            InkStepper(prop.footprintTilesH, min = 1, onValueChange = { onChange(prop.copy(footprintTilesH = it)) })
        }

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("TAGS (comma-separated, filtering only — no mechanics read these)") }
        var tagsText by remember(prop.id) { mutableStateOf(prop.tags.joinToString(", ")) }
        InkTextField(
            tagsText,
            onValueChange = { text ->
                tagsText = text
                onChange(prop.copy(tags = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(modifier = Modifier.padding(top = 12.dp)) { InkLabel("OBSTRUCTION (docs/51 — folded into the runtime map's terrain when either is on)") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            InkButton(
                if (prop.blocksMovement) "Blocks movement: ON" else "Blocks movement: off",
                emphasized = prop.blocksMovement,
                onClick = { onChange(prop.copy(blocksMovement = !prop.blocksMovement)) },
            )
            InkButton(
                if (prop.blocksLoS) "Blocks line of sight: ON" else "Blocks line of sight: off",
                modifier = Modifier.padding(start = 8.dp),
                emphasized = prop.blocksLoS,
                onClick = { onChange(prop.copy(blocksLoS = !prop.blocksLoS)) },
            )
        }
    }
}
