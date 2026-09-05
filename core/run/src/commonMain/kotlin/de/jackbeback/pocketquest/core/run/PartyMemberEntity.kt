package de.jackbeback.pocketquest.core.run

import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.rules.stat.stats

/**
 * Builds a throwaway [Entity] from a [PartyMember] so `:core:rules`' `stats()`/`grantedActions()`
 * can derive its real (equipment-inclusive) max HP/mana and granted features — used both by
 * [checkRunInvariants] (a member's `hp`/`mana` bounds depend on derived stats, not the archetype
 * baseline alone) and by the encounter handoff (`startEncounter`/`finishEncounter`) that actually
 * spawns a battle `Entity` from a `PartyMember`. [id] defaults to 0 since invariant-checking never
 * needs a real one; the handoff passes the real assigned id.
 *
 * `features` is derived from equipped items' `ItemDef.grantsFeature` (docs/11-run-state.md's "Gear
 * without breaking Archetype") — this is the one place that resolution actually happens.
 */
fun PartyMember.toEntity(cat: Catalog, id: EntityId = EntityId(0)): Entity {
    val grantedFeatures = equipment.slots.values.mapNotNull { instance -> cat.items[instance.def]?.grantsFeature }
    return Entity(
        id = id,
        archetype = archetype,
        pos = null,
        health = Health(hp),
        resources = Resources(ap = 0, mana = mana),
        actor = Actor(Faction.Player, controller),
        equipment = equipment,
        features = grantedFeatures,
        abilityBonuses = abilityBonuses,
    )
}

/** A fresh `PartyMember` at its derived (equipment-inclusive) max HP/mana — used at party formation, before any encounter has happened to it. */
fun PartyMember.atFullHealth(cat: Catalog): PartyMember {
    val stats = toEntity(cat).stats(cat)
    return copy(hp = stats.maxHp, mana = stats.maxMana)
}
