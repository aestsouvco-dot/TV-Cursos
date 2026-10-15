package com.tvcursor.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.autoStart(context)) return
        try {
            CursorService.start(context, CursorService.ACTION_START)
        } catch (_: Exception) {
        }
    }
}
