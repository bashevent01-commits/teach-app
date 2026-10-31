package com.knowapp.android.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.knowapp.android.data.repository.ExchangeRepository

// Tells the person receiving stock that a branch has sent them some, once per transfer
object TransferAlerts {
    private const val CHANNEL = "stock_transfers"
    private const val PREFS = "know_transfer_alerts"

    suspend fun check(context: Context, exchange: ExchangeRepository) {
        val transfers = exchange.transfers().getOrNull() ?: return
        val incoming = transfers.filter { it.direction == "incoming" && it.status == "pending" }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val told = prefs.getStringSet("told", emptySet()) ?: emptySet()
        val fresh = incoming.filter { it.id.toString() !in told }
        if (fresh.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Stock sent to you", NotificationManager.IMPORTANCE_HIGH))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val shown = mutableSetOf<String>()
        fresh.forEach { t ->
            val amount = t.quantity.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: t.quantity
            val text = "$amount x ${t.itemName} from ${t.fromBranchName}. Open Stock to confirm or reject it."
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Stock sent to you")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(2000 + t.id, notification)
                shown += t.id.toString()
            } catch (_: SecurityException) { }
        }
        // Only transfers still waiting are kept, so the list never grows without end
        prefs.edit().putStringSet("told", (incoming.map { it.id.toString() }.toSet().intersect(told + shown))).apply()
    }
}
