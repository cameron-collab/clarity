package com.example.clarity.ui.payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun PaymentScreen(sessionId: String, donorId: String, onPaid: (monthly: Boolean)->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Payment", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onPaid(true) }) { Text("Simulate Monthly Payment") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onPaid(false) }) { Text("Simulate OTG Payment") }
    }
}
