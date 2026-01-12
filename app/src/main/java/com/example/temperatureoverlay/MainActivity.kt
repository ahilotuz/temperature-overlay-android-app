package com.example.temperatureoverlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.temperatureoverlay.data.TemperatureRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Surface gives us a consistent background using the app theme.
            Surface(color = MaterialTheme.colorScheme.background) {
                TemperatureScreen()
            }
        }
    }
}

@Composable
private fun TemperatureScreen() {
    // UI state: whether the temperature display is visible.
    // We "remember" it so it survives recomposition (UI redraws).
    var isVisible by remember { mutableStateOf(true) }

    // Placeholder temperature for now.
    // We'll replace this with a real data source in a later PR.
    val temperatureText = "Temperature: -- °C"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Button(onClick = { isVisible = !isVisible }) {
            Text(if (isVisible) "Hide temperature" else "Show temperature")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isVisible) {
            Card(modifier = Modifier.padding(end = 32.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Device Temperature",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = temperatureText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        // We set expectations early: device temp isn’t always available.
                        text = "Note: real device temperature availability depends on hardware.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
