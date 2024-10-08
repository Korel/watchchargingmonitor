package dev.korel.watchchargingmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.NumberPicker
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter


class MainActivity : AppCompatActivity() {

    private val pickerValueFileName = "pickervalue"
    private var pickerValue = 80
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            updateNotificationPermissionText(isGranted)
        }


    private fun updateNotificationPermissionText(isGranted: Boolean) {
        val text =
            if (isGranted) "Notification permission: granted" else "Notification permission: not granted!"
        findViewById<TextView>(R.id.notificationPermissionTextView).text = text
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun hasNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun askNotificationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupNotificationPermission() {
        if (hasNotificationPermission()) {
            updateNotificationPermissionText(true)
        } else {
            askNotificationPermission()
        }

        findViewById<TextView>(R.id.notificationPermissionTextView).setOnClickListener {
            if (!hasNotificationPermission()) {
                askNotificationPermission()
            }
        }
    }


    private fun setupValuePicker() {
        val picker = findViewById<NumberPicker>(R.id.numberpicker)
        pickerValue = 80
        val pickerValueFile = File(filesDir, pickerValueFileName)
        if (pickerValueFile.exists()) {
            openFileInput(pickerValueFile.name).use { stream ->
                val value = stream.bufferedReader().use {
                    it.readText()
                }
                try {
                    pickerValue = value.toInt()
                    if (pickerValue < 15 || pickerValue > 99) {
                        pickerValue = 80
                    }
                } catch (_: Exception) {
                }
            }
        } else {
            pickerValueFile.createNewFile()
            val fileWriter = FileWriter(pickerValueFile, false)
            fileWriter.write(pickerValue.toString())
            fileWriter.close()
        }
        picker.maxValue = 99
        picker.minValue = 15
        picker.value = pickerValue

        picker.setOnScrollListener { _, scrollState ->
            if (scrollState == NumberPicker.OnScrollListener.SCROLL_STATE_IDLE) {
                val currentValue = picker.value
                val updateIntent = Intent("NotifyValueUpdate")
                updateIntent.putExtra("notifyValue", currentValue)
                sendBroadcast(updateIntent)
                pickerValue = currentValue
                val fileWriter = FileWriter(pickerValueFile, false)
                fileWriter.write(currentValue.toString())
                fileWriter.close()
                Log.d("Watchbatterymonitor", "Picker value file written: $currentValue")
            }
        }

    }

    private fun setupForegroundService() {
        val picker = findViewById<NumberPicker>(R.id.numberpicker)
        val serviceIntent = Intent(this, BatteryMonitoringService::class.java)
        serviceIntent.putExtra("notifyValue", picker.value)
        this.startForegroundService(serviceIntent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            launch { setupNotificationPermission() }
            launch { setupValuePicker() }
            launch { setupForegroundService() }
        }
    }

}