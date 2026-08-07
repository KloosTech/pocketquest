package de.jackbeback.pocketquest.core.rules.action

import de.jackbeback.pocketquest.core.model.ActionId
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity

/**
 * doc17-engine-gaps.md 1.6: `Archetype.actions` stops being the only action source once leveling
 * exists. Derived, not stored — same call-site pattern as `stats(cat)` — resolving each of
 * `Entity.features` through [cat] rather than baking a resolved action list onto `Entity` itself,
 * which would need to be kept in sync by whatever eventually builds one (deviates from doc11's
 * literal "Entity needs a grantedActions field" wording; chosen instead because a second stored
 * field that must agree with `features` has no enforcement and this project derives everything
 * else on `Entity` — `stats()`, `Modifier.Roll` grants — the same way).
 */
fun Entity.grantedActions(cat: Catalog): List<ActionId> =
    features.flatMap { cat.featureDef(it).grantsActions }

/** Every action this entity can currently take, from any source — archetype-innate plus feature grants. */
fun Entity.allActions(cat: Catalog): List<ActionId> =
    cat.archetype(archetype).actions + grantedActions(cat)
