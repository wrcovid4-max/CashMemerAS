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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashmemer.core.data.AppSettings
import com.cashmemer.core.data.SettingsStore
import com.cashmemer.devices.ConnectionState
import com.cashmemer.devices.IntegrationLog
import com.cashmemer.devices.PairedDevice
import com.cashmemer.devices.TerminalManager
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
        item { SectionTitle("Connected Devices & Integrations") }

        item { StatusBanner(status.state, status.lastError, status.bluetoothEnabled) }

        item {
            SectionCard {
                SectionTitle("Core Integrations")

                DeviceToggleRow(
                    icon = Icons.Filled.PointOfSale,
                    title = "Payment Terminal Integration",
                    subtitle = "Enable or disable smart payment card readers.",
                    checked = with(store) { settings.isEnabled(SettingsStore.DeviceToggle.PAYMENT_TERMINAL) },
                    onCheckedChange = {
                        viewModel.setToggle(SettingsStore.DeviceToggle.PAYMENT_TERMINAL, it)
                    },
                )
                HorizontalDivider()
                DeviceToggleRow(
                    icon = Icons.Filled.BugReport,
                    title = "Android OCR Companion",
                    subtitle = "Enable or disable Android OCR companion scanning.",
                    checked = with(store) { settings.isEnabled(SettingsStore.DeviceToggle.OCR_COMPANION) },
                    onCheckedChange = {
                        viewModel.setToggle(SettingsStore.DeviceToggle.OCR_COMPANION, it)
                    },
                )
            }
        }

        item {
            SectionCard {
                SectionTitle("Connection Preferences")
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
                                text = toggle.label,
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
                    SectionTitle("Manage Paired Devices")
                    OutlinedButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }

                when {
                    !status.bluetoothSupported ->
                        Text("This device has no Bluetooth radio.")

                    !status.permissionsGranted -> {
                        Text("Bluetooth permission is needed to see paired devices.")
                        Button(
                            onClick = {
                                permissionLauncher.launch(TerminalManager.requiredPermissions())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Grant permission") }
                    }

                    !status.bluetoothEnabled ->
                        Text("Switch Bluetooth on in system settings, then refresh.")

                    status.paired.isEmpty() ->
                        Text(
                            "Nothing paired yet. Pair your scanner, terminal or printer " +
                                "in Android's Bluetooth settings first — it will show up here.",
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
                    Text("  Forget All Paired Devices")
                }
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.HealthAndSafety, contentDescription = null)
                    Text("  Run Connection Diagnostics")
                }
                OutlinedButton(
                    onClick = { showLogs = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.BugReport, contentDescription = null)
                    Text("  View Integration Logs (${logs.size})")
                }
            }
        }
    }

    if (showDiagnostics) {
        val results = remember { viewModel.diagnostics(context) }
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("Connection Diagnostics") },
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
                            "Last error: $it",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) { Text("Close") }
            },
        )
    }

    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text("Integration Logs") },
            text = {
                if (logs.isEmpty()) {
                    Text("Nothing logged yet.")
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
                TextButton(onClick = { showLogs = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearLogs) { Text("Clear") }
            },
        )
    }

    if (confirmForgetAll) {
        AlertDialog(
            onDismissRequest = { confirmForgetAll = false },
            title = { Text("Forget all paired devices?") },
            text = {
                Text(
                    "Cash Memer will disconnect and clear its default device. " +
                        "Android keeps the Bluetooth pairings themselves — remove " +
                        "those in system Bluetooth settings if you want them gone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.disconnect()
                        viewModel.setDefaultDevice(null)
                        confirmForgetAll = false
                    }
                ) { Text("Forget") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetAll = false }) { Text("Cancel") }
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
    val (text, colour) = when {
        state == ConnectionState.CONNECTED ->
            "Device connected" to MaterialTheme.colorScheme.primary
        state == ConnectionState.CONNECTING ->
            "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
        state == ConnectionState.FAILED ->
            (error ?: "Connection failed") to MaterialTheme.colorScheme.error
        !bluetoothOn -> "Bluetooth is off" to MaterialTheme.colorScheme.error
        else -> "No device connected" to MaterialTheme.colorScheme.onSurfaceVariant
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
                AssistChip(onClick = {}, label = { Text("Default") })
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
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    enabled = !connecting,
                    modifier = Modifier.weight(1f),
                ) { Text(if (connecting) "Connecting…" else "Connect") }
            }
            if (!isDefault) {
                OutlinedButton(onClick = onMakeDefault, modifier = Modifier.weight(1f)) {
                    Text("Make default")
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}
