package de.jackbeback.pocketquest.game.hint

import org.jetbrains.compose.resources.StringResource
import pocketquest.composeapp.generated.resources.Res
import pocketquest.composeapp.generated.resources.hint_body_battle_intro
import pocketquest.composeapp.generated.resources.hint_body_boss
import pocketquest.composeapp.generated.resources.hint_body_conditions
import pocketquest.composeapp.generated.resources.hint_body_level_up
import pocketquest.composeapp.generated.resources.hint_body_overworld
import pocketquest.composeapp.generated.resources.hint_body_relic
import pocketquest.composeapp.generated.resources.hint_body_rest
import pocketquest.composeapp.generated.resources.hint_body_skills
import pocketquest.composeapp.generated.resources.hint_title_battle_intro
import pocketquest.composeapp.generated.resources.hint_title_boss
import pocketquest.composeapp.generated.resources.hint_title_conditions
import pocketquest.composeapp.generated.resources.hint_title_level_up
import pocketquest.composeapp.generated.resources.hint_title_overworld
import pocketquest.composeapp.generated.resources.hint_title_relic
import pocketquest.composeapp.generated.resources.hint_title_rest
import pocketquest.composeapp.generated.resources.hint_title_skills

data class HintDefinition(
    val id: String,
    val titleRes: StringResource,
    val bodyRes: StringResource,
)

/** All in-game tutorial hints. Each is shown at most once (persisted in Room). */
val allHints: List<HintDefinition> = listOf(
    HintDefinition(
        id       = "hint_battle_intro",
        titleRes = Res.string.hint_title_battle_intro,
        bodyRes  = Res.string.hint_body_battle_intro,
    ),
    HintDefinition(
        id       = "hint_skills",
        titleRes = Res.string.hint_title_skills,
        bodyRes  = Res.string.hint_body_skills,
    ),
    HintDefinition(
        id       = "hint_conditions",
        titleRes = Res.string.hint_title_conditions,
        bodyRes  = Res.string.hint_body_conditions,
    ),
    HintDefinition(
        id       = "hint_overworld",
        titleRes = Res.string.hint_title_overworld,
        bodyRes  = Res.string.hint_body_overworld,
    ),
    HintDefinition(
        id       = "hint_rest",
        titleRes = Res.string.hint_title_rest,
        bodyRes  = Res.string.hint_body_rest,
    ),
    HintDefinition(
        id       = "hint_level_up",
        titleRes = Res.string.hint_title_level_up,
        bodyRes  = Res.string.hint_body_level_up,
    ),
    HintDefinition(
        id       = "hint_relic",
        titleRes = Res.string.hint_title_relic,
        bodyRes  = Res.string.hint_body_relic,
    ),
    HintDefinition(
        id       = "hint_boss",
        titleRes = Res.string.hint_title_boss,
        bodyRes  = Res.string.hint_body_boss,
    ),
)
