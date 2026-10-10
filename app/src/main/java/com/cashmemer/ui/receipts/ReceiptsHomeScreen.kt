package com.cashmemer.ui.receipts

import androidx.compose.ui.unit.dp
import com.cashmemer.core.ui.theme.Holiday
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cashmemer.R
import com.cashmemer.core.data.AppSettings
import com.cashmemer.ui.dashboard.DashboardTab
import com.cashmemer.ui.history.HistoryTab
import com.cashmemer.ui.components.FitText

/** Receipts · History · Dashboard — the three tabs of the app's home screen. */
@Composable
fun ReceiptsHomeScreen(
    settings: AppSettings,
    onOpenBulkScan: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val titles = listOf(
        stringResource(R.string.tab_receipts),
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_dashboard),
    )

    // A tap on the live notification asks for the barcode scanner: show the form tab.
    val barcodePending by BarcodeScanBus.pending.collectAsState()
    LaunchedEffect(barcodePending) {
        if (barcodePending) selectedTab = 0
    }

    // Tapping Edit in History jumps back to the form with that receipt loaded.
    val editRequest by ReceiptEditBus.requestedId.collectAsState()
    LaunchedEffect(editRequest) {
        if (editRequest != null) selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    // "Dashboard" was wrapping to two lines and pushing the tab
                    // row out of shape; it now shrinks instead.
                    text = {
                        FitText(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> Box(modifier = Modifier.fillMaxSize()) {
                if (Holiday.HALLOWEEN) {
                    Image(
                    painter = painterResource(R.drawable.ic_ghost),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(340.dp),
                    alpha = 0.18f,
                )
                }
                NewReceiptTab(settings, onOpenBulkScan = onOpenBulkScan)
            }
            1 -> HistoryTab()
            else -> DashboardTab()
        }
    }
}
