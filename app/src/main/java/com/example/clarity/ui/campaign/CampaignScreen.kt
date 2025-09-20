package com.example.clarity.ui.campaign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CampaignScreen(
    sessionId: String,
    fundraiserId: String,
    onStart: () -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(text = "Campaign", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Session: $sessionId")
        Text(text = "Fundraiser: $fundraiserId")
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onStart) { Text("Start Donation") }
    }
}
