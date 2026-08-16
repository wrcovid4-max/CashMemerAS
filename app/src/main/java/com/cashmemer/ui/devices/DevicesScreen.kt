package com.cashmemer.ui.devices

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.devices.ConnectionState
import com.cashmemer.devices.IntegrationLog
import com.cashmemer.devices.PairedDevice
import com.cashmemer.devices.TerminalManager
import com.cashmemer.ui.labelRes
import com.cashmemer.ui.components.SectionCard
import com.cashmemer.ui.components.SectionTitle
import kotlinx.coroutines.launch

class DevicesViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SettingsStore(application)

    val status = TerminalManager.status
    val logs = IntegrationLog.entries

    fun refresh() = TerminalManager.refresh(getApplication<Application>())

    fun connect(address: String) =
        TerminalManager.connect(getApplication<Application>(), address)

    fun disconnect() = TerminalManager.disconnect()

    fun setToggle(toggle: SettingsStore.DeviceToggle, value: Boolean) {
        viewModelScope.launch { store.setDeviceToggle(toggle, value) }
    }

    fun setDefaultDevice(address: String?) {
        viewModelScope.launch { store.setDefaultDevice(address) }
    }

    fun diagnostics(context: Context) = TerminalManager.diagnostics(context)

    fun clearLogs() = IntegrationLog.clear()
}

/**
 * Connected Devices & Integrations. Every switch here writes to storage and
 * every button does something — pairing, connecting and diagnostics all run
 * against the real Bluetooth adapter.
 */
@Composable
fun DevicesScreen(
    settings: AppSettings,
    viewModel: DevicesViewModel = viewModel(),
) {
    val status by viewModel.status.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }

    var showDiagnostics by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var confirmForgetAll by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) {
        if (!TerminalManager.hasPermissions(context)) {
            permissionLauncher.launch(TerminalManager.requiredPermissions())
        } else {
            viewModel.refresh()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionTitle(stringResource(R.string.connected_devices)) }

        item { StatusBanner(status.state, status.lastError, status.bluetoothEnabled) }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.core_integrations))

                DeviceToggleRow(
                    icon = Icons.Filled.PointOfSale,
                    title = stringResource(R.string.payment_terminal),
                    subtitle = stringResource(R.string.payment_terminal_body),
                    checked = with(store) { settings.isEnabled(SettingsStore.DeviceToggle.PAYMENT_TERMINAL) },
                    onCheckedChange = {
                        viewModel.setToggle(SettingsStore.DeviceToggle.PAYMENT_TERMINAL, it)
                    },
                )
                HorizontalDivider()
                DeviceToggleRow(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.ocr_companion),
                    subtitle = stringResource(R.string.ocr_companion_body),
                    checked = with(store) { settings.isEnabled(SettingsStore.DeviceToggle.OCR_COMPANION) },
                    onCheckedChange = {
                        viewModel.setToggle(SettingsStore.DeviceToggle.OCR_COMPANION, it)
                    },
                )
            }
        }

        item {
            SectionCard {
                SectionTitle(stringResource(R.string.connection_preferences))
                SettingsStore.DeviceToggle.entries
                    .filter {
                        it != SettingsStore.DeviceToggle.PAYMENT_TERMINAL &&
                            it != SettingsStore.DeviceToggle.OCR_COMPANION
                    }
                    .forEach { toggle ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(toggle.labelRes),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Switch(
                                checked = with(store) { settings.isEnabled(toggle) },
                                onCheckedChange = { viewModel.setToggle(toggle, it) },
                            )
                        }
                    }
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(stringResource(R.string.manage_paired_devices))
                    OutlinedButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }

                when {
                    !status.bluetoothSupported ->
                        Text(stringResource(R.string.no_bluetooth_radio))

                    !status.permissionsGranted -> {
                        Text(stringResource(R.string.bluetooth_permission_needed))
                        Button(
                            onClick = {
                                permissionLauncher.launch(TerminalManager.requiredPermissions())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_grant_permission)) }
                    }

                    !status.bluetoothEnabled ->
                        Text(stringResource(R.string.bluetooth_switch_on))

                    status.paired.isEmpty() ->
                        Text(
                            stringResource(R.string.nothing_paired),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                    else -> status.paired.forEach { device ->
                        DeviceRow(
                            device = device,
                            isDefault = device.address == settings.defaultDeviceAddress,
                            connecting = status.state == ConnectionState.CONNECTING &&
                                status.activeAddress == device.address,
                            onConnect = { viewModel.connect(device.address) },
                            onDisconnect = viewModel::disconnect,
                            onMakeDefault = { viewModel.setDefaultDevice(device.address) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                OutlinedButton(
                    onClick = { confirmForgetAll = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(stringResource(R.string.forget_all_devices))
                }
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null)
                    Text(stringResource(R.string.run_diagnostics))
                }
                OutlinedButton(
                    onClick = { showLogs = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                    Text(stringResource(R.string.view_logs, logs.size))
                }
            }
        }
    }

    if (showDiagnostics) {
        val results = remember { viewModel.diagnostics(context) }
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { (label, ok) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (ok) Icons.Filled.CheckCircle
                                else Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    status.lastError?.let {
                        Text(
                            stringResource(R.string.last_error, it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text(stringResource(R.string.integration_logs)) },
            text = {
                if (logs.isEmpty()) {
                    Text(stringResource(R.string.nothing_logged))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(logs.reversed()) { entry ->
                            Text(
                                text = entry.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogs = false }) { Text(stringResource(R.string.action_close)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearLogs) { Text(stringResource(R.string.action_clear)) }
            },
        )
    }

    if (confirmForgetAll) {
        AlertDialog(
            onDismissRequest = { confirmForgetAll = false },
            title = { Text(stringResource(R.string.forget_all_title)) },
            text = {
                Text(
                    stringResource(R.string.forget_all_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        viewModel.setDefaultDevice(null)
                        confirmForgetAll = false
                    }
                ) { Text(stringResource(R.string.forget)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatusBanner(
    state: ConnectionState,
    error: String?,
    bluetoothOn: Boolean,
) {
    val context = LocalContext.current
    val (text, colour) = when {
        state == ConnectionState.CONNECTED ->
            context.getString(R.string.device_connected) to MaterialTheme.colorScheme.primary
        state == ConnectionState.CONNECTING ->
            context.getString(R.string.device_connecting) to MaterialTheme.colorScheme.onSurfaceVariant
        state == ConnectionState.FAILED ->
            (error ?: context.getString(R.string.connection_failed)) to MaterialTheme.colorScheme.error
        !bluetoothOn -> context.getString(R.string.bluetooth_off) to MaterialTheme.colorScheme.error
        else -> context.getString(R.string.device_none) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    SectionCard(accent = state == ConnectionState.CONNECTED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == ConnectionState.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
            } else {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = colour,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Text(text = text, color = colour, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DeviceToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    isDefault: Boolean,
    connecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onMakeDefault: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${device.kind.label} · ${device.address}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isDefault) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.default_device)) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (device.connected) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_disconnect))
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !connecting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(if (connecting) R.string.device_connecting else R.string.action_connect)) }
            }
            if (!isDefault) {
                OutlinedButton(onClick = onMakeDefault, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.make_default))
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
