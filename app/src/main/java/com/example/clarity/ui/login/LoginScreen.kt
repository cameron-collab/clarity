package com.example.clarity.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.clarity.api.FundraiserLoginIn
import com.example.clarity.api.RetrofitProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import com.example.clarity.data.CampaignCfg
import com.example.clarity.data.SessionStore

@Composable
fun LoginScreen(
    onLoggedIn: (sessionId: String, fundraiserId: String) -> Unit
) {
    var fundraiserId by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logo (make sure you have the PNG in res/drawable as globalfaces_logo.png)
            Image(
                painter = painterResource(id = com.example.clarity.R.drawable.globalfaces_logo),
                contentDescription = "Globalfaces Logo",
                modifier = Modifier
                    .size(240.dp) // adjust as needed
                    .padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = fundraiserId,
                onValueChange = { fundraiserId = it },
                label = { Text("Fundraiser ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (loading) return@Button
                    error = null
                    loading = true

                    scope.launch {
                        try {
                            val idUpper = fundraiserId.trim().uppercase()
                            val res = withContext(Dispatchers.IO) {
                                RetrofitProvider.api.login(FundraiserLoginIn(idUpper))
                            }

                            // Populate session cache
                            SessionStore.fundraiserId = idUpper
                            SessionStore.fundraiserDisplayName = res.fundraiser["DISPLAY_NAME"] as? String
                            SessionStore.fundraiserFirst = SessionStore.fundraiserDisplayName
                                ?.trim()
                                ?.split(" ")
                                ?.firstOrNull()

                            SessionStore.charityId = res.charity?.get("CHARITY_ID") as? String
                            SessionStore.charityName = res.charity?.get("NAME") as? String ?: "Your Charity"
                            SessionStore.charityLogoUrl = res.charity?.get("LOGO_URL") as? String
                            SessionStore.charityBlurb = res.charity?.get("BLURB") as? String
                            SessionStore.brandPrimaryHex = res.charity?.get("BRAND_PRIMARY_HEX") as? String

                            SessionStore.campaign = CampaignCfg.fromMap(res.campaign)
                            SessionStore.charityTermsUrl = res.charity?.get("TERMS_URL") as? String

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
}
