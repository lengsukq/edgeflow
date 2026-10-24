package com.edgeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edgeflow.BuildConfig
import com.edgeflow.data.AndroidVersions
import com.edgeflow.data.ModuleStatusProbe
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.theme.Spacing

/**
 * About and diagnostics.
 *
 * The compatibility row is the point of this screen: it states which Android
 * releases the module is verified against, so a user on an untested release can
 * tell whether a misbehaving gesture is a known limitation or a bug worth
 * reporting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(statusProbe: ModuleStatusProbe) {
    var status by remember { mutableStateOf(statusProbe.probe()) }
    LaunchedEffect(statusProbe) {
        delay(850)
        status = statusProbe.probe()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("About") }) },
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
            item {
                SectionCard(
                    title = "EdgeFlow",
                    subtitle = "Edge gestures, key remapping and system triggers, delivered " +
                        "through the system server.",
                ) {
                    SettingRow(title = "Version", subtitle = BuildConfig.VERSION_NAME)
                    RowDivider()
                    SettingRow(
                        title = "Target framework",
                        subtitle = "LSPosed with modern libxposed API 102",
                    )
                    RowDivider()
                    SettingRow(
                        title = "Verified on",
                        subtitle = "Target Android 9–17; not fully device-verified",
                    )
                }
                CardSpacer()
            }

            item {
                SectionCard(title = "This device") {
                    SettingRow(
                        title = "Android",
                        subtitle = "${status.androidRelease} (SDK ${status.androidSdk})",
                    )
                    RowDivider()
                    SettingRow(
                        title = "Framework",
                        subtitle = status.frameworkName ?: "No verified system_server response",
                    )
                    RowDivider()
                    SettingRow(
                        title = "Xposed API",
                        subtitle = status.apiVersion?.toString() ?: "Unknown",
                    )
                    RowDivider()
                    SettingRow(
                        title = "Compatibility",
                        subtitle = AndroidVersions.describe(status.androidSdk),
                    )
                }
                CardSpacer()
            }

            item {
                SectionCard(
                    title = "Why system scope",
                    subtitle = "Edge gestures have to see a touch before any application does. " +
                        "The only place that happens is the input pipeline inside the system " +
                        "server, so the module declares the system scope rather than hooking " +
                        "individual apps. That also means a reboot is required after changing " +
                        "the scope, because the system server has already started.",
                ) {
                    Spacer(Modifier.height(Spacing.sm))
                }
                CardSpacer()
            }

            item {
                SectionCard(
                    title = "Compatibility notes",
                    subtitle = "Android changes the classes this module hooks between releases. " +
                        "Where a hook target moved, the module detects the new shape at run time " +
                        "and falls back; where a capability was removed entirely, the related " +
                        "action is reported as unavailable rather than failing silently.",
                ) {
                    SettingRow(
                        title = "Key interception",
                        subtitle = "Signature differs on Android 16 and 17; resolved automatically",
                        leadingIcon = Icons.Outlined.Build,
                    )
                    RowDivider()
                    SettingRow(
                        title = "Input filter",
                        subtitle = "Unchanged from Android 9 through 17",
                        leadingIcon = Icons.Outlined.Android,
                    )
                    RowDivider()
                    SettingRow(
                        title = "Bar visibility",
                        subtitle = "Read from the inset source provider on Android 11+",
                        leadingIcon = Icons.Outlined.Extension,
                    )
                }
            }
        }
    }
}
