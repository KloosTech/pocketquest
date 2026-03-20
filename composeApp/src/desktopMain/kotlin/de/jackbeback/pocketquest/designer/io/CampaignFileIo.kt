package de.jackbeback.pocketquest.designer.io

import de.jackbeback.pocketquest.designer.model.CampaignBundle
import de.jackbeback.pocketquest.designer.model.DesignerConfig
import de.jackbeback.pocketquest.designer.model.EncounterBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private val campaignJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object CampaignFileIo {

    val defaultCampaignsRoot: File
        get() = File(System.getProperty("user.home"), "PocketQuestCampaigns").also { it.mkdirs() }

    private val configDir: File
        get() = File(System.getProperty("user.home"), ".pocketquest").also { it.mkdirs() }

    private val configFile: File
        get() = File(configDir, "config.json")

    private fun campaignDir(campaignId: String): File =
        File(defaultCampaignsRoot, campaignId)

    private fun encountersDir(campaignId: String): File =
        File(campaignDir(campaignId), "encounters")

    // ── Campaign CRUD ─────────────────────────────────────────────────────────

    fun saveCampaign(campaign: CampaignBundle): Result<File> = runCatching {
        val dir = campaignDir(campaign.id).also { it.mkdirs() }
        val file = File(dir, "campaign.json")
        file.writeText(campaignJson.encodeToString(campaign))
        file
    }

    fun loadCampaign(dir: File): Result<CampaignBundle> = runCatching {
        val file = File(dir, "campaign.json")
        campaignJson.decodeFromString<CampaignBundle>(file.readText())
    }

    fun listCampaigns(): List<File> =
        defaultCampaignsRoot
            .listFiles { f -> f.isDirectory && File(f, "campaign.json").exists() }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun deleteCampaign(campaignId: String): Result<Unit> = runCatching {
        val dir = campaignDir(campaignId)
        dir.deleteRecursively()
        Unit
    }

    // ── Encounter I/O within a campaign ───────────────────────────────────────

    fun saveEncounterInCampaign(campaignId: String, enc: EncounterBundle): Result<Unit> = runCatching {
        val dir = encountersDir(campaignId).also { it.mkdirs() }
        File(dir, "${enc.id}.json").writeText(campaignJson.encodeToString(enc))
    }

    fun loadEncounterFromCampaign(campaignId: String, encId: String): Result<EncounterBundle> = runCatching {
        val file = File(encountersDir(campaignId), "$encId.json")
        campaignJson.decodeFromString<EncounterBundle>(file.readText())
    }

    fun loadAllEncountersInCampaign(campaignId: String): List<EncounterBundle> {
        val dir = encountersDir(campaignId)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { campaignJson.decodeFromString<EncounterBundle>(f.readText()) }.getOrNull() }
            ?: emptyList()
    }

    // ── Config ────────────────────────────────────────────────────────────────

    fun saveConfig(config: DesignerConfig): Result<Unit> = runCatching {
        configFile.writeText(campaignJson.encodeToString(config))
    }

    fun loadConfig(): Result<DesignerConfig> = runCatching {
        campaignJson.decodeFromString<DesignerConfig>(configFile.readText())
    }
}
