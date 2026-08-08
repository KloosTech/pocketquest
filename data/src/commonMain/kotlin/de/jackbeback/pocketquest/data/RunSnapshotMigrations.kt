package de.jackbeback.pocketquest.data

import de.jackbeback.pocketquest.core.run.CURRENT_RUN_SCHEMA
import kotlinx.serialization.json.JsonElement

/**
 * [RunState][de.jackbeback.pocketquest.core.run.RunState]'s own migration chain — deliberately
 * separate from [SnapshotMigrations] (the combat `Resolver` blob's chain): docs/11-run-state.md
 * is explicit that "combat and run shapes will not change in lockstep." Same shape as
 * `SnapshotMigrations` otherwise — steps operate on `JsonElement`, never the current data classes.
 */
object RunSnapshotMigrations {
    private val steps: Map<Int, (JsonElement) -> JsonElement> = emptyMap()

    fun migrate(json: JsonElement, from: Int): JsonElement {
        require(from <= CURRENT_RUN_SCHEMA) {
            "run snapshot schema $from is newer than this build's CURRENT_RUN_SCHEMA=$CURRENT_RUN_SCHEMA — refusing to guess, update the app"
        }
        return (from until CURRENT_RUN_SCHEMA).fold(json) { acc, v -> steps.getValue(v)(acc) }
    }
}
