package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Flag
import de.jackbeback.pocketquest.core.model.Modifier

/**
 * doc17-engine-gaps.md 2.3 / doc18's "Taunt is a different mechanism": which entities [this] is
 * currently taunted by. `Stats.flags` (the generic derived-modifier set) can answer "is Taunted
 * active" but not "by whom" — Taunt is the one flag whose *source* actually matters downstream
 * (`:core:ai`'s target narrowing), so it's read directly off `statuses` rather than through the
 * generic flags path. A status with no `sourceId` can't taunt anything (there's no one to narrow
 * targeting toward) and is silently excluded rather than treated as an error — content authoring
 * only, not a resolver concern.
 */
fun Entity.tauntedBy(cat: Catalog): Set<EntityId> =
    statuses
        .filter { status -> cat.statusDef(status.def).modifiers.any { it is Modifier.Grant && it.flag == Flag.Taunted } }
        .mapNotNull { it.sourceId }
        .toSet()
