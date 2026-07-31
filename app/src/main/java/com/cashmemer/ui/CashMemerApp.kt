package com.cashmemer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import com.cashmemer.CashMemerApplication
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cashmemer.core.data.AppSettings
import com.cashmemer.ui.components.BrandHeader
import com.cashmemer.ui.components.CashMemerBottomBar
import com.cashmemer.ui.devices.DevicesScreen
import com.cashmemer.ui.inventory.InventoryScreen
import com.cashmemer.ui.members.MembersScreen
import com.cashmemer.ui.pricelist.PriceListScreen
import com.cashmemer.ui.rates.RatesScreen
import com.cashmemer.ui.receipts.ReceiptsHomeScreen
import com.cashmemer.ui.settings.SettingsScreen

/** Sub-screen of More, so it stays off the bottom bar. */
private const val ROUTE_DEVICES = "devices"

@Composable
fun CashMemerApp(settings: AppSettings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Receipts.route

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            BrandHeader(
                language = settings.language,
                onLanguageChange = { tag ->
                    // Per-app language: AppCompat recreates the activity for us.
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tag)
                    )
                    val app = context.applicationContext as CashMemerApplication
                    scope.launch { app.settingsStore.setLanguage(tag) }
                },
            )
        },
        bottomBar = {
            CashMemerBottomBar(
                currentRoute = currentRoute,
                onSelect = { destination ->
                    navController.navigate(destination.route) {
                        // Keep a single copy of each tab on the back stack.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            settings = settings,
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    settings: AppSettings,
    contentPadding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        NavHost(
            navController = navController,
            startDestination = Destination.Receipts.route,
        ) {
            composable(Destination.Receipts.route) { ReceiptsHomeScreen(settings) }
            composable(Destination.Inventory.route) { InventoryScreen() }
            composable(Destination.PriceList.route) { PriceListScreen() }
            composable(Destination.Rates.route) { RatesScreen() }
            composable(Destination.Members.route) { MembersScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    settings = settings,
                    onOpenDevices = { navController.navigate(ROUTE_DEVICES) },
                )
            }
            composable(ROUTE_DEVICES) { DevicesScreen(settings) }
        }
    }
}
