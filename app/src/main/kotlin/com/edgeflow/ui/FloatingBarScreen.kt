package com.edgeflow.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import com.edgeflow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.data.ConfigRepository
import com.edgeflow.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingBarScreen(repository: ConfigRepository) {
    val config by repository.config.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Floating shortcuts") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg),
        ) {
            item { FloatingAutomationBarCard(repository, config) }
        }
    }
}
