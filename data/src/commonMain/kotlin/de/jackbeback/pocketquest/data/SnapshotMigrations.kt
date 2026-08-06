package de.jackbeback.pocketquest.data

import kotlinx.serialization.json.JsonElement

/** Bump whenever [de.jackbeback.pocketquest.core.rules.resolver.Resolver]'s serialized shape changes in a way old snapshots can't decode as-is. */
const val CURRENT_SCHEMA = 1

/**
 * Room migrations don't help with blob *contents* — this is the chain for
 * that. Steps operate on JsonElement, never on the current data classes,
 * so a step doesn't break the moment the class changes again — see
 * docs/06-persistence.md. Empty for now: schema 1 is the first version,
 * there's nothing to migrate from yet. Add a step here (and a golden
 * checked-in snapshot test, per docs/09-test-plan.md) every time
 * CURRENT_SCHEMA increments.
 */
object SnapshotMigrations {
    private val steps: Map<Int, (JsonElement) -> JsonElement> = emptyMap()

    fun migrate(json: JsonElement, from: Int): JsonElement {
        require(from <= CURRENT_SCHEMA) {
            "snapshot schema $from is newer than this build's CURRENT_SCHEMA=$CURRENT_SCHEMA — refusing to guess, update the app"
        }
        return (from until CURRENT_SCHEMA).fold(json) { acc, v -> steps.getValue(v)(acc) }
    }
}
