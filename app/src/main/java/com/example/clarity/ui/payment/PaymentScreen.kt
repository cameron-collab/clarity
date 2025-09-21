@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.clarity.ui.payment

import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontWeight
import com.example.clarity.ui.theme.Brand


@Composable
fun PaymentScreen(
    sessionId: String,
    donorId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {

    Brand.ApplySystemBars()

    val scope = rememberCoroutineScope()
    val gift = SessionStore.selectedGift
    val isMonthly = (gift?.type ?: "OTG").equals("MONTHLY", ignoreCase = true)
    val amountCents = gift?.amountCents ?: 2000
    val currency = (gift?.currency ?: "CAD").lowercase()

    var cardCaptured by remember { mutableStateOf(false) }   // placeholder for tap result
    var capturing by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(false) }
    var submitBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    var smsConsent by remember { mutableStateOf(true) }
    var emailConsent by remember { mutableStateOf(true) }
    var mailConsent by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isMonthly) "Monthly Payment" else "One-Time Payment")
                },
                colors = Brand.appBarColors()
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (isMonthly) "Monthly Payment" else "One-Time Payment",
                style = MaterialTheme.typography.headlineSmall
            )

            // Accepted brands / wallets (static icons; replace with real images if you prefer)
            AcceptedRow()

            // Tap to pay (placeholder)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = Brand.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Tap your card (placeholder)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "We’ll integrate Stripe Tap to Pay or PaymentSheet here. " +
                                "For now, tap 'Simulate tap success' to continue.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !capturing,
                            onClick = {
                                // simulate tap flow
                                capturing = true
                                error = null
                                // …do nothing async here; in real flow you’d invoke Stripe SDK
                                cardCaptured = true
                                capturing = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Brand.primaryColor()
                            ),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Brand.primaryColor())
                            )
                        ) { Text(if (capturing) "Capturing…" else "Simulate tap success") }

                        if (cardCaptured) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Card captured") },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Brand.successColor(),         // solid money green
                                    labelColor = Color.White,
                                    leadingIconContentColor = Color.White
                                )
                            )
                        }

                    }
                }
            }

            // Communication Preferences (auto opt-in)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = Brand.cardColors()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Communication Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "You can contact me about my donation and related updates:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = smsConsent, onCheckedChange = { smsConsent = it }, colors = Brand.checkboxColors())
                        Spacer(Modifier.width(8.dp))
                        Text("Phone / SMS consent")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = emailConsent, onCheckedChange = { emailConsent = it }, colors = Brand.checkboxColors())
                        Spacer(Modifier.width(8.dp))
                        Text("Email consent")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mailConsent, onCheckedChange = { mailConsent = it }, colors = Brand.checkboxColors())
                        Spacer(Modifier.width(8.dp))
                        Text("Direct mail consent")
                    }
                }
            }


            // Signature
            Text("Signature", style = MaterialTheme.typography.titleMedium)
            SignaturePad(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                onSignedChanged = { signed = it }
            )

            // Submit
            val primaryCta = if (isMonthly)
                "Submit first payment & schedule"
            else
                "Submit $${"%.2f".format(amountCents / 100.0)} ${currency.uppercase()}"

            Button(
                onClick = {
                    error = null
                    submitBusy = true
                    scope.launch {
                        try {
                            if (!cardCaptured) {
                                error = "Please tap a card first."
                                return@launch
                            }
                            if (!signed) {
                                error = "Signature required."
                                return@launch
                            }

                            // 1) Save consents (best-effort; show error but continue)
                            try {
                                withContext(Dispatchers.IO) {
                                    RetrofitProvider.api.updateDonorConsent(
                                        com.example.clarity.api.model.DonorConsentIn(
                                            session_id = sessionId,
                                            donor_id = donorId,
                                            consent_sms = smsConsent,
                                            consent_email = emailConsent,
                                            consent_mail = mailConsent
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                // Non-fatal; show + log but continue to payment
                                val msg =
                                    "Saved payment, but failed to store communication preferences: ${e.message}"
                                error = msg
                                // Optionally log to EVENT_LOG (since we already wired logEvent previously)
                                scope.launch {
                                    try {
                                        RetrofitProvider.api.logEvent(
                                            com.example.clarity.api.model.LogEventIn(
                                                event_type = "DONOR_CONSENT_UPDATE_ERROR",
                                                session_id = sessionId,
                                                donor_id = donorId,
                                                attributes = mapOf(
                                                    "message" to (e.message ?: "Unknown"),
                                                    "sms" to smsConsent,
                                                    "email" to emailConsent,
                                                    "mail" to mailConsent
                                                )
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                }
                            }

                            // 2) Proceed with payment
                            if (isMonthly) {
                                // Monthly placeholder
                                successMsg =
                                    "Captured card & signature. TODO: SetupIntent + Subscription."
                            } else {
                                val body = com.example.clarity.api.model.PaymentIntentIn(
                                    amount = amountCents,
                                    currency = currency,
                                    session_id = sessionId,
                                    donor_id = donorId
                                )
                                val res = withContext(Dispatchers.IO) {
                                    RetrofitProvider.api.createPaymentIntent(body)
                                }
                                successMsg =
                                    "Created PaymentIntent: ${res.id} (client secret received)."
                            }

                            onDone()
                        } catch (e: Exception) {
                            val msg = e.message ?: "Payment failed."
                            error = msg

                            // Fire-and-forget event log (don’t let this crash the UI)
                            scope.launch {
                                try {
                                    RetrofitProvider.api.logEvent(
                                        com.example.clarity.api.model.LogEventIn(
                                            event_type = if (isMonthly) "PAYMENT_MONTHLY_ERROR" else "PAYMENT_OTG_ERROR",
                                            session_id = sessionId,
                                            donor_id = donorId,
                                            attributes = mapOf(
                                                "message" to msg,
                                                "isMonthly" to isMonthly,
                                                "amount_cents" to amountCents,
                                                "currency" to currency
                                            )
                                        )
                                    )
                                } catch (_: Exception) { /* swallow */
                                }
                            }
                        } finally {
                            submitBusy = false
                        }
                    }
                },
                enabled = cardCaptured && signed && !submitBusy,
                colors = Brand.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (submitBusy) "Processing…" else primaryCta)
            }

            successMsg?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AcceptedRow() {
    // Quick, text-based placeholders. Swap for real brand images if you like.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandPill("Visa")
        BrandPill("Mastercard")
        BrandPill("AmEx")
        BrandPill("Google Pay")
        BrandPill("Apple Pay")
    }
}

@Composable private fun BrandPill(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* ---------------- Signature Pad ---------------- */

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignaturePad(
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.onSurface,
    strokeWidth: Float = 6f,
    onSignedChanged: (Boolean) -> Unit
) {
    var paths by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

    LaunchedEffect(paths) { onSignedChanged(paths.isNotEmpty()) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            currentPath = currentPath + change.position
                        },
                        onDragEnd = {
                            if (currentPath.size > 1) {
                                paths = paths + listOf(currentPath)
                            }
                            currentPath = emptyList()
                        }
                    )
                }
        ) {
            // existing strokes
            paths.forEach { stroke ->
                if (stroke.size > 1) {
                    for (i in 0 until stroke.size - 1) {
                        drawLine(
                            color = strokeColor,
                            start = stroke[i],
                            end = stroke[i + 1],
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            // live stroke
            if (currentPath.size > 1) {
                for (i in 0 until currentPath.size - 1) {
                    drawLine(
                        color = strokeColor,
                        start = currentPath[i],
                        end = currentPath[i + 1],
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { paths = emptyList() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Brand.secondaryColor() // ✅ match charity
                )
            ) {
                Text("Clear")
            }
        }

    }
}
