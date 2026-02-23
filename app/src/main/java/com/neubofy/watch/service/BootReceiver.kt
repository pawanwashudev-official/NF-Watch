package com.neubofy.watch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Only start service if a device was previously paired
            val prefs = context.getSharedPreferences("nf_watch_boot", Context.MODE_PRIVATE)
            val pairedAddress = prefs.getString("paired_address", null)
            if (pairedAddress != null) {
                Log.d("BootReceiver", "Boot completed, paired device found ($pairedAddress), starting NFWatchService")
                val serviceIntent = Intent(context, NFWatchService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("BootReceiver", "Boot completed, no paired device, skipping service start")
            }
        }
    }
}
