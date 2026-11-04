package com.maksym.financetracker

import androidx.compose.runtime.*
import com.maksym.financetracker.data.desktopRepository
import com.maksym.financetracker.domain.ensure
import com.maksym.financetracker.presentation.FileGateway
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import java.io.File

@Composable fun DesktopFinanceApp() {
    val files = remember { DesktopFiles() }
    App({ desktopRepository() }, files)
}

class DesktopFiles : FileGateway {
    override suspend fun save(name: String, content: String): Boolean {
        val selected = withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply { selectedFile = File(name) }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile.takeIf { !it.exists() || JOptionPane.showConfirmDialog(null, "Replace ${it.name}?", "Replace file", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION }
        } ?: return false
        withContext(Dispatchers.IO) {
            val target = selected.toPath().toAbsolutePath()
            val temporary = Files.createTempFile(target.parent, ".personal-finance-tracker-", ".tmp")
            try {
                Files.writeString(temporary, content)
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING) }
            } finally { Files.deleteIfExists(temporary) }
        }
        return true
    }
    override suspend fun readBackup(): String? {
        val selected = withContext(Dispatchers.Swing) { val chooser = JFileChooser(); if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null } ?: return null
        return withContext(Dispatchers.IO) {
            selected.inputStream().use { stream ->
                val bytes = stream.readNBytes(20 * 1024 * 1024 + 1)
                ensure(bytes.size <= 20 * 1024 * 1024, "Backup exceeds 20 MB")
                bytes.toString(Charsets.UTF_8)
            }
        }
    }
}
