package com.riad.voyage.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.riad.voyage.MainActivity
import com.riad.voyage.R
import com.riad.voyage.data.AlertStore
import com.riad.voyage.data.PriceAlert
import com.riad.voyage.data.SettingsStore
import com.riad.voyage.data.TravelpayoutsApi
import com.riad.voyage.ui.formatPrice
import java.time.LocalDate
import java.time.YearMonth

data class PriceDrop(val alert: PriceAlert, val oldPrice: Int, val newPrice: Int)

object AlertChecker {
    private const val CHANNEL = "price_alerts"

    /** Vérifie chaque alerte, met à jour le dernier prix, et renvoie les baisses de prix. */
    suspend fun checkAll(context: Context): List<PriceDrop> {
        val settings = SettingsStore(context).load()
        if (settings.isDemo) return emptyList()
        val store = AlertStore(context)
        val api = TravelpayoutsApi(settings.token, settings.currency)
        val drops = mutableListOf<PriceDrop>()
        for (alert in store.all()) {
            if (isExpired(alert)) continue
            val flights = api.pricesForDates(
                alert.origin, alert.destination, alert.departure, alert.returnDate, alert.directOnly, limit = 5,
            )
            val min = flights.minOfOrNull { it.price } ?: continue
            val sameCurrency = alert.currency == settings.currency
            val old = alert.lastPrice
            if (old != null && sameCurrency && min < old) drops += PriceDrop(alert, old, min)
            store.upsert(alert.copy(lastPrice = min, currency = settings.currency, lastChecked = System.currentTimeMillis()))
        }
        return drops
    }

    fun isExpired(alert: PriceAlert): Boolean {
        val today = LocalDate.now()
        return runCatching {
            if (alert.departure.length == 7) YearMonth.parse(alert.departure).isBefore(YearMonth.from(today))
            else LocalDate.parse(alert.departure).isBefore(today)
        }.getOrDefault(false)
    }

    fun notify(context: Context, drops: List<PriceDrop>) {
        if (drops.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, com.riad.voyage.t("Alertes de prix"), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        drops.forEach { d ->
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(com.riad.voyage.tf("Prix en baisse : {0} → {1}", com.riad.voyage.t(d.alert.originName), com.riad.voyage.t(d.alert.destinationName)))
                .setContentText(
                    com.riad.voyage.tf("{0} au lieu de {1}", formatPrice(d.newPrice, d.alert.currency), formatPrice(d.oldPrice, d.alert.currency)),
                )
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(d.alert.id.hashCode(), n)
            } catch (_: SecurityException) {
            }
        }
    }
}
