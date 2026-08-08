package de.jackbeback.pocketquest.designer

import de.jackbeback.pocketquest.core.model.BattleMapDef
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.MapId
import de.jackbeback.pocketquest.core.model.SpawnRole
import de.jackbeback.pocketquest.core.model.SpawnZone

/**
 * "Just enough map schema to reference/pick from a couple of hand-authored test maps" — the
 * user's own scoping decision for this pass. No terrain-painting UI exists yet (that's the
 * separate Map editor doc16 describes), so these are hand-built in code rather than through a
 * tool, all-floor apart from the spawn zones themselves — real terrain content is a future pass.
 */
fun seedMaps(): List<BattleMapDef> = listOf(
    BattleMapDef(
        id = MapId("smallRoom"),
        width = 8,
        height = 8,
        spawns = listOf(
            SpawnZone(SpawnRole.Party, listOf(GridPos(0, 0), GridPos(1, 0), GridPos(0, 1))),
            SpawnZone(SpawnRole.Enemy, listOf(GridPos(6, 6), GridPos(7, 6), GridPos(6, 7))),
        ),
    ),
    BattleMapDef(
        id = MapId("corridor"),
        width = 12,
        height = 6,
        spawns = listOf(
            SpawnZone(SpawnRole.Party, listOf(GridPos(0, 2), GridPos(0, 3))),
            SpawnZone(SpawnRole.Enemy, listOf(GridPos(9, 1), GridPos(10, 2), GridPos(9, 3), GridPos(11, 2), GridPos(10, 4))),
            SpawnZone(SpawnRole.Boss, listOf(GridPos(11, 2))),
        ),
    ),
)

/** Starting point for a fresh working catalog — the seed maps plus the baseline "move" action, nothing else. Load replaces this wholesale, it never merges. */
fun freshCatalog(): Catalog = Catalog(maps = seedMaps().associateBy { it.id }).ensureMoveAction()
