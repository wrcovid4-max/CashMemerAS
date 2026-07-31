package com.cashmemer.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** A Bluetooth device the app can talk to. */
data class PairedDevice(
    val address: String,
    val name: String,
    val kind: DeviceKind,
    val connected: Boolean = false,
)

/**
 * What a paired device is for. Guessed from the Bluetooth class and name, and
 * overridable by the user — plenty of scanners report themselves as keyboards.
 */
enum class DeviceKind(val label: String) {
    BARCODE_SCANNER("Barcode scanner"),
    PAYMENT_TERMINAL("Payment terminal"),
    PRINTER("Receipt printer"),
    UNKNOWN("Other device"),
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

data class TerminalStatus(
    val bluetoothSupported: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val paired: List<PairedDevice> = emptyList(),
    val activeAddress: String? = null,
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val lastError: String? = null,
)

/**
 * Real Bluetooth plumbing for barcode scanners, payment terminals and receipt
 * printers over the Serial Port Profile — the protocol nearly all counter
 * hardware speaks.
 *
 * Scanned codes arrive on [scans]; the receipt form collects that and drops
 * each code straight into the current sale.
 */
object TerminalManager {

    /** Well-known SPP UUID. Effectively every serial BT device advertises it. */
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(TerminalStatus())
    val status: StateFlow<TerminalStatus> = _status.asStateFlow()

    /** Barcodes read from a connected scanner, one per emission. */
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val scans: SharedFlow<String> = _scans.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var readJob: Job? = null

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN,
            )
        }

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Re-reads adapter state and the bonded device list. */
    @SuppressLint("MissingPermission") // guarded by hasPermissions
    fun refresh(context: Context) {
        val adapter = adapter(context)
        val granted = hasPermissions(context)

        val paired = if (adapter != null && granted && adapter.isEnabled) {
            runCatching {
                adapter.bondedDevices.map { device ->
                    PairedDevice(
                        address = device.address,
                        name = device.name ?: device.address,
                        kind = device.guessKind(),
                        connected = device.address == _status.value.activeAddress &&
                            _status.value.state == ConnectionState.CONNECTED,
                    )
                }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        _status.update {
            it.copy(
                bluetoothSupported = adapter != null,
                bluetoothEnabled = adapter?.isEnabled == true,
                permissionsGranted = granted,
                paired = paired,
            )
        }

        IntegrationLog.add(
            "Refreshed: ${paired.size} paired device(s), " +
                "bluetooth ${if (adapter?.isEnabled == true) "on" else "off"}"
        )
    }

    /**
     * Opens an SPP socket and starts reading. Cancels any existing connection
     * first — one terminal at a time is all a single counter needs.
     */
    @SuppressLint("MissingPermission")
    fun connect(context: Context, address: String) {
        disconnect()

        val adapter = adapter(context)
        if (adapter == null || !adapter.isEnabled) {
            fail("Bluetooth is off")
            return
        }
        if (!hasPermissions(context)) {
            fail("Bluetooth permission not granted")
            return
        }

        _status.update {
            it.copy(
                activeAddress = address,
                state = ConnectionState.CONNECTING,
                lastError = null,
            )
        }
        IntegrationLog.add("Connecting to $address")

        readJob = scope.launch {
            try {
                val device = adapter.getRemoteDevice(address)
                // Discovery keeps the radio busy and makes connect() flaky.
                runCatching { adapter.cancelDiscovery() }

                val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = newSocket
                newSocket.connect()

                _status.update { it.copy(state = ConnectionState.CONNECTED) }
                IntegrationLog.add("Connected to ${device.name ?: address}")
                refresh(context)

                readLoop(newSocket)
            } catch (io: IOException) {
                fail(io.message ?: "Could not connect")
            } catch (error: Exception) {
                fail(error.message ?: "Connection error")
            }
        }
    }

    /**
     * Scanners send an ASCII payload terminated by CR and/or LF. Accumulate
     * until a terminator, then emit — splitting on every read would chop codes
     * that arrive in more than one packet.
     */
    private suspend fun readLoop(socket: BluetoothSocket) {
        val input = socket.inputStream
        val buffer = ByteArray(1024)
        val pending = StringBuilder()

        while (scope.isActive && socket.isConnected) {
            val read = try {
                input.read(buffer)
            } catch (io: IOException) {
                fail("Connection lost: ${io.message}")
                return
            }
            if (read <= 0) continue

            pending.append(String(buffer, 0, read, Charsets.US_ASCII))

            var newline = pending.indexOfFirst { it == '\n' || it == '\r' }
            while (newline >= 0) {
                val code = pending.substring(0, newline).trim()
                pending.delete(0, newline + 1)
                if (code.isNotEmpty()) {
                    IntegrationLog.add("Scanned $code")
                    _scans.emit(code)
                }
                newline = pending.indexOfFirst { it == '\n' || it == '\r' }
            }
        }
    }

    /** Sends raw bytes — ESC/POS for printers, protocol frames for terminals. */
    suspend fun send(bytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val active = socket ?: error("No device connected")
            active.outputStream.write(bytes)
            active.outputStream.flush()
            IntegrationLog.add("Sent ${bytes.size} byte(s)")
        }
    }

    fun disconnect() {
        readJob?.cancel()
        readJob = null
        runCatching { socket?.close() }
        socket = null
        _status.update {
            it.copy(state = ConnectionState.DISCONNECTED, activeAddress = null)
        }
    }

    private fun fail(message: String) {
        IntegrationLog.add("Error: $message")
        _status.update {
            it.copy(state = ConnectionState.FAILED, lastError = message)
        }
    }

    /** Human-readable check of everything that has to line up for a scan. */
    fun diagnostics(context: Context): List<Pair<String, Boolean>> {
        val adapter = adapter(context)
        val current = _status.value
        return listOf(
            "Device has Bluetooth" to (adapter != null),
            "Bluetooth is switched on" to (adapter?.isEnabled == true),
            "App has Bluetooth permission" to hasPermissions(context),
            "At least one device paired" to current.paired.isNotEmpty(),
            "A device is connected" to (current.state == ConnectionState.CONNECTED),
        )
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.guessKind(): DeviceKind {
        val lowered = (name ?: "").lowercase()
        return when {
            listOf("scan", "barcode", "qr").any { it in lowered } -> DeviceKind.BARCODE_SCANNER
            listOf("pos", "terminal", "pay", "card").any { it in lowered } ->
                DeviceKind.PAYMENT_TERMINAL
            listOf("print", "pos58", "pos80", "rp", "thermal").any { it in lowered } ->
                DeviceKind.PRINTER
            else -> DeviceKind.UNKNOWN
        }
    }
}
