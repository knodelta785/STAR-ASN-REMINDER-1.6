package com.ongar.pengingatabsen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Dipanggil AlarmManager: membunyikan alarm dan/atau menampilkan notifikasi, lalu menjadwalkan ulang. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: Store.TYPE_IN
        if (intent.action == Scheduler.ACTION_ALARM && Store.shouldRing(ctx, type)) {
            try {
                AlarmService.start(ctx, type)
            } catch (e: Exception) {
                // layanan tidak boleh dijalankan saat ini; notifikasi tetap ditampilkan di bawah
            }
        }
        Scheduler.scheduleAll(ctx)
        Notifier.sync(ctx)
    }
}

/** Tombol pada notifikasi: "Sudah absen", "Tunda", "Matikan". */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        when (intent.action) {
            ACTION_MARK -> Attendance.mark(ctx, type)
            ACTION_SNOOZE -> {
                AlarmService.stop(ctx)
                Scheduler.snooze(ctx, type, 5)
            }
            ACTION_DISMISS -> AlarmService.stop(ctx)
        }
    }

    companion object {
        const val ACTION_MARK = "com.ongar.pengingatabsen.MARK"
        const val ACTION_SNOOZE = "com.ongar.pengingatabsen.SNOOZE"
        const val ACTION_DISMISS = "com.ongar.pengingatabsen.DISMISS"
    }
}

/** Setelah restart HP / ganti jam / update aplikasi: jadwal dipasang ulang dan penjaga latar belakang dipasang. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.scheduleAll(ctx)
        Notifier.sync(ctx)
    }
}
