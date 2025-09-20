package com.example.clarity.ui.gift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.model.SendSmsIn
import com.example.clarity.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

// Top-level enum (don’t declare inside @Composable)
private enum class GiftMode { MONTHLY, OTG }

/**
 * Gift screen that uses campaign config:
 * - Monthly presets from SessionStore.campaign.presetAmounts (cents)
 * - OTG minimum from SessionStore.campaign.minAmount (cents)
 */
@Composable
fun GiftScreen(
    sessionId: String,
    donorId: String,
    mobileE164: String,
    onNavigateToVerify: () -> Unit,
    onOtg: () -> Unit
) {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Read campaign snapshot (nullable)
    val cfg = SessionStore.campaign
    val currency = cfg?.currency ?: "CAD"
    val charityName = SessionStore.charityName ?: "Your Charity"

    // UI state
    var mode by rememberSaveable { mutableStateOf(GiftMode.MONTHLY) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Monthly: selected preset (in cents)
    val monthlyPresets: List<Int> =
        (cfg?.presetAmounts?.takeIf { it.isNotEmpty() } ?: listOf(2000, 3000, 4000, 5000))
            .sorted()

    var monthlyAmountCents by rememberSaveable {
        mutableStateOf(monthlyPresets.firstOrNull() ?: 2000)
    }

    // OTG: free entry with minimum
    val otgMinCents = max(100, cfg?.minAmount ?: 100) // safety: min $1.00 if missing
    var otgAmountText by rememberSaveable { mutableStateOf(((otgMinCents / 100.0)).toString()) }

    fun centsToLabel(cents: Int) = "$${"%.0f".format(cents / 100.0)} $currency"

    // Validate OTG raw text to cents (null if invalid or below min)
    fun otgTextToCentsOrNull(txt: String): Int? {
        val clean = txt.trim().replace("[^0-9.]".toRegex(), "")
        val value = clean.toDoubleOrNull() ?: return null
        val cents = (value * 100).toInt()
        return if (cents >= otgMinCents) cents else null
    }

    Column(Modifier.padding(20.dp)) {
        Text("Gift", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // If campaign is missing, show a soft guard (prevents null crashes)
        if (cfg == null) {
            Text(
                "No campaign configuration found. Please log in again.",
                color = MaterialTheme.colorScheme.error
            )
            return@Column
        }

        // Toggle row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == GiftMode.MONTHLY,
                onClick = { mode = GiftMode.MONTHLY },
                label = { Text("Monthly") }
            )
            FilterChip(
                selected = mode == GiftMode.OTG,
                onClick = { mode = GiftMode.OTG },
                label = { Text("One-time") }
            )
        }

        Spacer(Modifier.height(16.dp))

        if (mode == GiftMode.MONTHLY) {
            // Preset chips
            Text("Choose a monthly amount:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                monthlyPresets.forEach { amt ->
                    AssistChip(
                        onClick = { monthlyAmountCents = amt },
                        label = { Text(centsToLabel(amt)) },
                        leadingIcon = {
                            if (monthlyAmountCents == amt) Icon(
                                Icons.Default.Check,
                                contentDescription = null
                            )
                        },
                        enabled = !loading
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                enabled = !loading && mobileE164.isNotBlank(),
                onClick = {
                    if (mobileE164.isBlank()) {
                        error = "Missing donor phone — go back and re-enter."
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val body = SendSmsIn(
                                to_e164 = mobileE164,
                                session_id = sessionId,
                                donor_id = donorId,
                                charity_name = charityName,
                                gift_type = "MONTHLY",
                                amount_cents = monthlyAmountCents,
                                currency = currency,
                                preview_message = null // backend composes final message
                            )
                            withContext(Dispatchers.IO) { RetrofitProvider.api.sendSms(body) }
                            onNavigateToVerify()
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to send SMS"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Sending SMS…" else "Continue")
            }
        } else {
            // OTG entry
            Text("Enter a one-time amount (min ${centsToLabel(otgMinCents)}):", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = otgAmountText,
                onValueChange = { otgAmountText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                prefix = { Text("$") },
                supportingText = {
                    val cents = otgTextToCentsOrNull(otgAmountText)
                    if (cents == null) Text("Enter at least ${centsToLabel(otgMinCents)}")
                }
            )

            Spacer(Modifier.height(20.dp))

            // Send SMS for OTG too (so the verify screen behavior stays consistent)
            Button(
                enabled = !loading && mobileE164.isNotBlank() && otgTextToCentsOrNull(otgAmountText) != null,
                onClick = {
                    val cents = otgTextToCentsOrNull(otgAmountText)
                    if (mobileE164.isBlank()) {
                        error = "Missing donor phone — go back and re-enter."
                        return@Button
                    }
                    if (cents == null) {
                        error = "Please enter at least ${centsToLabel(otgMinCents)}"
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val body = SendSmsIn(
                                to_e164 = mobileE164,
                                session_id = sessionId,
                                donor_id = donorId,
                                charity_name = charityName,
                                gift_type = "OTG",
                                amount_cents = cents,
                                currency = currency,
                                preview_message = null
                            )
                            withContext(Dispatchers.IO) { RetrofitProvider.api.sendSms(body) }
                            onNavigateToVerify() // we still verify via SMS YES/NO
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to send SMS"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Sending SMS…" else "Continue")
            }

            // If you still want the old “go straight to pay” button, keep this:
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOtg,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Skip SMS and proceed to payment") }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

/* --- Small, dependency-free FlowRow (to avoid bringing in Accompanist) --- */
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier) {
        Row(horizontalArrangement = horizontalArrangement, content = content)
    }
}
