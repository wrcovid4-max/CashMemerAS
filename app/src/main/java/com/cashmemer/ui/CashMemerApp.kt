package com.cashmemer.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import com.cashmemer.ui.ai.AskAiScreen
import com.cashmemer.ui.ai.AskAiFab
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.cashmemer.ui.receipts.BarcodeScanBus
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import com.cashmemer.ui.receipts.BulkScanScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
private const val ROUTE_BULK = "bulk"
private const val ROUTE_ASK_AI = "ask_ai"

@Composable
fun CashMemerApp(settings: AppSettings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Receipts.route

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Drive the toggle from the locale actually in effect, not from the saved
    // setting. Those two used to be read from different places and drifted
    // apart — the pill said Urdu while the screen stayed English. Reading the
    // applied locale here means the highlight can never disagree with the
    // content again.
    val appliedTag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val currentLanguage = com.wrcovid.kit.language.AppLanguage.fromTag(appliedTag).tag

    // Ask AI is shown on top of the app. Back on that screen returns to the app's home screen.
    var showAskAi by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAskAi) { showAskAi = false }

    if (showAskAi) {
        AskAiScreen(onBack = { showAskAi = false })
    } else Scaffold(
        topBar = {
            BrandHeader(
                language = currentLanguage,
                onLanguageChange = { tag ->
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
            onOpenAskAi = { showAskAi = true },
        )
    }
}

@Composable
private fun AppNavHost(
    navController: androidx.navigation.NavHostController,
    settings: AppSettings,
    contentPadding: PaddingValues,
    onOpenAskAi: () -> Unit,
) {
    val barcodePending by BarcodeScanBus.pending.collectAsState()
    LaunchedEffect(barcodePending) {
        if (barcodePending) navController.popBackStack(Destination.Receipts.route, false)
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        NavHost(
            navController = navController,
            startDestination = Destination.Receipts.route,
            // Tabs pop in instantly. snap() is a zero-duration transition — no
            // fade — but unlike None it lets the framework swap the screens
            // cleanly, so the outgoing tab doesn't linger on top for a frame.
            enterTransition = { fadeIn(animationSpec = snap()) },
            exitTransition = { fadeOut(animationSpec = snap()) },
            popEnterTransition = { fadeIn(animationSpec = snap()) },
            popExitTransition = { fadeOut(animationSpec = snap()) },
        ) {
            composable(Destination.Receipts.route) {
                ReceiptsHomeScreen(
                    settings = settings,
                    onOpenBulkScan = { navController.navigate(ROUTE_BULK) },
                )
            }
            composable(ROUTE_ASK_AI) { AskAiScreen(onBack = { navController.popBackStack() }) }
            composable(ROUTE_BULK) {
                BulkScanScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.popBackStack() },
                    onSaveAll = { navController.popBackStack() },
                )
            }
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
            if (currentRoute != ROUTE_ASK_AI && currentRoute != ROUTE_BULK) {
            AskAiFab(
                onClick = onOpenAskAi,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = if (currentRoute == Destination.Members.route) 104.dp else 16.dp,
                    ),
            )
        }
}
}
