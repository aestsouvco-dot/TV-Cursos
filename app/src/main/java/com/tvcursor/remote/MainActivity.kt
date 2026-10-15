package com.tvcursor.remote

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusTv: TextView
    private lateinit var btnHotkey: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnAuto: Button

    private val refresh = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 800)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun focusBg(): StateListDrawable {
        fun box(color: Int, stroke: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(3), stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), box(0xFF2E7DFF.toInt(), Color.WHITE))
            addState(intArrayOf(android.R.attr.state_pressed), box(0xFF1E5BC0.toInt(), Color.WHITE))
            addState(intArrayOf(), box(0xFF2A323C.toInt(), 0xFF2A323C.toInt()))
        }
    }

    private fun text(s: String, size: Float, bold: Boolean = false, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 19f
            setTextColor(Color.WHITE)
            background = focusBg()
            isFocusable = true
            setPadding(dp(24), dp(14), dp(24), dp(14))
            layoutParams = LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
            setOnClickListener { onClick() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Engine.init(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(56), dp(32), dp(56), dp(40))
        }
        val root = ScrollView(this).apply {
            setBackgroundColor(0xFF101418.toInt())
            isFillViewport = true
            addView(col)
        }

        col.addView(text("TV Cursor", 34f, bold = true))
        col.addView(text("Kursor mouse di layar TV, digerakkan dengan remote. Tanpa izin aksesibilitas.", 17f, color = 0xFFB0BAC5.toInt()))

        statusTv = text("", 17f, color = 0xFF8FD3FF.toInt())
        col.addView(statusTv)

        col.addView(button("1) Minta izin Shizuku dan hubungkan") {
            Engine.connect(true)
            toast("Jika muncul dialog izin Shizuku di TV, pilih Izinkan.")
        })

        btnHotkey = button("") { learnHotkey() }
        col.addView(btnHotkey)

        col.addView(button("3) Mulai layanan dan tampilkan kursor") {
            CursorService.start(this, CursorService.ACTION_SHOW)
        })

        col.addView(button("Hentikan layanan") {
            CursorService.start(this, CursorService.ACTION_STOP)
        })

        btnSpeed = button("") {
            Prefs.setSpeed(this, Prefs.speed(this) % 5 + 1)
            refreshButtons()
        }
        col.addView(btnSpeed)

        btnAuto = button("") {
            Prefs.setAutoStart(this, !Prefs.autoStart(this))
            refreshButtons()
        }
        col.addView(btnAuto)

        col.addView(text("Cara pakai", 22f, bold = true).apply { setPadding(0, dp(24), 0, dp(6)) })
        col.addView(text(HELP, 16f, color = 0xFFD5DCE4.toInt()))

        setContentView(root)
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        Engine.connect(false)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun learnHotkey() {
        CursorService.start(this, CursorService.ACTION_START)
        btnHotkey.text = "Tekan tombol remote sekarang (bukan panah/OK/Back)..."
        Engine.learn { name ->
            if (name != null) toast("Tombol aktivasi: $name") else toast("Waktu habis. Pastikan langkah 1 sudah terhubung.")
            refreshButtons()
        }
    }

    private fun refreshButtons() {
        val hot = Prefs.hotkey(this)
        btnHotkey.text = if (hot.isEmpty()) "2) Pilih tombol aktivasi" else "2) Tombol aktivasi: $hot (tekan untuk ganti)"
        btnSpeed.text = "Kecepatan kursor: ${Prefs.speed(this)} dari 5 (tekan untuk ganti)"
        btnAuto.text = "Mulai otomatis saat TV menyala: " + if (Prefs.autoStart(this)) "AKTIF" else "MATI"
    }

    private fun updateStatus() {
        val overlay = if (Settings.canDrawOverlays(this)) "OK"
        else "belum (otomatis diberikan setelah Shizuku terhubung)"
        val hot = Prefs.hotkey(this).ifEmpty { "belum dipilih" }
        val svc = CursorService.instance
        val svcText = when {
            svc == null -> "mati"
            svc.isActive() -> "berjalan, kursor TAMPIL"
            else -> "berjalan, kursor tersembunyi"
        }
        statusTv.text = "Shizuku: ${Engine.status}\n" +
            "Izin overlay: $overlay\n" +
            "Tombol aktivasi: $hot\n" +
            "Layanan: $svcText"
    }

    companion object {
        private val HELP = """
            AKTIVASI SEKALI (tanpa aksesibilitas, izinnya lewat Shizuku)
            - Pastikan Shizuku sudah berjalan di TV (mode root: buka aplikasi Shizuku, pilih Start).
            - Tekan tombol 1. Di dialog Shizuku pilih Izinkan untuk TV Cursor.
            - Status di atas akan berubah menjadi: Terhubung lewat Shizuku.

            KONTROL SAAT KURSOR TAMPIL
            - Panah: geser kursor (tahan = makin cepat).
            - OK: klik. Tahan OK: tekan lama.
            - Back: kembali. Tahan Back 0,7 detik: tutup kursor.
            - Dorong kursor ke tepi layar: scroll. CH+/CH- atau Page Up/Down juga scroll.
            - Tahan tombol aktivasi 0,7 detik: tampil / sembunyikan kursor dari aplikasi mana pun.
        """.trimIndent()
    }
}
