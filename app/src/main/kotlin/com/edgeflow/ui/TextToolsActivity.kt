package com.edgeflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgeflow.core.Ipc
import com.edgeflow.service.SecureClipboardHistory

/** Only the user can open this non-exported UI; it never polls clipboard in background. */
class TextToolsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.edgeflow.ui.i18n.AppLanguage.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(Ipc.EXTRA_OVERLAY_KIND)
        val snippets = intent.getStringArrayListExtra(EXTRA_SNIPPETS).orEmpty()
        val history = SecureClipboardHistory(this)
        val clipboard = getSystemService(ClipboardManager::class.java)
        setContent {
            MaterialTheme {
                TextToolsScreen(
                    universal = mode == Ipc.KIND_UNIVERSAL_COPY,
                    snippets = snippets,
                    store = history,
                    readCurrent = {
                        runCatching {
                            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(this)?.toString()
                        }.getOrNull()
                    },
                    copy = { item ->
                        clipboard?.setPrimaryClip(ClipData.newPlainText("EdgeFlow", item))
                    },
                    enableService = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    dismiss = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_SNIPPETS = "captured_texts"
    }
}

@Composable
private fun TextToolsScreen(
    universal: Boolean,
    snippets: List<String>,
    store: SecureClipboardHistory,
    readCurrent: () -> String?,
    copy: (String) -> Unit,
    enableService: () -> Unit,
    dismiss: () -> Unit,
) {
    var history by remember { mutableStateOf(store.entries()) }
    var message by remember { mutableStateOf("") }
    val entries = if (universal) snippets else history
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (universal) "Universal Copy" else "Clipboard history",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = dismiss) { Text("Close") }
        }
        Text(
            if (universal) "Text visible to Android Accessibility. Password fields are excluded. Select an entry to copy."
            else "History saves only when you tap Save clipboard while this screen is in focus. It is encrypted and remains on this device.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!universal) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val current = readCurrent()
                    if (current.isNullOrBlank()) {
                        message = "No readable clipboard text. Bring EdgeFlow to the foreground and try again."
                    } else {
                        runCatching { store.add(current) }.onSuccess {
                            history = store.entries()
                            message = "Saved locally"
                        }.onFailure { message = it.message ?: "Could not save clipboard text" }
                    }
                }) { Text("Save clipboard") }
                OutlinedButton(onClick = {
                    store.clear()
                    history = emptyList()
                    message = "History cleared"
                }) { Text("Clear") }
            }
        }
        if (universal && entries.isEmpty()) {
            Text("No readable text found, or the EdgeFlow Accessibility service is disabled.")
            OutlinedButton(onClick = enableService) { Text("Accessibility settings") }
        }
        if (!universal && entries.isEmpty()) Text("No saved clipboard entries")
        if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(entries, key = { index, _ -> index }) { _, entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(entry, maxLines = 8, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                runCatching { copy(entry) }.onSuccess {
                                    message = "Copied"
                                }.onFailure { message = it.message ?: "Copy failed" }
                            }) { Text("Copy") }
                            if (!universal) {
                                TextButton(onClick = {
                                    runCatching { store.remove(entry) }.onSuccess {
                                        history = store.entries()
                                    }.onFailure {
                                        message = it.message ?: "Could not remove entry"
                                    }
                                }) { Text("Remove") }
                            }
                        }
                    }
                }
            }
        }
    }
}
