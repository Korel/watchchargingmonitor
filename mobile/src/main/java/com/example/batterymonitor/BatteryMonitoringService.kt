package com.example.batterymonitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class BatteryMonitoringService : Service() {
    private val channelId = "Silent Channel"
    private val notificationId = 2
    private var didWarn = false
    private var notifyValue = 80
    private val mainNotificationBuilder = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(R.drawable.battery)
        .setContentTitle("Watch Battery")
        .setOnlyAlertOnce(true)

    private val notifyValueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "NotifyValueUpdate") {
                // Update the notifyValue with the new value from the broadcast
                notifyValue = intent.getIntExtra("notifyValue", notifyValue)
                didWarn = false
            }
        }
    }

    private fun createNotificationChannel() {
        val name = "Watch Battery Monitoring"
        val descriptionText = ""
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun makeLoudNotification(notifyLevel: Int) {
        val channel = NotificationChannel(
            "Normal Channel",
            "Battery Over Wanted Value Notification",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, "Normal Channel")
            .setSmallIcon(R.drawable.lightning)
            .setContentTitle("Watch Battery is over $notifyLevel%!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notificationManager.notify(notificationId + 1, builder.build())
    }

    private fun makeNotification(batteryLevel: Int, isCharging: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (batteryLevel >= notifyValue && !didWarn) {
            didWarn = true
            makeLoudNotification(notifyValue)
        }
        mainNotificationBuilder.setContentText("Battery level: $batteryLevel%")
        mainNotificationBuilder.setOngoing(true)
        if (!isCharging) {
            mainNotificationBuilder.setContentText("Charging stopped")
            mainNotificationBuilder.setOngoing(false)
        }

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, mainNotificationBuilder.build())
        }
    }

    private fun initForegroundService() {
        val foregroundChannelId = "Foreground Service"
        val name = "Batterymonitor (Foreground Service)"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(foregroundChannelId, name, importance)
        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, foregroundChannelId)
            .setSmallIcon(R.drawable.cogs)
            .setContentTitle("Battery Monitor")
            .setContentText("Battery monitor foreground service is running")
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val dataPath = "/WatchChargingMonitor"
        val dataClient = Wearable.getDataClient(this)
        dataClient.addListener { dataEventBuffer ->
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item: DataItem = event.dataItem
                    if (item.uri.path == dataPath) {
                        val data: String? = DataMapItem.fromDataItem(item).dataMap.getString("data")
                        val msg = JSONObject(data!!)
                        // val timestamp = msg.getLong("timestamp")
                        val batteryLevel = msg.getInt("batteryLevel")
                        val isCharging = msg.getBoolean("isCharging")
                        makeNotification(batteryLevel, isCharging)
                    }
                }
            }
            dataEventBuffer.release()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        initForegroundService()
        registerReceiver(
            notifyValueReceiver,
            IntentFilter("NotifyValueUpdate"),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}