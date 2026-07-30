package com.cashmemer.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.ui.graphics.vector.ImageVector
import com.cashmemer.R

/**
 * The bottom bar, left to right — mirrors the original app's rail:
 * new receipt · inventory · price list · rates · members · more.
 * History and Dashboard live as tabs inside [Receipts].
 */
enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Receipts("receipts", R.string.nav_receipts, Icons.Filled.PostAdd),
    Inventory("inventory", R.string.nav_inventory, Icons.Filled.Inventory2),
    PriceList("price_list", R.string.nav_price_list, Icons.AutoMirrored.Filled.List),
    Rates("rates", R.string.nav_rates, Icons.Filled.CurrencyExchange),
    Members("members", R.string.nav_members, Icons.Filled.People),
    Settings("settings", R.string.nav_more, Icons.Filled.MoreHoriz),
}
