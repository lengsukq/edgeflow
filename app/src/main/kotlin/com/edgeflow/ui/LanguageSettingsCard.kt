package com.edgeflow.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.edgeflow.R
import com.edgeflow.ui.components.SectionCard
import com.edgeflow.ui.components.SettingRow
import com.edgeflow.ui.i18n.AppLanguage
import com.edgeflow.ui.i18n.Text

@Composable
fun LanguageSettingsCard() {
    val context = LocalContext.current
    val choice = AppLanguage.selected(context)
    var showLanguagePicker by remember { mutableStateOf(false) }
    val selectedText = when (choice) {
        AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
        AppLanguage.ENGLISH -> stringResource(R.string.language_english)
        else -> stringResource(R.string.language_follow_system)
    }

    SectionCard(title = stringResource(R.string.language_settings_title)) {
        SettingRow(
            title = selectedText,
            subtitle = stringResource(R.string.language_settings_subtitle),
            onClick = { showLanguagePicker = true },
        )
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.language_picker_title)) },
            text = {
                Column {
                    listOf(
                        AppLanguage.SYSTEM to R.string.language_follow_system,
                        AppLanguage.CHINESE to R.string.language_chinese,
                        AppLanguage.ENGLISH to R.string.language_english,
                    ).forEach { (value, resId) ->
                        SettingRow(
                            title = (if (choice == value) "✓ " else "") + stringResource(resId),
                            onClick = {
                                showLanguagePicker = false
                                if (choice != value) {
                                    (context as? Activity)?.let { AppLanguage.update(it, value) }
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
