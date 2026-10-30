package com.ongar.pengingatabsen

import android.content.Context
import android.content.Intent

/** Satu pintu untuk mencatat / mereset absen agar alarm & notifikasi selalu ikut sinkron. */
object Attendance {
    const val ACTION_DATA_CHANGED = "com.ongar.pengingatabsen.DATA_CHANGED"

    fun mark(ctx: Context, type: String, at: Long = System.currentTimeMillis()) {
        val app = ctx.applicationContext
        Store.setTime(app, type, at)
        if (AlarmService.ringingType == type) AlarmService.stop(app)
        Scheduler.cancelSnooze(app, type)
        Scheduler.scheduleAll(app)   // alarm hari ini otomatis off
        Notifier.sync(app)           // notifikasi status bar hilang
        changed(app)
    }

    fun reset(ctx: Context, all: Boolean) {
        val app = ctx.applicationContext
        AlarmService.stop(app)
        if (all) Store.resetAll(app) else Store.resetToday(app)
        Scheduler.cancelSnooze(app, Store.TYPE_IN)
        Scheduler.cancelSnooze(app, Store.TYPE_OUT)
        Scheduler.scheduleAll(app)
        Notifier.sync(app)
        changed(app)
    }

    private fun changed(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_DATA_CHANGED).setPackage(ctx.packageName))
    }
}
