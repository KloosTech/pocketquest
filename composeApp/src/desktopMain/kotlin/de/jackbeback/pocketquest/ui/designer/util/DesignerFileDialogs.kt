package de.jackbeback.pocketquest.ui.designer.util

import de.jackbeback.pocketquest.designer.io.EncounterFileIo
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

internal fun showOpenDialog(): File? {
    val dialog = FileDialog(null as Frame?, "Open Encounter", FileDialog.LOAD).apply {
        directory = EncounterFileIo.defaultDirectory.absolutePath
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json") }
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val file = dialog.file     ?: return null
    return File(dir, file)
}

internal fun showSaveDialog(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, "Save Encounter", FileDialog.SAVE).apply {
        directory = EncounterFileIo.defaultDirectory.absolutePath
        file = "$suggestedName.json"
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val name = dialog.file     ?: return null
    val f = File(dir, if (name.endsWith(".json")) name else "$name.json")
    return f
}

internal fun showImageDialog(): File? {
    val dialog = FileDialog(null as Frame?, "Load Map Image", FileDialog.LOAD).apply {
        filenameFilter = FilenameFilter { _, name ->
            name.endsWith(".png", ignoreCase = true) ||
            name.endsWith(".jpg", ignoreCase = true) ||
            name.endsWith(".jpeg", ignoreCase = true)
        }
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val file = dialog.file     ?: return null
    return File(dir, file)
}

internal fun showJsonDialog(): File? {
    val dialog = FileDialog(null as Frame?, "Load Map JSON", FileDialog.LOAD).apply {
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val dir  = dialog.directory ?: return null
    val file = dialog.file     ?: return null
    return File(dir, file)
}

internal fun showDirDialog(): File? {
    val chooser = javax.swing.JFileChooser().apply {
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select Output Directory"
        val default = defaultResourcesDir()
        if (default != null && default.exists()) currentDirectory = default
    }
    return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
        chooser.selectedFile else null
}

internal fun defaultResourcesDir(): File? {
    var dir = File(System.getProperty("user.dir"))
    repeat(4) {
        val candidate = File(dir, "composeApp/src/commonMain/composeResources")
        if (candidate.exists()) return candidate
        dir = dir.parentFile ?: return null
    }
    return null
}
