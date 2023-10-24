package com.example.batterymonitor

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

class BatteryMonitoringService : Service() {

    private val handler = Handler(Looper.myLooper()!!)
    private val runnable: Runnable = object : Runnable {
        override fun run() {
            val isCharging = updateBatteryPercentage()
            if (isCharging)
                handler.postDelayed(this, 3000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.postDelayed(runnable, 30000)
        return START_STICKY
    }

    private fun updateBatteryPercentage(): Boolean {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            this.registerReceiver(null, ifilter)
        }

        val batteryLevelUnknown = 1000
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
        // val chargePlug: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        // val usbCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
        // val acCharge: Boolean = chargePlug == BatteryManager.BATTERY_PLUGGED_AC
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
        val dataPath = "/battery"
        val dataMap = PutDataMapRequest.create(dataPath)
        dataMap.dataMap.putString("data", data)
        val request: PutDataRequest = dataMap.asPutDataRequest()
        dataClient.putDataItem(request)
        // val putDataTask: Task<*> = dataClient.putDataItem(request)
        // putDataTask
            // .addOnSuccessListener(OnSuccessListener<Any?> { _ -> println("[Wear] putDataTask Success")})
            // .addOnFailureListener(OnFailureListener { _ -> println("[Wear] putDataTask Fail")})
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
