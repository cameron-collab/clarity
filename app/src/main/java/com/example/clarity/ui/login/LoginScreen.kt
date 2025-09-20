package com.example.clarity.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.clarity.api.FundraiserLoginIn
import com.example.clarity.api.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType



@Composable
fun LoginScreen(
    onLoggedIn: (sessionId: String, fundraiserId: String) -> Unit
) {
    var fundraiserId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Fundraiser Login",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = fundraiserId,
            onValueChange = { fundraiserId = it },
            label = { Text("Fundraiser ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            supportingText = {
                Text("Example: FR123")
            }
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (loading) return@Button
                error = null
                loading = true

                scope.launch {
                    try {
                        val idUpper = fundraiserId.trim().uppercase()
                        // Do network off main thread
                        val res = withContext(Dispatchers.IO) {
                            RetrofitProvider.api.login(FundraiserLoginIn(idUpper))
                        }
                        // Back on main: navigate
                        onLoggedIn(res.session_id, idUpper)
                    } catch (e: Exception) {
                        error = e.message ?: "Login failed"
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = fundraiserId.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 8.dp)
                )
            }
            Text(if (loading) "Signing in…" else "Sign in")
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Error: $error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
