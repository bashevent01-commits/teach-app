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
import com.knowapp.android.AppContainer
import java.time.ZoneId
import java.time.ZonedDateTime

// Tells a gym owner each morning from 8am who pays today and who pays tomorrow
object MemberDueAlerts {
    private const val CHANNEL = "members_due"
    private const val PREFS = "know_member_due"

    suspend fun check(context: Context, container: AppContainer) {
        val session = container.sessionStore.session.value ?: return
        if (session.role != "institution_admin" && !(session.role == "staff" && session.individual)) return
        if (session.institutionType == null) container.apiService.getMe().body()?.let { container.sessionStore.setInstitutionType(it.institutionType) }
        if (container.sessionStore.session.value?.institutionType != "gym") return
        val now = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
        if (now.hour < 8) return
        val day = now.toLocalDate().toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("notified_${session.userId}", "") == day) return
        val due = container.membersRepository.due().getOrNull() ?: return
        if (due.today.isEmpty() && due.tomorrow.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Members due", NotificationManager.IMPORTANCE_DEFAULT))
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        fun names(list: List<com.knowapp.android.data.model.MemberOut>) = list.take(3).joinToString(", ") { it.name } + if (list.size > 3) " and ${list.size - 3} more" else ""
        val text = listOfNotNull(
            if (due.today.isNotEmpty()) "Due today: ${names(due.today)}" else null,
            if (due.tomorrow.isNotEmpty()) "Due tomorrow: ${names(due.tomorrow)}" else null,
        ).joinToString(". ")
        val total = due.today.size + due.tomorrow.size
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(if (total == 1) "1 member to chase" else "$total members to chase")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(4201, notification)
        prefs.edit().putString("notified_${session.userId}", day).apply()
    }
}
