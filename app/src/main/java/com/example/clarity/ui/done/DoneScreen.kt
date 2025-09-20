package com.example.clarity.ui.done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun DoneScreen() {
    Column(Modifier.padding(20.dp)) {
        Text("Thank you!", style = MaterialTheme.typography.headlineSmall)
        Text("Donation flow complete.")
    }
}
