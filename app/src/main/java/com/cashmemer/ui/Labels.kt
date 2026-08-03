package com.cashmemer.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cashmemer.R
import com.cashmemer.core.model.PaymentType
import com.cashmemer.core.model.ReceiptCategory

/**
 * Localised labels for the domain enums.
 *
 * The enums live in :core and carry an English `label` for logs and the PDF
 * fallback; the translated name has to come from the app's resources, so the
 * mapping lives here rather than on the enum itself.
 */

@get:StringRes
val PaymentType.labelRes: Int
    get() = when (this) {
        PaymentType.CASH -> R.string.pay_cash
        PaymentType.CARD -> R.string.pay_card
        PaymentType.BANK_TRANSFER -> R.string.pay_bank_transfer
        PaymentType.MOBILE_WALLET -> R.string.pay_mobile_wallet
        PaymentType.APPLE_PAY -> R.string.pay_apple_pay
        PaymentType.GOOGLE_WALLET -> R.string.pay_google_wallet
        PaymentType.GOOGLE_PAY -> R.string.pay_google_pay
        PaymentType.KLARNA -> R.string.pay_klarna
        PaymentType.PAY_PAK -> R.string.pay_paypak
    }

@get:StringRes
val ReceiptCategory.labelRes: Int
    get() = when (this) {
        ReceiptCategory.SHOPPING -> R.string.cat_shopping
        ReceiptCategory.GROCERIES -> R.string.cat_groceries
        ReceiptCategory.FOOD -> R.string.cat_food
        ReceiptCategory.FUEL -> R.string.cat_fuel
        ReceiptCategory.UTILITIES -> R.string.cat_utilities
        ReceiptCategory.SERVICES -> R.string.cat_services
        ReceiptCategory.MEDICAL -> R.string.cat_medical
        ReceiptCategory.OTHER -> R.string.cat_other
    }

@Composable
fun PaymentType.localized(): String = stringResource(labelRes)

@Composable
fun ReceiptCategory.localized(): String = stringResource(labelRes)

/** For non-Compose callers such as the PDF renderer. */
fun PaymentType.localized(context: Context): String = context.getString(labelRes)

fun ReceiptCategory.localized(context: Context): String = context.getString(labelRes)
