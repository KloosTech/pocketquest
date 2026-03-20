package de.jackbeback.pocketquest.ui.character

import de.jackbeback.pocketquest.content.dsl.StatAttribute
import de.jackbeback.pocketquest.content.dsl.attributeModifier
import de.jackbeback.pocketquest.content.registry.SkillRegistry
import de.jackbeback.pocketquest.ecs.components.combat.SkillSetComponent
import de.jackbeback.pocketquest.ecs.components.core.Faction
import de.jackbeback.pocketquest.ecs.components.core.FactionComponent
import de.jackbeback.pocketquest.ecs.components.core.HealthComponent
import de.jackbeback.pocketquest.ecs.components.core.ManaComponent
import de.jackbeback.pocketquest.ecs.components.core.NameComponent
import de.jackbeback.pocketquest.ecs.components.core.StatsComponent
import de.jackbeback.pocketquest.ecs.core.World
import de.jackbeback.pocketquest.ecs.core.get
import de.jackbeback.pocketquest.ecs.core.query
import de.jackbeback.pocketquest.game.run.RunScopedState
import de.jackbeback.pocketquest.game.run.RunStateHolder
import de.jackbeback.pocketquest.ui.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AttributeUiState(
    val name: String,
    val abbrev: String,
    /** Key used in RunScopedState.attributeBonuses: "str", "dex", "con", "intelligence", "wis", "cha" */
    val key: String,
    val value: Int,
    val modifier: Int,
)

data class SkillCharacterUiState(
    val id: String,
    val name: String,
    val manaCost: Int,
    val description: String,
    val attributeModifier: StatAttribute?,
    val modifierValue: Int,
    val diceDescription: String,
)

data class CharacterUiState(
    val name: String,
    val level: Int,
    val exp: Int,
    val expToNextLevel: Int,
    val hp: HealthComponent,
    val mana: ManaComponent,
    val ac: Int,
    val attributes: List<AttributeUiState>,
    val skills: List<SkillCharacterUiState>,
    val relics: List<String>,
    val unspentPoints: Int,
)

class CharacterViewModel(
    private val world: World,
    private val runStateHolder: RunStateHolder,
    private val skillRegistry: SkillRegistry,
    private val navigator: Navigator,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val state: StateFlow<CharacterUiState?> = runStateHolder.run
        .map { run -> if (run == null) null else snapshot(run) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun spendPoint(attr: String) {
        runStateHolder.spendAttributePoint(attr)
    }

    fun goBack() {
        navigator.returnToOverworld()
    }

    private fun snapshot(run: RunScopedState): CharacterUiState {
        val playerId = world.query<FactionComponent>()
            .filter { (_, f) -> f.faction == Faction.PLAYER }
            .firstOrNull()?.first

        val stats = playerId?.let { world.get<StatsComponent>(it) }
            ?: StatsComponent(10, 10, 10, 10, 10, 10, 10)
        val hp   = playerId?.let { world.get<HealthComponent>(it) } ?: HealthComponent(0, 0)
        val mana = playerId?.let { world.get<ManaComponent>(it) }   ?: ManaComponent(0, 0)
        val name = playerId?.let { world.get<NameComponent>(it) }?.displayName
            ?: run.characterTemplateId.replaceFirstChar { it.uppercase() }
        val skillIds = playerId?.let { world.get<SkillSetComponent>(it) }?.skills ?: emptyList()

        val attributes = listOf(
            AttributeUiState("Strength",     "STR", "str",          stats.str,          attributeModifier(stats.str)),
            AttributeUiState("Dexterity",    "DEX", "dex",          stats.dex,          attributeModifier(stats.dex)),
            AttributeUiState("Constitution", "CON", "con",          stats.con,          attributeModifier(stats.con)),
            AttributeUiState("Intelligence", "INT", "intelligence", stats.intelligence, attributeModifier(stats.intelligence)),
            AttributeUiState("Wisdom",       "WIS", "wis",          stats.wis,          attributeModifier(stats.wis)),
            AttributeUiState("Charisma",     "CHA", "cha",          stats.cha,          attributeModifier(stats.cha)),
        )

        val skills = skillIds.mapNotNull { id -> skillRegistry.find(id) }.map { tpl ->
            val attrMod = tpl.attributeModifier
            val statValue = when (attrMod) {
                StatAttribute.STR -> stats.str
                StatAttribute.DEX -> stats.dex
                StatAttribute.CON -> stats.con
                StatAttribute.INT -> stats.intelligence
                StatAttribute.WIS -> stats.wis
                StatAttribute.CHA -> stats.cha
                null              -> 10
            }
            val modValue = if (attrMod != null) attributeModifier(statValue) else 0

            // Build a human-readable dice summary from the first damage/heal effect
            val diceDesc = tpl.effects.firstNotNullOfOrNull { effect ->
                when (effect) {
                    is de.jackbeback.pocketquest.content.dsl.SkillEffect.Damage -> {
                        val d = effect.dice
                        val base = "${d.count}d${d.sides}" + if (d.bonus != 0) "+${d.bonus}" else ""
                        "$base ${effect.type.name}"
                    }
                    is de.jackbeback.pocketquest.content.dsl.SkillEffect.Heal -> {
                        val d = effect.dice
                        "${d.count}d${d.sides}" + if (d.bonus != 0) "+${d.bonus}" else ""
                    }
                    else -> null
                }
            } ?: ""

            SkillCharacterUiState(
                id                = tpl.id,
                name              = tpl.name,
                manaCost          = tpl.manaCost,
                description       = tpl.description,
                attributeModifier = attrMod,
                modifierValue     = modValue,
                diceDescription   = diceDesc,
            )
        }

        return CharacterUiState(
            name           = name,
            level          = run.level,
            exp            = run.exp,
            expToNextLevel = run.level * 100,
            hp             = hp,
            mana           = mana,
            ac             = stats.ac,
            attributes     = attributes,
            skills         = skills,
            relics         = run.relics,
            unspentPoints  = run.unspentAttributePoints,
        )
    }
}
