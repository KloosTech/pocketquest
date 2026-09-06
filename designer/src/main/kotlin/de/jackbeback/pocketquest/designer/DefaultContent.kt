package de.jackbeback.pocketquest.designer

import de.jackbeback.pocketquest.core.model.ActionCost
import de.jackbeback.pocketquest.core.model.ActionDef
import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Cost
import de.jackbeback.pocketquest.core.model.PropDef
import de.jackbeback.pocketquest.core.model.PropId
import de.jackbeback.pocketquest.core.model.Range
import de.jackbeback.pocketquest.core.model.Shape
import de.jackbeback.pocketquest.core.model.TargetFilter
import de.jackbeback.pocketquest.core.model.TargetMode
import de.jackbeback.pocketquest.core.model.Targeting

val MOVE_ACTION_ID = ActionId("move")

/** Same shape as `:app`'s `DEMO_CATALOG_JSON` "move" entry — a Path-targeted, no-effects action; the resolver's `MoveAlong` handling is triggered by the Path targeting mode itself, not an authored effect. */
val MOVE_ACTION_DEF = ActionDef(
    id = MOVE_ACTION_ID,
    name = "Move",
    cost = Cost(action = ActionCost.Movement(tiles = 6)),
    targeting = Targeting(
        mode = TargetMode.Path,
        range = Range.Tiles(8),
        shape = Shape.Single,
        filter = TargetFilter(requireAlive = true),
        requiresLoS = false,
        maxTargets = 1,
    ),
    effects = emptyList(),
)

/**
 * Playtesting with no way to move (an archetype with an empty `actions` list has nothing to act
 * with — not even movement) is what surfaced this: "move" wasn't guaranteed to exist in every
 * catalog. Ensuring the *action* exists here is a one-line, always-safe normalization; whether a
 * given archetype actually *has* it is left to the Archetype editor's own ACTIONS section — this
 * only makes it available to assign, it never rewrites content the user already authored.
 */
fun Catalog.ensureMoveAction(): Catalog =
    if (MOVE_ACTION_ID in actions) this else copy(actions = actions + (MOVE_ACTION_ID to MOVE_ACTION_DEF))

/**
 * docs/51-props-catalog-and-placement.md: auto-generates one [PropDef] per [AssetManifest]
 * placeable asset not yet in [Catalog.props] — purely additive, never overwrites an already-authored
 * `PropDef`, safe to run unconditionally on every load (same "healing normalization" shape
 * [ensureMoveAction] already established). [PropDef.id] is set to the manifest asset's own id
 * (never author-typed) so every existing [de.jackbeback.pocketquest.core.model.PropPlacement.prop]
 * value keeps resolving with zero data loss — the whole point of the migration.
 */
fun Catalog.ensurePropDefs(): Catalog {
    val missing = AssetManifest.placeableProps
        .filter { PropId(it.id) !in props }
        .associate { asset -> PropId(asset.id) to PropDef(id = PropId(asset.id), name = asset.id, footprintTilesW = asset.tilesW ?: 1, footprintTilesH = asset.tilesH ?: 1) }
    return if (missing.isEmpty()) this else copy(props = props + missing)
}
