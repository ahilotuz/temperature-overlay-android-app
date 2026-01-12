package com.example.temperatureoverlay

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.temperatureoverlay.data.TemperatureRepository
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                TemperatureScreen(
                    onKeepScreenOnChanged = { keepOn ->
                        // Keep screen awake only while this Activity is visible.
                        if (keepOn) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TemperatureScreen(
    onKeepScreenOnChanged: (Boolean) -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }

    // Notify the Activity whenever the user toggles keep-screen-on.
    LaunchedEffect(keepScreenOn) {
        onKeepScreenOnChanged(keepScreenOn)
    }

    val context = LocalContext.current.applicationContext
    val repo = remember { TemperatureRepository(context) }

    var tempC by remember { mutableStateOf<Float?>(null) }

    // Simple polling loop:
    // - 5s when visible
    // - 30s when hidden (battery-friendly)
    LaunchedEffect(isVisible) {
        while (true) {
            tempC = repo.readBatteryTemperatureC()
            delay(if (isVisible) 5_000 else 30_000)
        }
    }

    val temperatureText = repo.formatTemperature(tempC)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = keepScreenOn,
                onCheckedChange = { keepScreenOn = it }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Keep screen on")
                Text(
                    text = "Prevents the screen from sleeping while this app is open.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { isVisible = !isVisible }) {
            Text(if (isVisible) "Hide temperature" else "Show temperature")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isVisible) {
            Card(modifier = Modifier.padding(end = 32.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Temperature (Battery)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = temperatureText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Source: Battery API. Some devices may not report a value.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        } else {
            Text(
                text = "Temperature is hidden. (Polling slows down to save battery.)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
