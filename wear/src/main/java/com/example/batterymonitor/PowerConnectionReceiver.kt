package com.example.batterymonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_POWER_CONNECTED) {
            val serviceIntent = Intent(context, BatteryMonitoringService::class.java)
            context.startService(serviceIntent)
        }
    }
}