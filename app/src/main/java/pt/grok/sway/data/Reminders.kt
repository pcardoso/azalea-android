package pt.grok.sway.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.grok.sway.MainActivity
import pt.grok.sway.R
import java.util.Calendar

/** Daily local reminders for drinking water and doing the prescribed exercises. */
class ReminderSettings(private val context: Context) {
    data class State(
        val waterEnabled: Boolean = false,
        /** Minutes after midnight. */
        val waterStart: Int = 9 * 60,
        val waterEnd: Int = 21 * 60,
        val waterIntervalHours: Int = 2,
        val exerciseEnabled: Boolean = false,
        val exerciseTime: Int = 9 * 60,
    ) {
        /** Times of day (minutes after midnight) a water reminder fires. */
        val waterSlots: List<Int>
            get() = if (waterIntervalHours <= 0 || waterEnd < waterStart) listOf(waterStart)
            else (waterStart..waterEnd step waterIntervalHours * 60).toList()
    }

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    fun update(transform: (State) -> State) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putBoolean("waterEnabled", next.waterEnabled)
            .putInt("waterStart", next.waterStart)
            .putInt("waterEnd", next.waterEnd)
            .putInt("waterInterval", next.waterIntervalHours)
            .putBoolean("exerciseEnabled", next.exerciseEnabled)
            .putInt("exerciseTime", next.exerciseTime)
            .apply()
        reschedule()
    }

    /** Clears and re-registers every alarm from the current settings (also called after reboot). */
    fun reschedule() {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Cancel every slot that could have been scheduled before.
        for (minutes in 0 until 24 * 60 step 30) alarms.cancel(pendingIntent(KIND_WATER, minutes))
        alarms.cancel(pendingIntent(KIND_EXERCISE, 0))

        val s = _state.value
        if (s.waterEnabled) s.waterSlots.forEach { schedule(alarms, KIND_WATER, it) }
        if (s.exerciseEnabled) schedule(alarms, KIND_EXERCISE, s.exerciseTime, requestMinutes = 0)
    }

    private fun schedule(alarms: AlarmManager, kind: String, minutes: Int, requestMinutes: Int = minutes) {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact is fine for a reminder and needs no exact-alarm permission.
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY,
            pendingIntent(kind, requestMinutes),
        )
    }

    private fun pendingIntent(kind: String, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind)
        val requestCode = (if (kind == KIND_WATER) 10_000 else 20_000) + minutes
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun load() = State(
        waterEnabled = prefs.getBoolean("waterEnabled", false),
        waterStart = prefs.getInt("waterStart", 9 * 60),
        waterEnd = prefs.getInt("waterEnd", 21 * 60),
        waterIntervalHours = prefs.getInt("waterInterval", 2),
        exerciseEnabled = prefs.getBoolean("exerciseEnabled", false),
        exerciseTime = prefs.getInt("exerciseTime", 9 * 60),
    )

    companion object {
        const val EXTRA_KIND = "kind"
        const val KIND_WATER = "water"
        const val KIND_EXERCISE = "exercise"
        const val CHANNEL_ID = "reminders"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.reminders), NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val water = intent.getStringExtra(ReminderSettings.EXTRA_KIND) == ReminderSettings.KIND_WATER
        val title = context.getString(if (water) R.string.reminder_water_title else R.string.reminder_exercise_title)
        val body = context.getString(if (water) R.string.reminder_water_body else R.string.reminder_exercise_body)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ReminderSettings.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(if (water) 1 else 2, notification)
        } catch (e: SecurityException) {
            // Notifications permission was revoked; nothing to show.
        }
    }
}

/** Alarms don't survive a reboot, so register them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderSettings(context).reschedule()
        }
    }
}
