package com.ongar.pengingatabsen

import android.Manifest
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ongar.pengingatabsen.databinding.ActivityMainBinding
import com.ongar.pengingatabsen.databinding.ItemHistoryRowBinding
import com.ongar.pengingatabsen.databinding.ItemStatusCardBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var updatingDays = false

    private val idLocale: Locale = Locale.forLanguageTag("id-ID")
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", idLocale)
    private val dayShortFmt = SimpleDateFormat("EEE, d MMM", idLocale)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val hmFmt = SimpleDateFormat("HH:mm", Locale.US)

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 15_000)
        }
    }

    private val changedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            Notifier.sync(this)
        }

    private val dayButtons by lazy {
        listOf(
            b.btnMon to Calendar.MONDAY,
            b.btnTue to Calendar.TUESDAY,
            b.btnWed to Calendar.WEDNESDAY,
            b.btnThu to Calendar.THURSDAY,
            b.btnFri to Calendar.FRIDAY,
            b.btnSat to Calendar.SATURDAY,
            b.btnSun to Calendar.SUNDAY
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        Notifier.createChannels(this)
        setupMenu()
        setupDays()
        setupCards()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, changedReceiver, IntentFilter(Attendance.ACTION_DATA_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(changedReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        Scheduler.scheduleAll(this)
        Notifier.sync(this)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------------- Setup ----------------

    private fun setupMenu() {
        b.toolbar.inflateMenu(R.menu.main_menu)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_accounts ->
                    startActivity(Intent(this, AccountsActivity::class.java))
                R.id.action_alarm_in -> openAlarm(Store.TYPE_IN)
                R.id.action_alarm_out -> openAlarm(Store.TYPE_OUT)
                R.id.action_default_time -> showDefaultTime()
                R.id.action_background -> showBackgroundMode()
                R.id.action_reset -> showReset()
                R.id.action_permissions -> showPermissions()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun setupDays() {
        updatingDays = true
        dayButtons.forEach { (btn, dow) -> btn.isChecked = Store.isDayEnabled(this, dow) }
        updatingDays = false

        b.toggleDays.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingDays) return@addOnButtonCheckedListener
            val dow = dayButtons.firstOrNull { it.first.id == checkedId }?.second
                ?: return@addOnButtonCheckedListener
            Store.setDayEnabled(this, dow, isChecked)
            Scheduler.scheduleAll(this)
            Notifier.sync(this)
            refresh()
        }
    }

    private fun setupCards() {
        b.btnOpenStar.setOnClickListener {
            startActivity(Intent(this, StarAsnActivity::class.java))
        }
        b.cardIn.btnAction.setOnClickListener { onAction(Store.TYPE_IN) }
        b.cardOut.btnAction.setOnClickListener { onAction(Store.TYPE_OUT) }
        b.cardIn.btnEdit.setOnClickListener { editTime(Store.TYPE_IN) }
        b.cardOut.btnEdit.setOnClickListener { editTime(Store.TYPE_OUT) }
        b.cardIn.tvSchedule.setOnClickListener { pickDefaultTime(Store.TYPE_IN) }
        b.cardOut.tvSchedule.setOnClickListener { pickDefaultTime(Store.TYPE_OUT) }
        b.cardIn.tvAlarm.setOnClickListener { openAlarm(Store.TYPE_IN) }
        b.cardOut.tvAlarm.setOnClickListener { openAlarm(Store.TYPE_OUT) }
    }

    // ---------------- Aksi ----------------

    private fun onAction(type: String) {
        if (Store.isDone(this, type)) {
            toast(
                "Sudah tercatat pukul " + timeFmt.format(Date(Store.getTime(this, type))) +
                    ". Gunakan \"Ubah jam\" atau menu Reset jika perlu."
            )
            return
        }
        Attendance.mark(this, type)
        refresh()
        val label = if (type == Store.TYPE_IN) "masuk" else "pulang"
        toast("Absen $label tercatat pukul " + timeFmt.format(Date(Store.getTime(this, type))))
    }

    private fun editTime(type: String) {
        val cur = Calendar.getInstance()
        cur.timeInMillis = Store.getTime(this, type)
        TimePickerDialog(
            this,
            { _, h, m ->
                val c = Calendar.getInstance()
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                Attendance.mark(this, type, c.timeInMillis)
                refresh()
            },
            cur.get(Calendar.HOUR_OF_DAY), cur.get(Calendar.MINUTE), true
        ).show()
    }

    private fun openAlarm(type: String) {
        startActivity(Intent(this, AlarmSettingActivity::class.java).putExtra(EXTRA_TYPE, type))
    }

    private fun showDefaultTime() {
        val items = arrayOf(
            "Absen masuk • " + Store.scheduleLabel(this, Store.TYPE_IN),
            "Absen pulang • " + Store.scheduleLabel(this, Store.TYPE_OUT)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Jam Absen Default")
            .setMessage("Jam default hanya jadwal acuan. Waktu alarm diatur terpisah di menu Alarm.")
            .setItems(items) { _, which ->
                pickDefaultTime(if (which == 0) Store.TYPE_IN else Store.TYPE_OUT)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun pickDefaultTime(type: String) {
        TimePickerDialog(
            this,
            { _, h, m ->
                Store.setDefaultTime(this, type, h, m)
                Scheduler.scheduleAll(this)
                Notifier.sync(this)
                refresh()
            },
            Store.defaultHour(this, type), Store.defaultMinute(this, type), true
        ).show()
    }

    private fun showBackgroundMode() {
        val on = Store.backgroundEnabled(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("Mode Latar Belakang")
            .setMessage(
                "Status: " + (if (on) "AKTIF" else "NONAKTIF") + "\n\n" +
                    "Saat aktif, alarm dan pengingat tetap berjalan di belakang layar tanpa notifikasi tambahan, " +
                    "walau aplikasi ditutup, dan dipasang ulang otomatis setelah HP restart.\n\n" +
                    "Agar tidak dimatikan sistem, pilih \"Tanpa batasan\" di pengaturan baterai " +
                    "dan aktifkan Mulai otomatis (Autostart) bila HP Anda memilikinya."
            )
            .setPositiveButton(if (on) "Matikan" else "Aktifkan") { _, _ ->
                Store.setBackgroundEnabled(this, !on)
                Scheduler.scheduleAll(this)
                        toast(if (on) "Mode latar belakang dimatikan" else "Mode latar belakang aktif")
            }
            .setNeutralButton("Izin baterai") { _, _ -> requestBatteryExemption() }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun requestBatteryExemption() {
        val pkg = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
            }
        }
    }

    private fun showReset() {
        val items = arrayOf("Reset absen hari ini", "Reset semua data absen")
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Data Absen")
            .setItems(items) { _, which -> confirmReset(which == 1) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun confirmReset(all: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(if (all) "Hapus semua data absen?" else "Reset absen hari ini?")
            .setMessage(
                if (all) {
                    "Seluruh riwayat jam absen masuk dan pulang akan dihapus. " +
                        "Tindakan ini tidak dapat dibatalkan."
                } else {
                    "Absen masuk dan pulang hari ini kembali menjadi \"belum absen\", " +
                        "dan notifikasi serta alarm aktif lagi."
                }
            )
            .setPositiveButton("Reset") { _, _ ->
                Attendance.reset(this, all)
                refresh()
                toast(if (all) "Semua data absen dihapus" else "Absen hari ini direset")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showPermissions() {
        val items = arrayOf(
            "Izin notifikasi",
            "Alarm tepat waktu (Android 12)",
            "Optimasi baterai",
            "Notifikasi layar penuh (Android 14)"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("Izin & Baterai")
            .setMessage(
                "Agar alarm dan pengingat selalu muncul tepat waktu, pastikan izin berikut aktif " +
                    "dan aplikasi tidak dibatasi hemat baterai."
            )
            .setItems(items) { _, which ->
                val pkg = Uri.parse("package:$packageName")
                val intent = when (which) {
                    0 -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                    } else null
                    2 -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
                    } else null
                }
                if (intent == null) {
                    toast("Tidak diperlukan di versi Android ini")
                } else {
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                    }
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    // ---------------- Tampilan ----------------

    private fun refresh() {
        b.tvDate.text = dateFmt.format(Date())
        render(b.cardIn, Store.TYPE_IN)
        render(b.cardOut, Store.TYPE_OUT)
        renderTodayNote()
        renderHistory()
    }

    private fun render(c: ItemStatusCardBinding, type: String) {
        val isIn = type == Store.TYPE_IN
        val done = Store.isDone(this, type)
        val main = color(if (done) R.color.green else R.color.red)

        c.root.setCardBackgroundColor(color(if (done) R.color.green_bg else R.color.red_bg))
        c.root.strokeColor = main
        c.root.strokeWidth = dp(2)

        c.ivIcon.setImageResource(if (isIn) R.drawable.ic_login else R.drawable.ic_logout)
        c.ivIcon.imageTintList = ColorStateList.valueOf(main)
        c.tvTitle.text = if (isIn) "ABSEN MASUK" else "ABSEN PULANG"
        c.tvSchedule.text = "Jam default " + Store.scheduleLabel(this, type)

        c.tvPill.text = if (done) "SUDAH ABSEN" else "BELUM ABSEN"
        c.tvPill.backgroundTintList = ColorStateList.valueOf(main)

        c.tvTimeLabel.text = when {
            !done -> "Belum tercatat hari ini"
            isIn -> "Tercatat masuk pukul"
            else -> "Tercatat pulang pukul"
        }
        c.tvTime.text = if (done) timeFmt.format(Date(Store.getTime(this, type))) else "--:--:--"
        c.tvTime.setTextColor(main)

        c.btnAction.text = when {
            done -> "TERCATAT"
            isIn -> "SUDAH ABSEN MASUK"
            else -> "SUDAH ABSEN PULANG"
        }
        c.btnAction.backgroundTintList = ColorStateList.valueOf(main)
        c.btnAction.icon = if (done) ContextCompat.getDrawable(this, R.drawable.ic_check) else null
        c.btnEdit.visibility = if (done) View.VISIBLE else View.GONE

        val t = Store.activeSlotsLabel(this, type)
        c.tvAlarm.text = when {
            !Store.alarmEnabled(this, type) || t.isEmpty() -> "Alarm • NONAKTIF"
            done && Store.isTodayEnabled(this) -> "Alarm $t • OFF otomatis (sudah absen)"
            else -> "Alarm $t • AKTIF"
        }
    }

    private fun renderTodayNote() {
        b.tvTodayNote.text = if (Store.isTodayEnabled(this)) {
            "Hari ini termasuk hari pengingat."
        } else {
            "Hari ini bukan hari pengingat, notifikasi dan alarm tidak muncul."
        }
    }

    private fun renderHistory() {
        b.historyList.removeAllViews()
        val gray = color(R.color.text_secondary)
        val green = color(R.color.green)
        val cal = Calendar.getInstance()
        repeat(7) {
            val key = Store.dateKey(cal)
            val row = ItemHistoryRowBinding.inflate(layoutInflater, b.historyList, false)
            row.tvDay.text = dayShortFmt.format(cal.time)
            val tin = Store.getTime(this, Store.TYPE_IN, key)
            val tout = Store.getTime(this, Store.TYPE_OUT, key)
            row.tvIn.text = if (tin > 0) hmFmt.format(Date(tin)) else "--:--"
            row.tvIn.setTextColor(if (tin > 0) green else gray)
            row.tvOut.text = if (tout > 0) hmFmt.format(Date(tout)) else "--:--"
            row.tvOut.setTextColor(if (tout > 0) green else gray)
            b.historyList.addView(row.root)
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
