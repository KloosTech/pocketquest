package de.jackbeback.pocketquest.designer.io

import de.jackbeback.pocketquest.designer.model.EncounterBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Pretty-printing JSON instance for encounter files. */
val encounterJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object EncounterFileIo {

    fun save(encounter: EncounterBundle, file: File): Result<Unit> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(encounterJson.encodeToString(encounter))
    }

    fun load(file: File): Result<EncounterBundle> = runCatching {
        encounterJson.decodeFromString<EncounterBundle>(file.readText())
    }

    fun listEncounters(directory: File): List<File> =
        directory.listFiles { f -> f.extension == "json" }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()

    /** Default directory where encounter files are saved. */
    val defaultDirectory: File
        get() = File(System.getProperty("user.home"), "PocketQuestEncounters").also { it.mkdirs() }
}
