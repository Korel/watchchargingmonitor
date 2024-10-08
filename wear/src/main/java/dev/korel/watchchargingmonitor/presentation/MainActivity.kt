package dev.korel.watchchargingmonitor.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.rememberPickerState
import dev.korel.watchchargingmonitor.BatteryMonitoringService
import androidx.wear.tooling.preview.devices.WearDevices

class MainActivity : ComponentActivity() {

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {})

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        val serviceIntent = Intent(this, BatteryMonitoringService::class.java)
        startForegroundService(serviceIntent)

        if(!hasNotificationPermission()){
            askNotificationPermission()
        }

        setContent {
            WearApp()
        }
    }

    @Composable
    fun WearApp() {
        val pickerState =
            rememberPickerState(
                initialNumberOfOptions = 87,
                initiallySelectedOption = 65,
                repeatItems = false
            )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                text = "Sends battery % to phone when charging",
                modifier = Modifier.padding(8.dp)
            )

            Text(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.secondary,
                text = "Vibrate at battery %:",
                modifier = Modifier.padding(8.dp)
            )


            Picker(
                state = pickerState,
                modifier = Modifier.size(70.dp),
                contentDescription = "Battery Percentage Picker",

                ) { index ->
                val updateIntent = Intent("NotifyValueUpdate")
                updateIntent.putExtra("notifyValue", index + 15)
                sendBroadcast(updateIntent)
                Text(
                    color = MaterialTheme.colors.secondary,
                    text = "${index + 15}",
                )
            }

        }
    }

    @Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
    @Composable
    fun DefaultPreview() {
        WearApp()
    }
}