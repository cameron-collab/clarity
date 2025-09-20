package com.example.clarity.ui.verify
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun SmsVerifyScreen(sessionId: String, donorId: String, onYes: ()->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Waiting for SMS YES…", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        // For now: manual advance; later poll backend or subscribe via websocket
        Button(onClick = onYes) { Text("Simulate YES") }
    }
}
