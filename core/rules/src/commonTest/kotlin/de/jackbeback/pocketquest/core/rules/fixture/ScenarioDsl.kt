package de.jackbeback.pocketquest.core.rules.fixture

import de.jackbeback.pocketquest.core.model.AbilityScores
import de.jackbeback.pocketquest.core.model.Actor
import de.jackbeback.pocketquest.core.model.AiProfileId
import de.jackbeback.pocketquest.core.model.Archetype
import de.jackbeback.pocketquest.core.model.ArchetypeId
import de.jackbeback.pocketquest.core.model.BattleMap
import de.jackbeback.pocketquest.core.model.Catalog
import de.jackbeback.pocketquest.core.model.Controller
import de.jackbeback.pocketquest.core.model.Entity
import de.jackbeback.pocketquest.core.model.EntityId
import de.jackbeback.pocketquest.core.model.Equipment
import de.jackbeback.pocketquest.core.model.Expiry
import de.jackbeback.pocketquest.core.model.Faction
import de.jackbeback.pocketquest.core.model.GameState
import de.jackbeback.pocketquest.core.model.GridPos
import de.jackbeback.pocketquest.core.model.Health
import de.jackbeback.pocketquest.core.model.ActiveStatus
import de.jackbeback.pocketquest.core.model.ItemDef
import de.jackbeback.pocketquest.core.model.ItemId
import de.jackbeback.pocketquest.core.model.ItemInstance
import de.jackbeback.pocketquest.core.model.LinkId
import de.jackbeback.pocketquest.core.model.Modifier
import de.jackbeback.pocketquest.core.model.Resources
import de.jackbeback.pocketquest.core.model.RngState
import de.jackbeback.pocketquest.core.model.Slot
import de.jackbeback.pocketquest.core.model.StackPolicy
import de.jackbeback.pocketquest.core.model.StatusDef
import de.jackbeback.pocketquest.core.model.StatusId
import de.jackbeback.pocketquest.core.model.TurnPhase
import de.jackbeback.pocketquest.core.model.TurnState
import de.jackbeback.pocketquest.core.rules.checkInvariants

/**
 * Small DSL so scenarios read like the rules they encode — see
 * docs/09-test-plan.md#fixtures. Named string ids ("lyra") resolve to
 * EntityId; every scenario is seeded; checkInvariants runs on build().
 */
data class Scenario(
    val state: GameState,
    val catalog: Catalog,
    private val ids: Map<String, EntityId>,
) {
    fun id(name: String): EntityId = ids[name] ?: error("Unknown scenario entity: '$name'")
    fun entity(name: String): Entity = state.byId.getValue(id(name))
}

fun scenario(block: ScenarioBuilder.() -> Unit): Scenario = ScenarioBuilder().apply(block).build()

class ScenarioBuilder {
    private var width = 10
    private var height = 10
    private var seed = 0L
    private val archetypes = mutableMapOf<String, Archetype>()
    private val statusDefs = mutableMapOf<String, StatusDef>()
    private val itemDefs = mutableMapOf<String, ItemDef>()
    private val entityOrder = mutableListOf<String>()
    private val entityBuilders = mutableMapOf<String, EntityBuilder>()
    private val pendingStatuses = mutableListOf<PendingStatus>()
    private var initiativeNames: List<String> = emptyList()

