package com.example.clarity.ui.comms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun CommsScreen(sessionId: String, donorId: String, onNext: (needsTerms: Boolean)->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Comms Preferences", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onNext(true) }) { Text("Monthly → Terms & Signature") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onNext(false) }) { Text("OTG → Done") }
    }
}
