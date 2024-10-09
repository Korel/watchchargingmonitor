package dev.korel.watchchargingmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlin.random.Random

@RequiresApi(Build.VERSION_CODES.S)
fun vibrate(context: Context) {
    val vibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    val vibrator = vibratorManager.defaultVibrator
    val vibrationEffect = VibrationEffect.createWaveform(
        longArrayOf(500, 50, 500, 50, 1000, 200, 500, 50, 500, 50, 1000), intArrayOf(
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE,
            0,
            VibrationEffect.DEFAULT_AMPLITUDE
        ), -1
    )
    vibrator.vibrate(vibrationEffect)
}

fun phoneCommunication(dataMap: PutDataMapRequest, context: Context) {
    val dataClient = Wearable.getDataClient(context)
    val request: PutDataRequest = dataMap.asPutDataRequest().setUrgent()
    val putDataTask = dataClient.putDataItem(request)
    putDataTask.addOnFailureListener { _ ->
        Log.w("WatchChargingMonitor", "Could not send data to phone")
    }
    putDataTask.addOnSuccessListener { _ ->
        Log.d(
            "WatchChargingMonitor", "Sent data: ${dataMap.dataMap}"
        )
    }
}

fun createBatteryChargeInfoDataMap(
    dataPath: String, batteryLevel: Float, isCharging: Boolean
): PutDataMapRequest {
    val dataMap = PutDataMapRequest.create(dataPath)
    dataMap.dataMap.putFloat("batteryLevel", batteryLevel)
    dataMap.dataMap.putBoolean("isCharging", isCharging)
    dataMap.dataMap.putLong("timestamp", System.currentTimeMillis())
    return dataMap
}

fun makeLoudNotification(context: Context, notifyLevel: Int) {
    val channel = NotificationChannel(
        "Normal Channel", "Battery reached wanted value", NotificationManager.IMPORTANCE_DEFAULT
    )
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)

    val builder =
        NotificationCompat.Builder(context, "Normal Channel").setSmallIcon(R.drawable.lightning)
            .setContentTitle("Watch Battery reached $notifyLevel%!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    notificationManager.notify(Random.nextInt(), builder.build())
}

class BatteryMonitoringService : Service() {
    private val dataPath = "/WatchChargingMonitor"
    private var powerConnected = false
    private var batteryLevel = -1f
    private var didVibrate = false
    private var notifyValue = 80

    private val batteryReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context, intent: Intent) {
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            batteryLevel = level * 100 / scale.toFloat()
            val dataMap = createBatteryChargeInfoDataMap(
                dataPath, batteryLevel, status == BatteryManager.BATTERY_STATUS_CHARGING
            )
            phoneCommunication(dataMap, context)

            if (batteryLevel >= notifyValue && !didVibrate) {
                Log.d("WatchChargingMonitor", "Vibrating...")
                didVibrate = true
                vibrate(context)
                makeLoudNotification(context, notifyValue)
            }

        }

    }

    private val powerConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("WatchChargingMonitor", "Received ${intent.action}")
            val action = intent.action ?: return
            if (!powerConnected && action == Intent.ACTION_POWER_CONNECTED) {
                powerConnected = true
                registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } else if (powerConnected && action == Intent.ACTION_POWER_DISCONNECTED) {
                powerConnected = false
                Log.d("WatchChargingMonitor", "Power disconnected")
                unregisterReceiver(batteryReceiver)
                didVibrate = false
                val dataMap = createBatteryChargeInfoDataMap(dataPath, batteryLevel, false)
                phoneCommunication(dataMap, context)
            }
        }
    }

    private fun registerPowerConnectionReceiver() {
        registerReceiver(powerConnectionReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        registerReceiver(powerConnectionReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
    }


    private val notifyValueReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "NotifyValueUpdate") {
                // Update the notifyValue with the new value from the broadcast
                notifyValue = intent.getIntExtra("notifyValue", notifyValue)
                didVibrate = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initVibrateNotify() {
        registerReceiver(
            notifyValueReceiver, IntentFilter("NotifyValueUpdate"), RECEIVER_NOT_EXPORTED
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initForegroundService()
        registerPowerConnectionReceiver()
        initVibrateNotify()
        Log.i("WatchChargingMonitor", "Service started")
        return START_STICKY
    }

    private fun initForegroundService() {
        val foregroundChannelId = "Foreground Service"
        val name = "Foreground Service"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(foregroundChannelId, name, importance)
        // Register the channel with the system.
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification =
            NotificationCompat.Builder(this, foregroundChannelId).setSmallIcon(R.drawable.cogs)
                .setContentTitle("Watch Charging Monitor")
                .setContentText("Watch Charging Monitor foreground service is running")
                .setPriority(NotificationCompat.PRIORITY_LOW).build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerConnectionReceiver)
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(notifyValueReceiver)

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
