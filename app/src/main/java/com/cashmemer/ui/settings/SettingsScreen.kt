package com.cashmemer.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.MassPrintOption
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.data.ThemeMode
import com.cashmemer.core.util.Format
import com.cashmemer.backup.BackupScheduler
import com.cashmemer.backup.BackupWriter
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val repository = CashMemerRepository.get(application)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = launch { store.setThemeMode(mode) }
    fun setAutoSend(value: Boolean) = launch { store.setAutoSend(value) }
    fun setSaveSignature(value: Boolean) = launch { store.setSaveSignature(value) }
    fun setAutoPrint(value: Boolean) = launch { store.setAutoPrint(value) }
    fun setShowPage1(value: Boolean) = launch { store.setShowPage1(value) }
    fun setShowPage2(value: Boolean) = launch { store.setShowPage2(value) }
    fun setMassPrint(option: MassPrintOption) = launch { store.setMassPrint(option) }
    fun setAppLock(value: Boolean) = launch { store.setAppLock(value) }

    fun setPasscode(passcode: String, confirm: String) {
        if (passcode.length != 4 || passcode != confirm) {
            _message.value = "Passcodes must match and be 4 digits"
            return
        }
        launch {
            store.setPasscode(passcode)
            _message.value = "Passcode updated"
        }
    }

    /** Copies the whole database out as JSON — the offline backup path. */
    fun exportJson(onReady: (String) -> Unit) = launch {
        onReady(repository.exportJson())
    }

    /**
     * Stores the folder the user picked and takes a persistable grant so the
     * scheduled worker can still write to it after a reboot.
     */
    fun setBackupFolder(uri: Uri) = launch {
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        store.setBackupFolder(uri.toString())
        _message.value = "Backup folder set"
    }

    fun setAutoBackup(enabled: Boolean) = launch {
        store.setAutoBackup(enabled)
        val app = getApplication<Application>()
        if (enabled) BackupScheduler.enable(app) else BackupScheduler.disable(app)
    }

    fun backupNow() = launch {
        BackupWriter.run(getApplication<Application>())
            .onSuccess { _message.value = "Saved $it" }
            .onFailure { _message.value = it.message ?: "Backup failed" }
    }

    fun importJson(json: String) = launch {
        repository.importJson(json)
            .onSuccess { _message.value = "Restored $it record(s)" }
            .onFailure { _message.value = it.message ?: "Restore failed" }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: SettingsViewModel = viewModel(),
) {
    val message by viewModel.message.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard {
                RowTitle(Icons.Filled.Palette, "Appearance settings")
                Text(
                    "Customize theme look & feel representing your style",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Settings, "General Settings")
                ToggleRow("Auto-Send", settings.autoSend, viewModel::setAutoSend)
                ToggleRow("Save Signature", settings.saveSignature, viewModel::setSaveSignature)
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Print, "Print Settings")
                ToggleRow("Auto-Print", settings.autoPrint, viewModel::setAutoPrint)
                ToggleRow("Show Page 1 (Viewer)", settings.showPage1, viewModel::setShowPage1)
                ToggleRow("Show Page 2 (Viewer)", settings.showPage2, viewModel::setShowPage2)

                Text("Mass Print Option", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MassPrintOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.massPrint == option,
                            onClick = { viewModel.setMassPrint(option) },
                            label = {
                                Text(
                                    when (option) {
                                        MassPrintOption.PAGE_1 -> "Page 1"
                                        MassPrintOption.PAGE_2 -> "Page 2"
                                        MassPrintOption.BOTH -> "Both"
                                    }
                                )
                            },
                        )
                    }
                }
                Text(
                    "Automatically print, send email/SMS, and save signature upon generation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Lock, "App Lock")
                ToggleRow("Require secure lock", settings.appLock, viewModel::setAppLock)
                Text(
                    "Require secure lock (PIN, Password, Pattern, Fingerprint or Face Unlock) " +
                        "on startup and returning from background to access the app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { PasscodeCard(onUpdate = viewModel::setPasscode) }

        item { AutoBackupCard(settings = settings, viewModel = viewModel) }

        item { BackupCard(viewModel = viewModel) }

        message?.let { text ->
            item {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RowTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        SectionTitle(title, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PasscodeCard(onUpdate: (String, String) -> Unit) {
    var passcode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    SectionCard {
        SectionTitle("Custom Passcode Lock")
        Text(
            "Set up a custom 4-digit passcode as a secure fallback.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = passcode,
            onValueChange = { if (it.length <= 4) passcode = it.filter(Char::isDigit) },
            label = { Text("Enter New Passcode") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 4) confirm = it.filter(Char::isDigit) },
            label = { Text("Confirm New Passcode") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    passcode = ""
                    confirm = ""
                },
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }
            Button(
                onClick = { onUpdate(passcode, confirm) },
                modifier = Modifier.weight(1f),
            ) { Text("Update") }
        }
    }
}

/**
 * Scheduled backups. Point this at a Drive/Dropbox-synced folder and the
 * snapshots leave the phone on their own.
 */
@Composable
private fun AutoBackupCard(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setBackupFolder) }

    SectionCard {
        RowTitle(Icons.Filled.Schedule, "Automatic Backup")
        Text(
            "Writes a dated JSON snapshot every day into a folder you choose. " +
                "Pick a folder that syncs to the cloud and your records survive " +
                "losing this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Text(
                text = if (settings.backupFolderUri == null) "  Choose backup folder"
                else "  Change backup folder",
            )
        }

        settings.backupFolderUri?.let { uri ->
            Text(
                text = Uri.decode(uri.substringAfterLast(':')).ifBlank { uri },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ToggleRow(
            label = "Daily automatic backup",
            checked = settings.autoBackup,
            onCheckedChange = viewModel::setAutoBackup,
        )

        Button(
            onClick = viewModel::backupNow,
            enabled = settings.backupFolderUri != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text("  Back up now")
        }

        Text(
            text = when {
                settings.lastBackupError != null -> "Last backup failed: ${settings.lastBackupError}"
                settings.lastBackupAt > 0L -> "Last backup: ${Format.timestamp(settings.lastBackupAt)}"
                else -> "No backup taken yet"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (settings.lastBackupError != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupCard(viewModel: SettingsViewModel) {
    var payload by remember { mutableStateOf("") }

    SectionCard {
        RowTitle(Icons.Filled.CloudUpload, "Backup & Recovery (Offline Backup)")
        Text(
            "Export all locally stored receipt rows or restore previous records instantly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.exportJson { payload = it } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("  Export JSON")
            }
            OutlinedButton(
                onClick = { viewModel.importJson(payload) },
                enabled = payload.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text("  Restore JSON")
            }
        }

        OutlinedTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text("Backup payload") },
            minLines = 4,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
