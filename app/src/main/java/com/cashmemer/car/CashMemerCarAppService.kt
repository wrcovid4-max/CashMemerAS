package com.cashmemer.car

import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.cashmemer.R
import com.cashmemer.core.data.CashMemerRepository
import com.cashmemer.core.model.Receipt
import com.cashmemer.core.util.Format
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Android Auto entry point. Driving-safe by design: read-only, short lists, no
 * text entry. Shows what the shop has taken today and the last few receipts.
 */
class CashMemerCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = CashMemerSession()
}

private class CashMemerSession : Session() {
    override fun onCreateScreen(intent: android.content.Intent): Screen =
        TodayScreen(carContext)
}

/** Today's takings, with a link through to the recent receipts list. */
private class TodayScreen(carContext: CarContext) : Screen(carContext) {

    private var total: Double = 0.0
    private var count: Int = 0
    private var currency: String = "PKR"
    private var loaded = false

    init {
        lifecycleScope.launch {
            val receipts = CashMemerRepository.get(carContext).allReceiptsOnce()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todays = receipts.filter {
                Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() == today
            }

            total = todays.sumOf { it.total }
            count = todays.size
            currency = receipts.firstOrNull()?.currencyCode ?: "PKR"
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder().apply {
            if (!loaded) {
                setLoading(true)
            } else {
                addRow(
                    Row.Builder()
                        .setTitle("Today's takings")
                        .addText(Format.amountWithCurrency(total, currency))
                        .build()
                )
                addRow(
                    Row.Builder()
                        .setTitle("Receipts today")
                        .addText(count.toString())
                        .build()
                )
                addAction(
                    Action.Builder()
                        .setTitle("Recent")
                        .setOnClickListener {
                            screenManager.push(RecentReceiptsScreen(carContext))
                        }
                        .build()
                )
            }
        }.build()

        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext,
                                        R.drawable.ic_launcher_foreground,
                                    )
                                ).build()
                            )
                            .setOnClickListener { invalidate() }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

/** The last few sales. Capped short — long lists are blocked while driving. */
private class RecentReceiptsScreen(carContext: CarContext) : Screen(carContext) {

    private var receipts: List<Receipt> = emptyList()
    private var loaded = false

    init {
        lifecycleScope.launch {
            receipts = CashMemerRepository.get(carContext).recentReceipts(MAX_ROWS)
            loaded = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = ItemList.Builder()

        if (loaded && receipts.isEmpty()) {
            builder.setNoItemsMessage("No receipts yet")
        } else {
            receipts.forEach { receipt ->
                builder.addItem(
                    Row.Builder()
                        .setTitle(receipt.placeName.ifBlank { "Untitled" })
                        .addText(
                            Format.amountWithCurrency(receipt.total, receipt.currencyCode)
                        )
                        .addText(Format.date(receipt.createdAt))
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("Recent receipts")
            .setHeaderAction(Action.BACK)
            .apply { if (!loaded) setLoading(true) else setSingleList(builder.build()) }
            .build()
    }

    private companion object {
        /** Android Auto refuses lists longer than this while in motion. */
        const val MAX_ROWS = 6
    }
}
