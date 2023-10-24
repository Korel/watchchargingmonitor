package com.example.batterymonitor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.NumberPicker

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val serviceIntent = Intent(this, BatteryMonitoringService::class.java)
        serviceIntent.putExtra("notifyValue", 80)
        this.startService(serviceIntent)
        val picker = findViewById<NumberPicker>(R.id.numberpicker)
        picker.maxValue = 99
        picker.minValue = 15
        picker.value = 80
        picker.setOnValueChangedListener{ _, _, current ->
            val updateIntent = Intent("NotifyValueUpdate")
            updateIntent.putExtra("notifyValue", current)
            sendBroadcast(updateIntent)
        }
    }
}