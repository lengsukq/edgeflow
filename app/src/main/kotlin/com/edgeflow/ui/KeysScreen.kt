package com.edgeflow.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeflow.core.ActionCatalog
import com.edgeflow.core.ActionId
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.KeyGesture
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.setKeyEnabled
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.SwitchRow
import com.edgeflow.ui.navigation.BindingFamily
import com.edgeflow.ui.theme.Spacing

/**
 * Hardware key remapping.
 *
 * Three gestures per key — click, double click, long press — shown flat, because
 * seven keys by three is a small enough matrix to read at once. Each key gets its
 * own card so the enable switch scopes visibly to just that key.
 *
 * The double-click row carries an explanatory note: remapping double click means
 * the single click has to wait out the double-click window, which users notice
 * as a slight delay. Saying so up front prevents it being reported as a bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeysScreen(
    configRepository: ConfigRepository,
    onPickAction: (family: Int, primary: Int, secondary: Int) -> Unit,
) {
    val config by configRepository.config.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Hardware keys") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = Spacing.xxl,
            ),
        ) {
            items(HardwareKey.entries.toList()) { key ->
                val table = config.keys
                val hasDoubleClick = table.resolve(key, KeyGesture.DOUBLE_CLICK).id != ActionId.NONE

                SectionCard(title = key.label) {
                    SwitchRow(
                        title = "Enable this key",
                        checked = table.isKeyEnabled(key),
                        onCheckedChange = { enabled -> configRepository.setKeyEnabled(key, enabled) },
                    )
                    RowDivider()

                    KeyGesture.entries.forEach { gesture ->
                        val action = table.resolve(key, gesture)
                        SettingRow(
                            title = gesture.label,
                            subtitle = if (action.id == ActionId.NONE) {
                                "Not set"
                            } else {
                                ActionCatalog.describe(action)
                            },
                            enabled = table.isKeyEnabled(key),
                            onClick = {
                                onPickAction(BindingFamily.HARDWARE_KEY.id, key.id, gesture.index)
                            },
                        )
                        if (gesture == KeyGesture.CLICK && hasDoubleClick) {
                            Text(
                                text = "A single click waits for the double-click window " +
                                    "(${config.settings.keyDoubleClickTimeoutMillis} ms) before firing.",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = Spacing.lg,
                                    end = Spacing.lg,
                                    bottom = Spacing.sm,
                                ),
                            )
                        }
                    }
                }
                CardSpacer()
            }
        }
    }
}
