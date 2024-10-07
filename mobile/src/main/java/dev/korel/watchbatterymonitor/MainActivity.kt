package dev.korel.watchbatterymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.batterymonitor.R
import java.io.File
import java.io.FileWriter


class MainActivity : AppCompatActivity() {

    private val pickerValueFileName = "pickervalue"
    private var pickerValue = 80

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
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
                Log.d("Watchbatterymonitor", "Picker value file read: $value")
                try {
                    pickerValue = value.toInt()
                    if (pickerValue < 15 || pickerValue > 99) {
                        pickerValue = 80
                    }
                } catch (_: Exception) {
                }
            }
        } else {
            Log.d("Watchbatterymonitor", "Picker value file does not exist")
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
        val notificationPermissionText = findViewById<TextView>(R.id.textView2)
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                notificationPermissionText.text = "Notification permission: granted"
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationPermissionText.text = "Notification permission: granted"
        }
        notificationPermissionText.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                val intent: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.setData(uri)
                startActivity(intent)
            }
        }
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