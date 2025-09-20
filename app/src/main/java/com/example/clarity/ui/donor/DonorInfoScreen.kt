package com.example.clarity.ui.donor
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun DonorInfoScreen(sessionId: String, fundraiserId: String, onNext: (donorId: String) -> Unit) {
    var donorId by remember { mutableStateOf("donor-demo") } // replace after wiring /donor/upsert
    Column(Modifier.padding(20.dp)) {
        Text("Donor Info", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onNext(donorId) }) { Text("Continue") }
    }
}
