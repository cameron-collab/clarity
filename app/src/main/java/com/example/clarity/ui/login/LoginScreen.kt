package com.example.clarity.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.example.clarity.R
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun LoginScreen(
    onLoggedIn: (sessionId: String, fundraiserId: String) -> Unit
) {
    var fundraiserDigits by remember { mutableStateOf("") } // digits only after "FR"
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val systemUi = rememberSystemUiController()
    DisposableEffect(Unit) {
        systemUi.setStatusBarColor(color = Color(0xFF00D5D7), darkIcons = true)
        systemUi.setNavigationBarColor(color = Color.White, darkIcons = true)
        onDispose { }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.globalfaces_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Transparent overlay box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)) // dim overlay
        )

        // Centered login content
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(24.dp)
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.globalfaces_logo),
                    contentDescription = "Globalfaces Logo",
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 24.dp)
                )

                // Fundraiser ID field
                OutlinedTextField(
                    value = fundraiserDigits,
                    onValueChange = { new ->
                        fundraiserDigits = new.filter { it.isDigit() }  // keep only 0–9
                    },
                    label = { Text("Fundraiser ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    prefix = { Text("FR") }, // visually “auto-populates” FR
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color(0xFF00D5D7),
                        unfocusedIndicatorColor = Color(0xFF00D5D7).copy(alpha = 0.5f),
                        focusedLabelColor = Color(0xFF00D5D7),
                        cursorColor = Color(0xFF00D5D7),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                Spacer(Modifier.height(20.dp))

                // Sign in button
                Button(
                    onClick = {
                        if (loading) return@Button
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                val idUpper = "FR${fundraiserDigits}".trim().uppercase()
                                val res = withContext(Dispatchers.IO) {
                                    RetrofitProvider.api.login(FundraiserLoginIn(idUpper))
                                }

                                // Populate session cache
                                SessionStore.fundraiserId = idUpper
                                SessionStore.sessionId = res.session_id
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
                    enabled = fundraiserDigits.isNotBlank() && !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D5D7),
                        contentColor = Color.White
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                            color = Color.White
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
}