    fun map(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun seed(seed: Long) {
        this.seed = seed
    }

    fun archetype(name: String, block: ArchetypeBuilder.() -> Unit) {
        archetypes[name] = ArchetypeBuilder(name).apply(block).build()
    }

    fun statusDef(name: String, block: StatusDefBuilder.() -> Unit) {
        statusDefs[name] = StatusDefBuilder(name).apply(block).build()
    }

    fun itemDef(name: String, block: ItemDefBuilder.() -> Unit) {
        itemDefs[name] = ItemDefBuilder(name).apply(block).build()
    }

    fun entity(name: String, block: EntityBuilder.() -> Unit) {
        entityOrder += name
        entityBuilders[name] = EntityBuilder().apply(block)
    }

    fun status(entityName: String, statusName: String, stacks: Int = 1, concentration: Boolean = false) {
        pendingStatuses += PendingStatus(entityName, statusName, stacks, concentration)
    }

    fun initiative(vararg names: String) {
        initiativeNames = names.toList()
    }

    fun build(): Scenario {
        val ids = entityOrder.withIndex().associate { (i, name) -> name to EntityId(i.toLong()) }

        val entities = entityOrder.map { name ->
            val b = entityBuilders.getValue(name)
            val archetypeName = b.archetypeName ?: error("entity '$name' missing archetype(...)")
            val statuses = pendingStatuses
                .filter { it.entityName == name }
                .map { ps ->
                    ActiveStatus(
                        def = StatusId(ps.statusName),
                        sourceId = null,
                        linkId = if (ps.concentration) LinkId(ids.getValue(name).raw) else null,
                        stacks = ps.stacks,
                        expiry = Expiry.Permanent,
                        appliedAtVersion = 0,
                    )
                }
            Entity(
                id = ids.getValue(name),
                archetype = ArchetypeId(archetypeName),
                pos = b.pos,
                health = b.hpValue?.let { Health(it) },
                resources = if (b.apValue != null || b.manaValue != null) {
                    Resources(ap = b.apValue ?: 0, mana = b.manaValue ?: 0)
                } else null,
                actor = Actor(b.faction, b.controller),
                equipment = Equipment(b.equipmentSlots.toMap()),
                statuses = statuses,
            )
        }

        val order = (initiativeNames.ifEmpty { entityOrder }).map { ids.getValue(it) }
        val state = GameState(
            entities = entities,
            map = BattleMap(width, height),
            turn = TurnState(round = 1, order = order, activeIndex = 0, phase = TurnPhase.Start),
            rng = RngState(seed),
        )
        val catalog = Catalog(
            archetypes = archetypes.values.associateBy { it.id },
            statuses = statusDefs.values.associateBy { it.id },
            items = itemDefs.values.associateBy { it.id },
        )

        val violations = checkInvariants(state, catalog)
        check(violations.isEmpty()) { "Scenario invariants violated: $violations" }

        return Scenario(state, catalog, ids)
    }

    private data class PendingStatus(val entityName: String, val statusName: String, val stacks: Int, val concentration: Boolean)
}

class ArchetypeBuilder(private val name: String) {
    var hp: Int = 10
    var ac: Int = 10
    var speed: Int = 6
    var ap: Int = 2
    var mana: Int = 0
    private var abilityScores = AbilityScores(10, 10, 10, 10, 10, 10)
    private val innate = mutableListOf<Modifier>()

    fun abilities(str: Int = 10, dex: Int = 10, con: Int = 10, int: Int = 10, wis: Int = 10, cha: Int = 10) {
        abilityScores = AbilityScores(str, dex, con, int, wis, cha)
    }

    fun modifier(m: Modifier) {
        innate += m
    }

    fun build(): Archetype = Archetype(
        id = ArchetypeId(name),
        name = name,
        abilities = abilityScores,
        baseMaxHp = hp,
        baseAc = ac,
        speedTiles = speed,
        baseMaxAp = ap,
        baseMaxMana = mana,
        innateModifiers = innate.toList(),
    )
}

class StatusDefBuilder(private val name: String) {
    var stackPolicy: StackPolicy = StackPolicy.Refresh
    private val mods = mutableListOf<Modifier>()

    fun modifier(m: Modifier) {
        mods += m
    }

    fun build(): StatusDef = StatusDef(StatusId(name), name, stackPolicy, mods.toList())
}

class ItemDefBuilder(private val name: String) {
    private val mods = mutableListOf<Modifier>()

    fun modifier(m: Modifier) {
        mods += m
    }

    fun build(): ItemDef = ItemDef(ItemId(name), name, mods.toList())
}

class EntityBuilder {
    var archetypeName: String? = null
        private set
    var pos: GridPos? = null
        private set
    var hpValue: Int? = null
        private set
    var apValue: Int? = null
        private set
    var manaValue: Int? = null
        private set
    var faction: Faction = Faction.Player
        private set
    var controller: Controller = Controller.Human
        private set
    val equipmentSlots: MutableMap<Slot, ItemInstance> = mutableMapOf()

    fun archetype(name: String) {
        archetypeName = name
    }

    fun at(col: Int, row: Int) {
        pos = GridPos(col, row)
    }

    fun hp(v: Int) {
        hpValue = v
    }

    fun ap(v: Int) {
        apValue = v
    }

    fun mana(v: Int) {
        manaValue = v
    }

    fun faction(f: Faction) {
        faction = f
    }

    fun ai(profile: String = "default") {
        controller = Controller.Ai(AiProfileId(profile))
    }

    fun equip(slot: Slot, itemName: String, enchantment: Int = 0, attuned: Boolean = false) {
        equipmentSlots[slot] = ItemInstance(ItemId(itemName), enchantment, attuned = attuned)
    }
}
