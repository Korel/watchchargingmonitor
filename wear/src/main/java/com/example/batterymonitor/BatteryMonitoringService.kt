package com.example.batterymonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class BatteryMonitoringService : Service() {
    class PowerConnectionReceiver(private val service: BatteryMonitoringService) :

        BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("WatchChargingMonitor", "Received ${intent.action}")
            val action = intent.action ?: return
            if (action == Intent.ACTION_POWER_CONNECTED) {
                val thread = Thread {
                    service.startMonitoring()
                }
                thread.start()
            }
        }
    }

    private val receiver = PowerConnectionReceiver(this)
    fun startMonitoring() {
        Log.i("WatchChargingMonitor", "Charge monitoring started")
        var isCharging = true
        while (isCharging) {
            Log.d("WatchChargingMonitor", "Sending...")
            isCharging = updateBatteryPercentage()
            Thread.sleep(3000)
        }
        Log.i("WatchChargingMonitor", "Charge monitoring finished")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initForegroundService()
        initReceiver()
        Log.i("WatchChargingMonitor", "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    private fun initReceiver() {
        val intentFilter = IntentFilter()
        intentFilter.addAction("android.intent.action.ACTION_POWER_CONNECTED")
        // intentFilter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED")
        registerReceiver(receiver, intentFilter)
    }

    private fun initForegroundService() {
        val foregroundChannelId = "Foreground Service"
        val name = "Batterymonitor (Foreground Service)"
        val descriptionText = ""
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(foregroundChannelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, foregroundChannelId)
            .setSmallIcon(R.drawable.cogs)
            .setContentTitle("Battery Monitor")
            .setContentText("Battery monitor foreground service is running")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        startForeground(1, notification)
    }

    private fun updateBatteryPercentage(): Boolean {
        val batteryLevelUnknown = 1000
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }
        val batteryLevel: Int = batteryStatus?.let {
            val level: Int =
                it.getIntExtra(BatteryManager.EXTRA_LEVEL, batteryLevelUnknown)
            val scale: Int =
                it.getIntExtra(BatteryManager.EXTRA_SCALE, batteryLevelUnknown)
            if (level != batteryLevelUnknown && scale != batteryLevelUnknown) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                batteryLevelUnknown
            }
        } ?: batteryLevelUnknown

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean =
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val timestamp = System.currentTimeMillis()
        val msg = JSONObject()
        msg.put("timestamp", timestamp)
        msg.put("batteryLevel", batteryLevel)
        msg.put("isCharging", isCharging)
        phoneCommunication(msg.toString())
        return isCharging
    }

    private fun phoneCommunication(data: String) {
        val dataClient = Wearable.getDataClient(this)
        val dataPath = "/WatchChargingMonitor"
        val dataMap = PutDataMapRequest.create(dataPath)
        dataMap.dataMap.putString("data", data)
        val request: PutDataRequest = dataMap.asPutDataRequest()
        val putDataTask = dataClient.putDataItem(request)
        putDataTask.addOnFailureListener { _ ->
            Log.w("WatchChargingMonitor", "Could not send data to phone")
        }
        putDataTask.addOnSuccessListener { _ ->
            Log.d(
                "WatchChargingMonitor",
                "Sent data: $data"
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
