package com.example.clarity.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clarity.api.FundraiserLoginIn
import com.example.clarity.api.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.await
import androidx.compose.foundation.layout.*   // Column, Row, Spacer, padding(), height()
import androidx.compose.material3.*          // Text, Button, MaterialTheme (M3)
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp           // ← dp

@Composable
fun LoginScreen(onLoggedIn: (sessionId: String, fundraiserId: String) -> Unit) {
    var fundraiserId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(20.dp)) {
        Text("Fundraiser Login", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = fundraiserId,
            onValueChange = { fundraiserId = it },
            label = { Text("Fundraiser ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = fundraiserId.isNotBlank() && !loading,
            onClick = {
                loading = true; error = null
                scope.launch(Dispatchers.IO) {
                    try {
                        val res = RetrofitProvider.api.login(FundraiserLoginIn(fundraiserId)).await()
                        onLoggedIn(res.session_id, fundraiserId)
                    } catch (e: Exception) {
                        error = e.message
                    } finally { loading = false }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Signing in…" else "Sign in") }

        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }
    }
}
