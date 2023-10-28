package com.example.batterymonitor

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.NumberPicker
import java.io.File
import java.io.FileWriter
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    val pickerValueFileName = "pickervalue"
    var pickerValue = 80

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val picker = findViewById<NumberPicker>(R.id.numberpicker)
        val pickerValueFile = File(filesDir, pickerValueFileName)
        if (pickerValueFile.exists()) {
            openFileInput(pickerValueFile.name).use { stream ->
                val value = stream.bufferedReader().use {
                    it.readText()
                }
                println("[Phone] Read value $value")
                try {
                    pickerValue = value.toInt()
                } catch (_: Exception) {
                }
            }
        }
        picker.maxValue = 99
        picker.minValue = 15
        picker.value = pickerValue
        picker.setOnValueChangedListener { _, _, current ->
            val updateIntent = Intent("NotifyValueUpdate")
            updateIntent.putExtra("notifyValue", current)
            sendBroadcast(updateIntent)
            pickerValue = picker.value
        }
        val serviceIntent = Intent(this, BatteryMonitoringService::class.java)
        serviceIntent.putExtra("notifyValue", picker.value)
        this.startForegroundService(serviceIntent)
    }

    override fun onDestroy() {
        println("onDestroy called")
        super.onDestroy()
        val pickerValueFile = File(filesDir, pickerValueFileName)
        if (!pickerValueFile.exists()) pickerValueFile.createNewFile()
        val fileWriter = FileWriter(pickerValueFile, false)
        fileWriter.write(pickerValue.toString())
        fileWriter.close()
    }
}