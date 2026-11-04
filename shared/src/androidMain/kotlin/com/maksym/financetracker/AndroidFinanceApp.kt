package com.maksym.financetracker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.maksym.financetracker.data.androidRepository
import com.maksym.financetracker.domain.ensure
import com.maksym.financetracker.presentation.FileGateway
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@Composable fun AndroidFinanceApp() {
    val context = LocalContext.current.applicationContext
    var pending by remember { mutableStateOf<CancellableContinuation<Uri?>?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> pending?.takeIf { it.isActive }?.resume(uri); pending = null }
    val read = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending?.takeIf { it.isActive }?.resume(uri); pending = null }
    DisposableEffect(Unit) { onDispose { pending?.takeIf { it.isActive }?.resume(null); pending = null } }
    val files = remember(context) { object : FileGateway {
        override suspend fun save(name: String, content: String): Boolean {
            val uri = suspendCancellableCoroutine<Uri?> { continuation -> pending = continuation; save.launch(name) } ?: return false
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(content) } }
            return true
        }
        override suspend fun readBackup(): String? {
            val uri = suspendCancellableCoroutine<Uri?> { continuation -> pending = continuation; read.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) } ?: return null
            return withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        ensure(output.size() + count <= 20 * 1024 * 1024, "Backup exceeds 20 MB")
                        output.write(buffer, 0, count)
                    }
                    output.toString("UTF-8")
                }
            }
        }
    } }
    App({ androidRepository(context) }, files)
}
