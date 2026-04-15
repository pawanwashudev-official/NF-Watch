package com.neubofy.watch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SimStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SimStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        if (action == "android.intent.action.SIM_STATE_CHANGED") {
            val state = intent.getStringExtra("ss")
            Log.d(TAG, "SIM state changed to: $state")

            val prefs = context.getSharedPreferences("nf_watch_boot", Context.MODE_PRIVATE)
            val simProtectionEnabled = prefs.getBoolean("sim_protection_enabled", false)

            if (!simProtectionEnabled) {
                Log.d(TAG, "SIM protection is disabled")
                return
            }

            if (state == "ABSENT") {
                // Launch the foreground service which will wait 3 seconds and trigger find phone
                val serviceIntent = Intent(context, NFWatchService::class.java)
                serviceIntent.action = "SIM_REMOVED"
                context.startService(serviceIntent)
            } else if (state == "LOADED" || state == "READY") {
                val serviceIntent = Intent(context, NFWatchService::class.java)
                serviceIntent.action = "SIM_INSERTED"
                context.startService(serviceIntent)
            }
        }
    }
}
