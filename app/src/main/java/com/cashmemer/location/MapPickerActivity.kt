package com.cashmemer.location

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cashmemer.R
import com.cashmemer.core.ui.theme.CashMemerTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What the picker hands back to the receipt form. */
data class PickedLocation(
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Map picker. The pin stays fixed at the centre of the screen and the map moves
 * underneath it — the standard pattern, and steadier than dragging a marker on
 * a phone held one-handed at a counter.
 */
class MapPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startLat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val startLng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)

        setContent {
            CashMemerTheme {
                MapPickerScreen(
                    start = if (startLat.isNaN() || startLng.isNaN()) null
                    else LatLng(startLat, startLng),
                    onConfirm = { picked ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_ADDRESS, picked.address)
                                .putExtra(EXTRA_LAT, picked.latitude)
                                .putExtra(EXTRA_LNG, picked.longitude),
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_LAT = "latitude"
        const val EXTRA_LNG = "longitude"

        /** Lahore — a sane opening view before any fix arrives. */
        val DEFAULT_CENTRE = LatLng(31.5204, 74.3587)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MapPickerScreen(
    start: LatLng?,
    onConfirm: (PickedLocation) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            start ?: MapPickerActivity.DEFAULT_CENTRE,
            if (start != null) 17f else 12f,
        )
    }

    var address by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    // Jump to the user's position the first time, unless we were given a point.
    LaunchedEffect(Unit) {
        if (start == null && LocationResolver.hasPermission(context)) {
            LocationResolver.currentLatLng(context).onSuccess { (lat, lng) ->
                cameraPositionState.position =
                    CameraPosition.fromLatLngZoom(LatLng(lat, lng), 17f)
            }
        }
    }

    // Reverse-geocode wherever the map settles, not on every frame.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.target }
            .debounce(400)
            .distinctUntilChanged()
            .collect { target ->
                resolving = true
                address = LocationResolver
                    .addressFor(context, target.latitude, target.longitude)
                    .getOrElse { "${target.latitude}, ${target.longitude}" }
                resolving = false
            }
    }

    // A missing key gives a blank beige rectangle with no explanation at all,
    // which reads as a broken app rather than a build that is missing a value.
    val hasMapsKey = remember { mapsApiKey(context).isNotBlank() }
    var showingMapHelp by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasMapsKey) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isMyLocationEnabled = LocationResolver.hasPermission(context),
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false),
            )

            // The pin is UI, not a map marker — it never moves.
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 24.dp),
            )
        } else {
            MissingKeyNotice(modifier = Modifier.align(Alignment.Center))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_address)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (query.isBlank()) return@IconButton
                        searching = true
                        scope.launch {
                            LocationResolver.searchPlace(context, query)
                                .onSuccess { (lat, lng) ->
                                    cameraPositionState.position =
                                        CameraPosition.fromLatLngZoom(LatLng(lat, lng), 17f)
                                }
                            searching = false
                        }
                    }
                ) {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.selected_address), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (resolving) stringResource(R.string.finding_address) else address,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Button(
                    onClick = {
                        val target = cameraPositionState.position.target
                        onConfirm(
                            PickedLocation(address, target.latitude, target.longitude)
                        )
                    },
                    enabled = !resolving && address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.confirm_location)) }

                // A key can be present and the tiles still not draw, because
                // the Cloud project has the wrong API turned on. Nothing in the
                // SDK says so, so there has to be a way to ask.
                if (hasMapsKey) {
                    TextButton(
                        onClick = { showingMapHelp = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.map_not_loading)) }
                }
            }
        }
    }

    if (showingMapHelp) {
        AlertDialog(
            onDismissRequest = { showingMapHelp = false },
            title = { Text(stringResource(R.string.maps_troubleshoot_title)) },
            text = { Text(stringResource(R.string.maps_troubleshoot_body)) },
            confirmButton = {
                TextButton(onClick = { showingMapHelp = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

/**
 * Explains a blank map instead of showing one.
 *
 * Searching and confirming still work from here — the address comes from the
 * device's own geocoder, which needs no key at all — so the screen stays usable
 * while the key is sorted out.
 */
@Composable
private fun MissingKeyNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.maps_key_missing_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.maps_key_missing_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Reads back the key Gradle injected, so the app can tell when it is absent. */
private fun mapsApiKey(context: Context): String = runCatching {
    context.packageManager
        .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        .metaData
        ?.getString("com.google.android.geo.API_KEY")
        .orEmpty()
        .trim()
}.getOrDefault("")

/** Opens the map picker, optionally centred on a point already chosen. */
class PickLocationContract : ActivityResultContract<Pair<Double, Double>?, PickedLocation?>() {

    override fun createIntent(context: Context, input: Pair<Double, Double>?): Intent =
        Intent(context, MapPickerActivity::class.java).apply {
            input?.let {
                putExtra(MapPickerActivity.EXTRA_LAT, it.first)
                putExtra(MapPickerActivity.EXTRA_LNG, it.second)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): PickedLocation? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return PickedLocation(
            address = intent.getStringExtra(MapPickerActivity.EXTRA_ADDRESS).orEmpty(),
            latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LAT, 0.0),
            longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LNG, 0.0),
        )
    }
}
