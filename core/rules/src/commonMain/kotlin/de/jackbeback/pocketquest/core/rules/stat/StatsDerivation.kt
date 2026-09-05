package de.jackbeback.pocketquest.core.rules.stat

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.DamageType
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.Flag
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.plus
import de.jackbeback.pocketquest.core.model.Resistance
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.Stat
import de.jackbeback.pocketquest.core.model.Stats

/**
 * One entry in the fixed source order used for derivation. `sourceOrder` is
 * only meaningful relative to other entries produced in the same call to
 * [stats] — it is how `Override` ties resolve deterministically instead of
 * by (unordered) set/map iteration.
 */
private data class OrderedModifier(val sourceOrder: Int, val modifier: Modifier)

/**
 * `base (archetype) -> Add (summed) -> Mul (multiplied) -> Override (last
 * writer wins, by sourceOrder) -> clamp`. Sources, in fixed order: archetype
 * innate, worn equipment (Slot enum order), statuses (by StatusId then
 * appliedAtVersion) — see docs/03-modifiers-and-status.md.
 *
 * `ActiveStatus`/`ItemInstance` carry only a def id, so their modifiers are
 * resolved through [cat] here, not stored on the instance — see
 * [de.jackbeback.pocketquest.core.model.ModifierSource]. Stacks scale a
 * status's `Add` modifiers linearly (its `Mul`/`Override`/`Grant`/`Resist`/
 * `Roll` modifiers apply once regardless of stack count — repeating those
 * has no well-defined meaning).
 */
fun Entity.stats(cat: Catalog): Stats {
    val archetype = cat.archetype(archetype)

    var order = 0
    val ordered = mutableListOf<OrderedModifier>()
    fun addAll(mods: List<Modifier>) {
        for (m in mods) ordered += OrderedModifier(order, m)
        order++
    }

    addAll(archetype.innateModifiers)

    // doc17-engine-gaps.md 1.7: feature modifiers join the fixed source order right after
    // archetype innate and before equipment — doc11-run-state.md's own ordering instruction.
    // List order (not sorted, unlike statuses) — features are an authored/chosen sequence on the
    // entity already, not an unordered multiset that needs a deterministic tie-break imposed.
    for (featureId in features) {
        addAll(cat.featureDef(featureId).modifiers)
    }

    for (slot in Slot.entries) {
        val item = equipment.slots[slot] ?: continue
        addAll(cat.itemDef(item.def).modifiers)
    }

    for (status in statuses.sortedWith(compareBy({ it.def.raw }, { it.appliedAtVersion }))) {
        val def = cat.statusDef(status.def)
        val scaled = def.modifiers.map { m ->
            if (m is Modifier.Add) m.copy(value = m.value * status.stacks) else m
        }
        addAll(scaled)
    }

    // A champion's point-buy (abilityBonuses, zero for every non-champion entity) sits on top of
    // the archetype baseline here, before the Add/Mul/Override modifier pipeline below runs — it's
    // part of the entity's own baseline, not a modifier source.
    val abilities = archetype.abilities + abilityBonuses
    val work = WorkingStats(
        maxHp = archetype.baseMaxHp,
        armorClass = archetype.baseAc,
        speedTiles = archetype.speedTiles,
        maxAp = archetype.baseMaxAp,
        maxMana = archetype.baseMaxMana,
        str = abilities.str,
        dex = abilities.dex,
        con = abilities.con,
        int = abilities.int,
        wis = abilities.wis,
        cha = abilities.cha,
    )

    for (om in ordered) (om.modifier as? Modifier.Add)?.let { work.add(it.stat, it.value) }
    for (om in ordered) (om.modifier as? Modifier.Mul)?.let { work.mul(it.stat, it.factor) }

    val overrides = mutableMapOf<Stat, Int>()
    for (om in ordered) (om.modifier as? Modifier.Override)?.let { overrides[it.stat] = it.value }
    for ((stat, value) in overrides) work.set(stat, value)

    val flags = mutableSetOf<Flag>()
    for (om in ordered) (om.modifier as? Modifier.Grant)?.let { flags += it.flag }

    val resistances = mutableMapOf<DamageType, Resistance>()
    for (om in ordered) (om.modifier as? Modifier.Resist)?.let {
        val current = resistances[it.damageType]
        if (current == null || it.level.protectiveness() > current.protectiveness()) {
            resistances[it.damageType] = it.level
        }
    }

    // Unresolved on purpose — matching a grant's RollContext against an actual roll (e.g.
    // "advantage vs Enemy" against a specific attack's real target) happens at the roll site via
    // RollContext.matches(), not here. See KNOWN_ISSUES.md #11.
    val rollGrants = ordered.mapNotNull { it.modifier as? Modifier.Roll }

    return work.toStats(flags, resistances, rollGrants)
}

private fun Resistance.protectiveness(): Int = when (this) {
    Resistance.Vulnerable -> -1
    Resistance.None -> 0
    Resistance.Resistant -> 1
    Resistance.Immune -> 2
}

private class WorkingStats(
    var maxHp: Int, var armorClass: Int, var speedTiles: Int, var maxAp: Int, var maxMana: Int,
    var str: Int, var dex: Int, var con: Int, var int: Int, var wis: Int, var cha: Int,
) {
    fun get(stat: Stat): Int = when (stat) {
        Stat.MaxHp -> maxHp
        Stat.ArmorClass -> armorClass
        Stat.SpeedTiles -> speedTiles
        Stat.MaxAp -> maxAp
        Stat.MaxMana -> maxMana
        Stat.Str -> str
        Stat.Dex -> dex
        Stat.Con -> con
        Stat.Int -> int
        Stat.Wis -> wis
        Stat.Cha -> cha
    }

    fun set(stat: Stat, value: Int) {
        when (stat) {
            Stat.MaxHp -> maxHp = value
            Stat.ArmorClass -> armorClass = value
            Stat.SpeedTiles -> speedTiles = value
            Stat.MaxAp -> maxAp = value
            Stat.MaxMana -> maxMana = value
            Stat.Str -> str = value
            Stat.Dex -> dex = value
            Stat.Con -> con = value
            Stat.Int -> int = value
            Stat.Wis -> wis = value
            Stat.Cha -> cha = value
        }
    }

    fun add(stat: Stat, value: Int) = set(stat, get(stat) + value)
    fun mul(stat: Stat, factor: Float) = set(stat, (get(stat) * factor).toInt())

    fun toStats(flags: Set<Flag>, resistances: Map<DamageType, Resistance>, rollGrants: List<Modifier.Roll>): Stats = Stats(
        maxHp = maxHp.coerceAtLeast(1),
        armorClass = armorClass.coerceAtLeast(0),
        speedTiles = speedTiles.coerceAtLeast(0),
        maxAp = maxAp.coerceAtLeast(0),
        maxMana = maxMana.coerceAtLeast(0),
        abilities = AbilityScores(str, dex, con, int, wis, cha),
        flags = flags,
        resistances = resistances,
        rollGrants = rollGrants,
    )
}
