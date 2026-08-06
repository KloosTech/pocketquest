package de.jackbeback.pocketquest.core.content

import de.jackbeback.pocketquest.core.model.ArchetypeId
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private val BAD_DISCRIMINATOR_CATALOG = """
{
  "actions": {
    "x": {
      "id": "x",
      "name": "X",
      "cost": { "action": { "type": "explode" }, "mana": 0, "charges": null, "hpCost": 0 },
      "targeting": {
        "mode": "SingleEntity",
        "range": { "type": "melee" },
        "shape": { "type": "single" }
      },
      "effects": []
    }
  }
}
""".trimIndent()

class CatalogLoaderTest {

    @Test
    fun parseDecodesAWellFormedCatalogUsingFieldDefaultsForOmittedSections() {
        val catalog = CatalogLoader.parse("{}")
        assertTrue(catalog.archetypes.isEmpty() && catalog.statuses.isEmpty() && catalog.items.isEmpty() && catalog.actions.isEmpty())
    }

    @Test
    fun parseWrapsMalformedJsonInACatalogParseException() {
        assertFailsWith<CatalogParseException> { CatalogLoader.parse("{ not valid json") }
    }

    @Test
    fun parseWrapsAnUnknownSealedDiscriminatorInACatalogParseException() {
        assertFailsWith<CatalogParseException> { CatalogLoader.parse(BAD_DISCRIMINATOR_CATALOG) }
    }

    @Test
    fun theSampleCatalogFixtureParsesAndPassesValidation() {
        val catalog = CatalogLoader.parse(SAMPLE_CATALOG_JSON)
        assertTrue(catalog.archetypes.containsKey(ArchetypeId("fighter")))
        CatalogValidator.validate(catalog)
    }
}
