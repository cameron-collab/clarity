package com.example.clarity.ui.verify
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier          // ← Modifier
import androidx.compose.ui.unit.dp           // ← dp
import com.example.clarity.api.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun SmsVerifyScreen(sessionId: String, donorId: String, onYes: () -> Unit, onNo: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // small effect: start polling
    LaunchedEffect(sessionId, donorId) {
        while (true) {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitProvider.api.getSmsStatus(sessionId = sessionId, donorId = donorId)
                }
                // resp should contain "result" with values like "YES","NO","PENDING", or None
                when ((resp.result ?: "PENDING").uppercase()) {
                    "YES" -> { onYes(); break }
                    "NO"  -> { onNo(); break }
                    else -> { /* still pending */ }
                }
            } catch (e: Exception) {
                // don't crash; show a small error then continue
                error = e.message
            }
            kotlinx.coroutines.delay(2000L) // 2s poll interval
        }
    }

    Column(Modifier.padding(20.dp)) {
        Text("Waiting for SMS YES…", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onYes() }) { Text("Simulate YES") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onNo() }) { Text("Simulate NO (go back)") }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
