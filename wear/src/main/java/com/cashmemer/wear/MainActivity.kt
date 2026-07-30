package com.cashmemer.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.cashmemer.core.util.Format
import com.cashmemer.core.wear.WearSummary
import com.google.android.gms.wearable.Wearable

/**
 * Wear OS companion. Three glanceable cards, matching the published feature set:
 * today's takings + sync status, live rates, and store weather.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
    }

    override fun onResume() {
        super.onResume()
        requestRefreshFromPhone()
    }

    /** Nudges the phone to push a fresh summary whenever the watch is opened. */
    private fun requestRefreshFromPhone() {
        val messageClient = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        com.cashmemer.core.wear.WearSync.PATH_REQUEST_REFRESH,
                        ByteArray(0),
                    )
                }
            }
    }
}

@Composable
private fun WearApp() {
    val context = LocalContext.current
    val summary by WearSummaryStore.observe(context)
        .collectAsState(initial = WearSummary())

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { TodayCard(summary) }
            item { SyncCard(summary) }

            if (summary.rates.isNotEmpty()) {
                item {
                    Text(
                        text = "Live rates",
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(summary.rates) { rate ->
                    RateRow(
                        code = rate.code,
                        flag = rate.flagEmoji,
                        value = Format.rate(rate.rate),
                    )
                }
            }

            summary.weather?.let { weather ->
                item {
                    WeatherCard(
                        place = weather.place,
                        temperature = "${weather.temperatureC.toInt()}°C",
                        condition = weather.condition,
                    )
                }
            }

            if (summary.generatedAt == 0L) {
                item {
                    Text(
                        text = "Open Cash Memer on your phone to sync.",
                        style = MaterialTheme.typography.caption1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayCard(summary: WearSummary) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Today", style = MaterialTheme.typography.caption1)
            Text(
                text = Format.amountWithCurrency(summary.todayTotal, summary.currencyCode),
                style = MaterialTheme.typography.title2,
            )
            Text(
                text = "${summary.todayCount} receipt(s)",
                style = MaterialTheme.typography.caption2,
            )
        }
    }
}

@Composable
private fun SyncCard(summary: WearSummary) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Sync status", style = MaterialTheme.typography.caption1)
            Text(
                text = if (summary.synced) "All receipts backed up"
                else "${summary.pendingCount} pending",
                style = MaterialTheme.typography.body1,
            )
            if (summary.lastSyncedAt > 0) {
                Text(
                    text = Format.timestamp(summary.lastSyncedAt),
                    style = MaterialTheme.typography.caption2,
                )
            }
        }
    }
}

@Composable
private fun RateRow(code: String, flag: String, value: String) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "$flag $code", modifier = Modifier.weight(1f))
            Text(text = value, style = MaterialTheme.typography.body1)
        }
    }
}

@Composable
private fun WeatherCard(place: String, temperature: String, condition: String) {
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("Store weather", style = MaterialTheme.typography.caption1)
            Text(temperature, style = MaterialTheme.typography.title2)
            Text("$condition · $place", style = MaterialTheme.typography.caption2)
        }
    }
}
