package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.Catalog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class CatalogParseException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * Turns authored JSON text into a [Catalog]. Parsing alone does not
 * guarantee the catalog is playable — dangling id references (an action
 * naming a status that was never defined) parse just fine and only fail
 * much later, deep in the resolver. Run [CatalogValidator] over the result
 * before trusting it. Reading the JSON off disk/assets/bundle is a platform
 * concern left to :app/:data — this module only turns text into data.
 */
object CatalogLoader {

    fun parse(text: String): Catalog =
        try {
            Json.decodeFromString(Catalog.serializer(), text)
        } catch (e: SerializationException) {
            throw CatalogParseException("Malformed catalog JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw CatalogParseException("Malformed catalog JSON: ${e.message}", e)
        }
}
