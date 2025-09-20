package com.example.clarity.ui.signature
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun SignatureScreen(sessionId: String, donorId: String, onSigned: ()->Unit) {
    Column(Modifier.padding(20.dp)) {
        Text("Signature", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSigned) { Text("Simulate Sign & Continue") }
    }
}
