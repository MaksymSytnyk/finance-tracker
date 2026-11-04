package com.maksym.financetracker

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.remember
import com.maksym.financetracker.data.iosRepository
import com.maksym.financetracker.domain.FinanceException
import com.maksym.financetracker.presentation.FileGateway
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Swift owns document presentation and security-scoped URLs. */
interface IosDocuments {
    fun save(name: String, content: String, completion: (Boolean, String?) -> Unit)
    fun load(completion: (String?, String?) -> Unit)
}

fun MainViewController(documents: IosDocuments) = ComposeUIViewController {
    val files = remember(documents) { object : FileGateway {
        override suspend fun save(name: String, content: String): Boolean = suspendCancellableCoroutine { continuation ->
            documents.save(name, content) { saved, error ->
                if (continuation.isActive) { if (error != null) continuation.resumeWithException(FinanceException(error)) else continuation.resume(saved) }
            }
        }
        override suspend fun readBackup(): String? = suspendCancellableCoroutine { continuation ->
            documents.load { content, error ->
                if (continuation.isActive) { if (error != null) continuation.resumeWithException(FinanceException(error)) else continuation.resume(content) }
            }
        }
    } }
    App({ iosRepository() }, files)
}
