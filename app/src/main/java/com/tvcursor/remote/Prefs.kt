package com.tvcursor.remote

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("tvcursor", Context.MODE_PRIVATE)

    /** Nama tombol evdev (mis. KEY_MENU) yang ditahan untuk menampilkan/menyembunyikan kursor. */
    fun hotkey(c: Context): String = sp(c).getString("hotkey", "") ?: ""
    fun setHotkey(c: Context, v: String) = sp(c).edit().putString("hotkey", v).apply()

    /** Kecepatan kursor 1..5 */
    fun speed(c: Context): Int = sp(c).getInt("speed", 3)
    fun setSpeed(c: Context, v: Int) = sp(c).edit().putInt("speed", v).apply()

    fun autoStart(c: Context): Boolean = sp(c).getBoolean("autostart", true)
    fun setAutoStart(c: Context, v: Boolean) = sp(c).edit().putBoolean("autostart", v).apply()
}
