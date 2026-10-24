package com.edgeflow.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.edgeflow.ui.i18n.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.edgeflow.core.ActionId
import com.edgeflow.core.EdgeZone
import com.edgeflow.core.Gesture
import com.edgeflow.core.HardwareKey
import com.edgeflow.core.KeyGesture
import com.edgeflow.data.ConfigRepository
import com.edgeflow.data.Guidance
import com.edgeflow.data.ModuleStatus
import com.edgeflow.data.ModuleStatusProbe
import com.edgeflow.ui.components.CardSpacer
import com.edgeflow.ui.components.EmptyState
import com.edgeflow.ui.components.RowDivider
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.components.StatusPill
import com.edgeflow.ui.i18n.AppLanguage
import com.edgeflow.ui.i18n.UiTranslations
import androidx.compose.ui.platform.LocalContext
import com.edgeflow.ui.theme.Spacing

/**
 * Landing screen: is the module live, and what is configured?
 *
 * The status card is first because that is the one thing a user needs to know
 * before anything else matters — a beautifully configured gesture that never
 * fires is worse than no configuration. It states the problem and the fix rather
 * than only reporting a boolean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    configRepository: ConfigRepository,
    statusProbe: ModuleStatusProbe,
    onOpenGestures: () -> Unit,
    onOpenKeys: () -> Unit,
    onOpenStateTriggers: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(statusProbe, configRepository),
    ),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("EdgeFlow") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = UiTranslations.forContext(context, "Recheck status"))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
                StatusCard(status = status, onRecheck = viewModel::refresh)
                CardSpacer()
            }

            item {
                val boundGestures = config.gestures.bindings.values.sumOf { it.size }
                val boundKeys = config.keys.bindings.values.sumOf { it.size }
                val boundStates = config.stateTriggers.count { it.value.id != ActionId.NONE }
                SectionCard(title = "Configuration") {
                    SettingRow(
                        title = "Edge gestures",
                        subtitle = "$boundGestures of ${EdgeZone.entries.size * Gesture.entries.size} bindings set",
                        leadingIcon = Icons.Outlined.Gesture,
                        onClick = onOpenGestures,
                    )
                    RowDivider()
                    SettingRow(
                        title = "Hardware keys",
                        subtitle = "$boundKeys of ${HardwareKey.entries.size * KeyGesture.entries.size} bindings set",
                        leadingIcon = Icons.Outlined.Keyboard,
                        onClick = onOpenKeys,
                    )
                    RowDivider()
                    SettingRow(
                        title = "System triggers",
                        subtitle = "$boundStates of $StateTriggerCount triggers bound",
                        leadingIcon = Icons.Outlined.Layers,
                        onClick = onOpenStateTriggers,
                    )
                }
                CardSpacer()
            }

            item {
                SectionCard(
                    title = "How it works",
                    subtitle = "EdgeFlow runs inside the system server through LSPosed. " +
                        "Touches are matched against the screen edges before any app sees them, " +
                        "so a bound gesture is consumed and an unbound one passes straight through.",
                ) {
                    SettingRow(
                        title = "Android version",
                        subtitle = status?.let {
                            com.edgeflow.data.AndroidVersions.describe(it.androidSdk)
                        } ?: "Checking…",
                    )
                }
            }
        }
    }
}

/**
 * The activation status card.
 *
 * Three states, each with its own colour and its own instruction. A single
 * "not active" message would leave the user guessing whether the problem is the
 * framework, the scope, or a missing reboot.
 */
