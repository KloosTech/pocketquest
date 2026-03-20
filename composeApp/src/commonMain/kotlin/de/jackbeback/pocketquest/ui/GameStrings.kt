package de.jackbeback.pocketquest.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.relic_desc_ancient_tome
import pocketquest.composeapp.generated.resources.relic_desc_arcane_focus
import pocketquest.composeapp.generated.resources.relic_desc_blood_pact
import pocketquest.composeapp.generated.resources.relic_desc_iron_will
import pocketquest.composeapp.generated.resources.relic_desc_mage_stone
import pocketquest.composeapp.generated.resources.relic_desc_scholars_ring
import pocketquest.composeapp.generated.resources.relic_desc_war_trophy
import pocketquest.composeapp.generated.resources.relic_desc_well_of_power
import pocketquest.composeapp.generated.resources.relic_name_ancient_tome
import pocketquest.composeapp.generated.resources.relic_name_arcane_focus
import pocketquest.composeapp.generated.resources.relic_name_blood_pact
import pocketquest.composeapp.generated.resources.relic_name_iron_will
import pocketquest.composeapp.generated.resources.relic_name_mage_stone
import pocketquest.composeapp.generated.resources.relic_name_scholars_ring
import pocketquest.composeapp.generated.resources.relic_name_war_trophy
import pocketquest.composeapp.generated.resources.relic_name_well_of_power
import pocketquest.composeapp.generated.resources.skill_desc_basic_attack
import pocketquest.composeapp.generated.resources.skill_desc_basic_heal
import pocketquest.composeapp.generated.resources.skill_desc_block
import pocketquest.composeapp.generated.resources.skill_desc_fireball
import pocketquest.composeapp.generated.resources.skill_desc_magic_missile
import pocketquest.composeapp.generated.resources.skill_desc_thorn_whip
import pocketquest.composeapp.generated.resources.skill_name_basic_attack
import pocketquest.composeapp.generated.resources.skill_name_basic_heal
import pocketquest.composeapp.generated.resources.skill_name_block
import pocketquest.composeapp.generated.resources.skill_name_fireball
import pocketquest.composeapp.generated.resources.skill_name_magic_missile
import pocketquest.composeapp.generated.resources.skill_name_thorn_whip
import pocketquest.composeapp.generated.resources.unit_name_chieftain
import pocketquest.composeapp.generated.resources.unit_name_grunt
import pocketquest.composeapp.generated.resources.unit_name_veteran_grunt
import pocketquest.composeapp.generated.resources.unit_name_wizard

/** Returns the localized display name for the given relic ID. */
@Composable
fun relicLocalizedName(id: String): String = stringResource(
    when (id) {
        "ancient_tome"  -> Res.string.relic_name_ancient_tome
        "iron_will"     -> Res.string.relic_name_iron_will
        "war_trophy"    -> Res.string.relic_name_war_trophy
        "mage_stone"    -> Res.string.relic_name_mage_stone
        "well_of_power" -> Res.string.relic_name_well_of_power
        "scholars_ring" -> Res.string.relic_name_scholars_ring
        "blood_pact"    -> Res.string.relic_name_blood_pact
        "arcane_focus"  -> Res.string.relic_name_arcane_focus
        else            -> Res.string.relic_name_ancient_tome
    }
)

/** Returns the localized description for the given relic ID. */
@Composable
fun relicLocalizedDescription(id: String): String = stringResource(
    when (id) {
        "ancient_tome"  -> Res.string.relic_desc_ancient_tome
        "iron_will"     -> Res.string.relic_desc_iron_will
        "war_trophy"    -> Res.string.relic_desc_war_trophy
        "mage_stone"    -> Res.string.relic_desc_mage_stone
        "well_of_power" -> Res.string.relic_desc_well_of_power
        "scholars_ring" -> Res.string.relic_desc_scholars_ring
        "blood_pact"    -> Res.string.relic_desc_blood_pact
        "arcane_focus"  -> Res.string.relic_desc_arcane_focus
        else            -> Res.string.relic_desc_ancient_tome
    }
)

/** Returns the localized display name for the given skill ID. */
@Composable
fun skillLocalizedName(id: String): String = stringResource(
    when (id) {
        "magic_missile" -> Res.string.skill_name_magic_missile
        "basic_heal"    -> Res.string.skill_name_basic_heal
        "thorn_whip"    -> Res.string.skill_name_thorn_whip
        "fireball"      -> Res.string.skill_name_fireball
        "basic_attack"  -> Res.string.skill_name_basic_attack
        "block"         -> Res.string.skill_name_block
        else            -> Res.string.skill_name_basic_attack
    }
)

/** Returns the localized description for the given skill ID. */
@Composable
fun skillLocalizedDescription(id: String): String = stringResource(
    when (id) {
        "magic_missile" -> Res.string.skill_desc_magic_missile
        "basic_heal"    -> Res.string.skill_desc_basic_heal
        "thorn_whip"    -> Res.string.skill_desc_thorn_whip
        "fireball"      -> Res.string.skill_desc_fireball
        "basic_attack"  -> Res.string.skill_desc_basic_attack
        "block"         -> Res.string.skill_desc_block
        else            -> Res.string.skill_desc_basic_attack
    }
)

/** Returns the localized display name for the given unit template ID. */
@Composable
fun unitLocalizedName(id: String): String = stringResource(
    when (id) {
        "wizard"         -> Res.string.unit_name_wizard
        "grunt"          -> Res.string.unit_name_grunt
        "veteran_grunt"  -> Res.string.unit_name_veteran_grunt
        "chieftain"      -> Res.string.unit_name_chieftain
        else             -> Res.string.unit_name_wizard
    }
)
