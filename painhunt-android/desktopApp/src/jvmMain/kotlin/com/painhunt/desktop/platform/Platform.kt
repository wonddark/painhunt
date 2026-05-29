package com.painhunt.desktop.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

fun openUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

/** Opens a native file chooser filtered to .json and returns the file bytes, or null if cancelled. */
fun pickJsonFileBytes(): ByteArray? {
    val dialog = FileDialog(null as Frame?, "Import JSON", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name).readBytes()
}
