package com.example.clarity.ui.gift
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun GiftScreen(sessionId: String, donorId: String, onMonthly: ()->Unit, onOtg: ()->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Gift", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onMonthly) { Text("Monthly (20/30/40/50)") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOtg) { Text("One-Time Gift") }
    }
}
