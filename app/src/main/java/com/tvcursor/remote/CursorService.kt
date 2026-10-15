package com.tvcursor.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * Layanan latar depan. Memegang jendela overlay kursor.
 *
 * Overlay dibuat FOKUS (tanpa FLAG_NOT_FOCUSABLE) sehingga semua tombol remote masuk ke kursor,
 * bukan ke aplikasi di bawahnya, dan TIDAK bisa disentuh (FLAG_NOT_TOUCHABLE) sehingga klik yang
 * disuntikkan lewat Shizuku menembus ke aplikasi di bawahnya.
 */
class CursorService : Service() {

    companion object {
        const val ACTION_START = "com.tvcursor.remote.START"
        const val ACTION_SHOW = "com.tvcursor.remote.SHOW"
        const val ACTION_STOP = "com.tvcursor.remote.STOP"
        private const val CHANNEL = "tvcursor"

        @Volatile
        var instance: CursorService? = null

        fun start(c: Context, action: String) {
            val i = Intent(c, CursorService::class.java).setAction(action)
            c.startForegroundService(i)
        }

        fun toggle(c: Context) {
            val s = instance
            if (s != null) {
                s.setActive(!s.isActive())
            } else {
                try { start(c, ACTION_SHOW) } catch (_: Exception) {}
            }
        }
    }

    private lateinit var wm: WindowManager
    private var view: CursorView? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Engine.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "TV Cursor", NotificationManager.IMPORTANCE_MIN)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("TV Cursor aktif")
            .setContentText("Tahan tombol aktivasi di remote untuk menampilkan kursor")
            .setContentIntent(pi)
            .build()
        startForeground(1, n)

        Engine.startMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> setActive(true)
            ACTION_STOP -> {
                setActive(false)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hide()
        Engine.stopMonitor()
        instance = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- tampil / sembunyi

    fun isActive(): Boolean = view != null

    fun setActive(on: Boolean) {
        if (on == isActive()) return
        if (on) show() else hide()
    }

    private fun show() {
        if (!Settings.canDrawOverlays(this)) {
            Engine.toast("Izin overlay belum aktif. Hubungkan Shizuku dulu (tombol 1), izin diberikan otomatis.")
            Engine.connect(true)
            return
        }
        val v = CursorView(this, this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        // Android 12+ memblokir sentuhan yang menembus overlay dengan opasitas > 0.8
        lp.alpha = 0.79f
        lp.title = "TVCursor"
        try {
            wm.addView(v, lp)
            view = v
            v.post { v.requestFocus() }
        } catch (e: Exception) {
            Engine.toast("Gagal menampilkan kursor: ${e.message}")
        }
    }

    private fun hide() {
        val v = view ?: return
        view = null
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------- aksi dari CursorView

    fun speedFactor(): Float = 0.5f + 0.25f * Prefs.speed(this)

    fun tap(x: Int, y: Int) {
        Engine.run("input tap $x $y")
    }

    fun longPress(x: Int, y: Int) {
        Engine.run("input swipe $x $y $x $y 700")
    }

    fun scroll(x: Int, y: Int, w: Int, h: Int, vertical: Boolean, positive: Boolean) {
        val cmd = if (vertical) {
            val a = (h * 0.68f).toInt()
            val b = (h * 0.32f).toInt()
            // positive = isi bergeser ke bawah (jari menggeser ke atas)
            if (positive) "input swipe $x $a $x $b 300" else "input swipe $x $b $x $a 300"
        } else {
            val a = (w * 0.68f).toInt()
            val b = (w * 0.32f).toInt()
            if (positive) "input swipe $a $y $b $y 300" else "input swipe $b $y $a $y 300"
        }
        Engine.run(cmd)
    }

    /**
     * Teruskan sebuah tombol (mis. BACK) ke aplikasi di bawah. Overlay dilepas dari fokus sebentar
     * supaya tombol yang disuntikkan sampai ke aplikasi, bukan kembali ke kursor.
     */
    fun passKey(code: Int) {
        setWindowFocusable(false)
        Engine.run("input keyevent $code") {
            main.postDelayed({ setWindowFocusable(true) }, 80)
        }
    }

    private fun setWindowFocusable(focusable: Boolean) {
        val v = view ?: return
        val lp = v.layoutParams as? WindowManager.LayoutParams ?: return
        lp.flags = if (focusable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            wm.updateViewLayout(v, lp)
            if (focusable) v.requestFocus()
        } catch (_: Exception) {
        }
    }
}
