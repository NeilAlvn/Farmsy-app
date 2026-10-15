package app.farmsy.android.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.farmsy.android.R

/// One local notification two days before a free trial turns into a charge —
/// the Android twin of iOS `TrialReminder`. No toggle (owner, 2026-10-15):
/// everyone who starts a trial gets it. The paywall's "nothing to pay today" is
/// only honest if we say when that changes.
///
/// An inexact alarm: off by minutes, never days, and it needs no
/// SCHEDULE_EXACT_ALARM permission. Alarms die on reboot, so the fire time is
/// kept in prefs and re-armed on every app start.
/// ponytail: no BOOT_COMPLETED receiver — a phone rebooted and then not opened
/// for the rest of the trial misses the reminder; add one if that shows up.
object TrialReminder {
    private const val PREFS = "farmsy"
    private const val KEY_AT = "trialReminderAt"
    private const val KEY_PRICE = "trialReminderPrice"
    private const val DAY_MS = 86_400_000L

    fun schedule(ctx: Context, trialEndsMillis: Long, price: String) {
        val at = trialEndsMillis - 2 * DAY_MS
        if (at <= System.currentTimeMillis()) return   // test-track trials last minutes
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_AT, at).putString(KEY_PRICE, price).apply()
        arm(ctx, at)
    }

    /// From `FarmsyApp.onCreate`: put back an alarm a reboot cleared.
    fun rearm(ctx: Context) {
        val at = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0L)
        if (at > System.currentTimeMillis()) arm(ctx, at)
    }

    private fun arm(ctx: Context, at: Long) {
        ctx.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC_WAKEUP, at, pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, TrialReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun fire(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val price = prefs.getString(KEY_PRICE, null) ?: return
        prefs.edit().remove(KEY_AT).remove(KEY_PRICE).apply()
        PushRegistrar.show(
            ctx,
            ctx.getString(R.string.trial_reminder_title),
            ctx.getString(R.string.trial_reminder_body_arg, price),
            null,
        )
    }
}

/// Declared in the manifest; the alarm above wakes it.
class TrialReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TrialReminder.fire(context)
}