@Composable
private fun StatusCard(
    status: ModuleStatus?,
    onRecheck: () -> Unit,
) {
    if (status == null) {
        SectionCard(title = "Module status") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = "Checking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val ready = status.guidance == Guidance.READY
    val container = if (ready) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = if (ready) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    SectionCard(title = "Module status") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = when {
                    ready -> "System Hook active"
                    status.frameworkServiceConnected -> "Framework connected · hook pending"
                    else -> "Framework / hook unconfirmed"
                },
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = guidanceMessage(status),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status.rebootNeededForCurrentApk) {
            SettingRow(
                title = "Restart needed after APK update",
                subtitle = "This APK was installed after device startup. EdgeFlow does not hot reload system_server hooks, so the running hook may still be the previous version.",
            )
            RowDivider()
        }

        RowDivider()
        SettingRow(
            title = "Framework",
            subtitle = status.frameworkName?.let { name ->
                name + (status.frameworkVersion?.let { " " + it } ?: "")
            } ?: "Awaiting XposedService connection",
        )
        if (status.activeScope != null) {
            RowDivider()
            SettingRow(
                title = "Framework scope",
                subtitle = if ("system" in status.activeScope) "System Framework (system) selected"
                    else "system is not in the framework's granted scope",
            )
        }
        RowDivider()
        SettingRow(
            title = "API version",
            subtitle = status.apiVersion?.toString() ?: "Awaiting framework service",
        )
        RowDivider()
        SettingRow(
            title = "Android",
            subtitle = "${status.androidRelease} (SDK ${status.androidSdk})",
        )
        if (ready) {
            status.systemServerStage?.let { stage ->
                SettingRow(
                    title = "System startup stage",
                    subtitle = stage,
                )
            }
            RowDivider()
            SettingRow(
                title = "Gesture input runtime",
                subtitle = "Hook: ${if (status.inputHookInstalled) "ready" else "missing"} · " +
                    "Native: ${if (status.nativeInputBridgeBound) "ready" else "not connected"} · " +
                    "Filter: ${if (status.nativeInputGateRequested) "on" else "off"}",
            )
            SettingRow(
                title = "System gesture configuration",
                subtitle = "Schema ${status.inputConfigVersion} · ${status.inputEnabledZones}/12 zones · " +
                    "${status.inputBoundActions} executable bindings",
            )
            SettingRow(
                title = "Gesture diagnostics",
                subtitle = "${status.inputTouchCount} input events · " +
                    "${status.inputTriggeredCount} matched gestures · tap Recheck to refresh",
            )
            SettingRow(
                title = "Native action injection",
                subtitle = "${status.inputInjectionAttempts} attempts · " +
                    "${status.inputInjectionAccepted} accepted · " +
                    "${status.inputInjectionRejected} rejected" +
                    (if (status.lastInjectionResult >= 0) " · last code ${status.lastInjectionResult}" else "") +
                    ". Accepted only means Android received the event, not that the target acted.",
            )
            SettingRow(
                title = "Last automation: ${status.automationState}",
                subtitle = "${status.automationOrigin.ifBlank { "No run yet" }} · " +
                    "${status.automationSteps} dispatched · ${status.automationFailures} failed. " +
                    "Dispatch is not proof of an app-side effect.",
            )
            if (status.automationRecent.isNotBlank()) {
                SettingRow(
                    title = "Recent automation steps",
                    subtitle = status.automationRecent,
                )
            }
        }
    }
}

/** Turns a status into the concrete next step. */
private fun guidanceMessage(status: ModuleStatus): String = when (status.guidance) {
    Guidance.MODULE_PACKAGE_INVALID ->
        "Module APK is missing its modern Xposed entry or system scope declaration. Reinstall a complete build."
    Guidance.SYSTEM_HOOK_UNCONFIRMED ->
        "No live response from system_server yet. If LSPosed has granted System Framework (system), restart to load the updated module and recheck. A hidden framework cannot be judged by installed package names."
    Guidance.READY ->
        "The system_server module responded to a live check. Individual gesture hooks still require testing."
}

/**
 * Number of distinct system state triggers.
 *
 * Declared explicitly because the trigger ids have gaps; this count must match
 * the list on the system triggers screen.
 */
private const val StateTriggerCount = 38
