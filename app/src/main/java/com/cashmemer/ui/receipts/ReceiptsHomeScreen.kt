package com.cashmemer.ui.receipts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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

/** Receipts · History · Dashboard — the three tabs of the app's home screen. */
@Composable
fun ReceiptsHomeScreen(settings: AppSettings) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val titles = listOf(
        stringResource(R.string.tab_receipts),
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_dashboard),
    )

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
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
            }
        }

        when (selectedTab) {
            0 -> NewReceiptTab(settings)
            1 -> HistoryTab()
            else -> DashboardTab()
        }
    }
}
