package com.glowupdaily.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDateTime

object ReminderScheduler {
    private const val CHANNEL_ID = "glow_daily_reminders"

    fun schedule(context: Context, setting: ReminderSetting) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", setting.title)
            putExtra("id", setting.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            setting.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (!setting.enabled) {
            alarmManager.cancel(pending)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(setting.hour, setting.minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            if (setting.id == "review") AlarmManager.INTERVAL_DAY * 7 else AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    fun scheduleAll(context: Context, settings: List<ReminderSetting>) {
        settings.forEach { schedule(context, it) }
    }

    fun show(context: Context, title: String, id: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recordatorios Glow Up Daily", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            9000 + id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText("Tu rutina te espera. Avanza a tu ritmo.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(id.hashCode(), notification) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.show(
            context,
            intent.getStringExtra("title") ?: "Glow Up Daily",
            intent.getStringExtra("id") ?: "daily"
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val data = LocalStore(context).load()
            ReminderScheduler.scheduleAll(context, data.reminders)
        }
    }
}
