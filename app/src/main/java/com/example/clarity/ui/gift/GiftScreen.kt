package com.example.clarity.ui.gift

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.model.SendSmsIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GiftScreen(
    sessionId: String,
    donorId: String,
    mobileE164: String,                    // <— new
    onNavigateToVerify: () -> Unit,
    onOtg: () -> Unit
) {
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(20.dp)) {
        Text("Gift", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Simple: one preset monthly amount for now (e.g., $20)
        Button(
            enabled = !loading,
            onClick = {
                if (mobileE164.isBlank()) {
                    error = "Missing donor phone — go back and re-enter."
                    return@Button
                }
                loading = true
                error = null
                scope.launch {
                    try {
                        // TODO: replace amount/currency/charity_name with real values from campaign payload
                        val body = SendSmsIn(
                            to_e164 = mobileE164,
                            session_id = sessionId,
                            donor_id = donorId,
                            charity_name = "Globalfaces",   // placeholder; plug in real charity
                            gift_type = "MONTHLY",
                            amount_cents = 2000,
                            currency = "CAD",
                            preview_message = null
                        )
                        withContext(Dispatchers.IO) {
                            RetrofitProvider.api.sendSms(body)
                        }
                        onNavigateToVerify()
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to send SMS"
                    } finally {
                        loading = false
                    }
                }
            }
        ) {
            Text(if (loading) "Sending SMS…" else "Monthly (e.g., $20)")
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = onOtg, enabled = !loading) {
            Text("One-Time Gift")
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
