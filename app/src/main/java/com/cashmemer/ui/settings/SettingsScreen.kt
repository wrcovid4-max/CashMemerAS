package com.cashmemer.ui.settings

import android.app.Application
import android.content.Context
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.data.MassPrintOption
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.core.data.ThemeMode
import com.cashmemer.core.util.Format
import com.cashmemer.auth.GoogleAuth
import com.cashmemer.backup.BackupScheduler
import com.cashmemer.backup.BackupWriter
import com.cashmemer.sync.FirebaseSync
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)
    private val repository = CashMemerRepository.get(application)

    /** See ReceiptFormViewModel — no composable scope here either. */
    private fun str(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

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
            _message.value = str(R.string.msg_passcode_mismatch)
            return
        }
        launch {
            store.setPasscode(passcode)
            _message.value = str(R.string.msg_passcode_updated)
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
        _message.value = str(R.string.msg_backup_folder_set)
    }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** [activityContext] must be an Activity — the chooser needs a window. */
    fun signIn(activityContext: Context) = launch {
        GoogleAuth.signInAndStore(activityContext, store)
            .onSuccess { account ->
                // Trade the Google token for a Firebase session so Firestore
                // rules can scope data to this user. Harmless when Firebase
                // is not configured — sync just stays unavailable.
                val app = getApplication<Application>()
                if (FirebaseSync.isConfigured(app)) {
                    FirebaseSync.authenticate(app, account.idToken)
                        .onFailure { error ->
                            _message.value = str(
                                R.string.msg_sign_in_sync_failed,
                                error.message.orEmpty(),
                            )
                            return@onSuccess
                        }
                }
                _message.value = str(R.string.msg_signed_in_as, account.email)
            }
            .onFailure { error ->
                _message.value = when (error) {
                    is GoogleAuth.CancelledException -> null
                    is GoogleAuth.NotConfiguredException ->
                        str(R.string.msg_no_web_client_id)
                    else -> error.message ?: str(R.string.msg_sign_in_failed)
                }
            }
    }

    fun signOut(activityContext: Context) = launch {
        FirebaseSync.signOut(getApplication<Application>())
        GoogleAuth.signOut(activityContext, store)
        _message.value = str(R.string.msg_signed_out)
    }

    /** Uploads everything local to Firestore. */
    fun syncUp() = runSync { FirebaseSync.push(getApplication<Application>()) }

    /** Pulls the cloud copy down, replacing what is on this device. */
    fun syncDown() = runSync { FirebaseSync.pull(getApplication<Application>()) }

    private fun runSync(block: suspend () -> Result<Int>) = launch {
        if (_syncing.value) return@launch
        _syncing.value = true
        block()
            .onSuccess { _message.value = str(R.string.msg_synced_records, it) }
            .onFailure { error ->
                _message.value = when (error) {
                    is FirebaseSync.NotConfiguredException ->
                        str(R.string.msg_no_google_services)
                    is FirebaseSync.NotSignedInException -> str(R.string.msg_sign_in_first)
                    else -> error.message ?: str(R.string.msg_sync_failed)
                }
            }
        _syncing.value = false
    }

    fun setAutoBackup(enabled: Boolean) = launch {
        store.setAutoBackup(enabled)
        val app = getApplication<Application>()
        if (enabled) BackupScheduler.enable(app) else BackupScheduler.disable(app)
    }

    fun backupNow() = launch {
        BackupWriter.run(getApplication<Application>())
            .onSuccess { _message.value = str(R.string.msg_saved_file, it) }
            .onFailure { _message.value = it.message ?: str(R.string.msg_backup_failed) }
    }

    fun importJson(json: String) = launch {
        repository.importJson(json)
            .onSuccess { _message.value = str(R.string.msg_restored_records, it) }
            .onFailure { _message.value = it.message ?: str(R.string.msg_restore_failed) }
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
    onOpenDevices: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val message by viewModel.message.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val context = LocalContext.current
    val cloudReady = remember(context) { FirebaseSync.isConfigured(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AccountCard(
                settings = settings,
                cloudReady = cloudReady,
                syncing = syncing,
                onSignIn = { viewModel.signIn(context) },
                onSignOut = { viewModel.signOut(context) },
                onSyncUp = viewModel::syncUp,
                onSyncDown = viewModel::syncDown,
            )
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Devices, stringResource(R.string.connected_devices))
                Text(
                    stringResource(R.string.connected_devices_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onOpenDevices, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_open))
                }
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Palette, stringResource(R.string.appearance_settings))
                Text(
                    stringResource(R.string.appearance_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Settings, stringResource(R.string.general_settings))
                ToggleRow(stringResource(R.string.auto_send), settings.autoSend, viewModel::setAutoSend)
                ToggleRow(stringResource(R.string.save_signature), settings.saveSignature, viewModel::setSaveSignature)
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Print, stringResource(R.string.print_settings))
                ToggleRow(stringResource(R.string.auto_print), settings.autoPrint, viewModel::setAutoPrint)
                ToggleRow(stringResource(R.string.show_page_1), settings.showPage1, viewModel::setShowPage1)
                ToggleRow(stringResource(R.string.show_page_2), settings.showPage2, viewModel::setShowPage2)

                Text(stringResource(R.string.mass_print_option), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MassPrintOption.entries.forEach { option ->
                        FilterChip(
                            selected = settings.massPrint == option,
                            onClick = { viewModel.setMassPrint(option) },
                            label = {
                                Text(
                                    when (option) {
                                        MassPrintOption.PAGE_1 -> stringResource(R.string.page_1)
                                        MassPrintOption.PAGE_2 -> stringResource(R.string.page_2)
                                        MassPrintOption.BOTH -> stringResource(R.string.both)
                                    }
                                )
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.mass_print_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionCard {
                RowTitle(Icons.Filled.Lock, stringResource(R.string.app_lock))
                ToggleRow(stringResource(R.string.require_secure_lock), settings.appLock, viewModel::setAppLock)
                Text(
                    stringResource(R.string.app_lock_body),
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
        SectionTitle(stringResource(R.string.custom_passcode_lock))
        Text(
            stringResource(R.string.custom_passcode_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = passcode,
            onValueChange = { if (it.length <= 4) passcode = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.enter_new_passcode)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 4) confirm = it.filter(Char::isDigit) },
            label = { Text(stringResource(R.string.confirm_new_passcode)) },
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
            ) { Text(stringResource(R.string.action_cancel)) }
            Button(
                onClick = { onUpdate(passcode, confirm) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.action_update)) }
        }
    }
}

/**
 * Google account card. Sign-in establishes identity only — it is not a cloud
 * backup, so the copy here says so rather than implying the data is safe.
 */
@Composable
private fun AccountCard(
    settings: AppSettings,
    cloudReady: Boolean,
    syncing: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onSyncUp: () -> Unit,
    onSyncDown: () -> Unit,
) {
    SectionCard {
        RowTitle(Icons.Filled.AccountCircle, stringResource(R.string.cloud_sync_backup))

        if (settings.signedIn) {
            Text(
                text = settings.accountName ?: "Signed in",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = settings.accountEmail.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cloudReady) {
                Text(
                    text = stringResource(R.string.cloud_sync_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onSyncUp,
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                        Text(stringResource(R.string.sync))
                    }
                    OutlinedButton(
                        onClick = onSyncDown,
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Text(stringResource(R.string.restore))
                    }
                }
                Text(
                    text = stringResource(R.string.restore_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.cloud_sync_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(stringResource(R.string.sign_out))
            }
        } else {
            Text(
                text = stringResource(R.string.sign_in_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                Text(stringResource(R.string.sign_in_google))
            }
            if (!GoogleAuth.isConfigured) {
                Text(
                    text = "Needs GOOGLE_WEB_CLIENT_ID in local.properties — " +
                        "see the README.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
        RowTitle(Icons.Filled.Schedule, stringResource(R.string.automatic_backup))
        Text(
            stringResource(R.string.automatic_backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Text(
                text = if (settings.backupFolderUri == null) stringResource(R.string.choose_backup_folder)
                else stringResource(R.string.change_backup_folder),
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
            label = stringResource(R.string.daily_automatic_backup),
            checked = settings.autoBackup,
            onCheckedChange = viewModel::setAutoBackup,
        )

        Button(
            onClick = viewModel::backupNow,
            enabled = settings.backupFolderUri != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Text(stringResource(R.string.back_up_now))
        }

        Text(
            text = when (val backupError = settings.lastBackupError) {
                null -> if (settings.lastBackupAt > 0L) {
                    stringResource(R.string.last_backup, Format.timestamp(settings.lastBackupAt))
                } else {
                    stringResource(R.string.no_backup_yet)
                }
                else -> stringResource(R.string.last_backup_failed, backupError)
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
        RowTitle(Icons.Filled.CloudUpload, stringResource(R.string.backup_recovery))
        Text(
            stringResource(R.string.backup_recovery_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.exportJson { payload = it } },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text(stringResource(R.string.export_json))
            }
            OutlinedButton(
                onClick = { viewModel.importJson(payload) },
                enabled = payload.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text(stringResource(R.string.restore_json))
            }
        }

        OutlinedTextField(
            value = payload,
            onValueChange = { payload = it },
            label = { Text(stringResource(R.string.backup_payload)) },
            minLines = 4,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
