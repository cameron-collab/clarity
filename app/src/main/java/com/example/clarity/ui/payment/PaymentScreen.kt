@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.clarity.ui.payment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.payment.TapToPayController
import com.example.clarity.data.SessionStore
import com.example.clarity.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.clarity.api.model.PaymentResult

@Composable
fun PaymentScreen(
    sessionId: String,
    donorId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Brand.ApplySystemBars()

    val scope = rememberCoroutineScope()
    val tapToPayController = remember { TapToPayController() }

    val gift = SessionStore.selectedGift
    val isMonthly = (gift?.type ?: "OTG").equals("MONTHLY", ignoreCase = true)
    val amountCents = gift?.amountCents ?: 2000
    val currency = (gift?.currency ?: "CAD").lowercase()

    // Payment flow states
    var paymentInProgress by remember { mutableStateOf(false) }
    var paymentCompleted by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(false) }
    var submitBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf("Ready to start payment") }

    // Communication preferences
    var smsConsent by remember { mutableStateOf(true) }
    var emailConsent by remember { mutableStateOf(true) }
    var mailConsent by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isMonthly) "Monthly Payment" else "One-Time Payment") },
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
            AcceptedRow()

            // Stripe Terminal Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = Brand.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tap to Pay", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Amount: ${String.format("%.2f", amountCents / 100.0)} ${currency.uppercase()}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(statusMsg, style = MaterialTheme.typography.bodySmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Start Payment Button - now uses real Stripe Terminal
                        // Keep the original PaymentScreen payment processing logic - no special handling needed

                        // Keep the original PaymentScreen payment processing logic - no special handling needed

                        Button(
                            enabled = !paymentInProgress && !paymentCompleted,
                            onClick = {
                                paymentInProgress = true
                                statusMsg = "Processing payment..."
                                error = null

                                scope.launch {
                                    try {
                                        val result = tapToPayController.processCompletePayment(
                                            amountCents = amountCents,
                                            currency = currency,
                                            sessionId = sessionId,
                                            donorId = donorId,
                                            isMonthly = isMonthly
                                        )

                                        when (result) {
                                            is PaymentResult.Success -> {
                                                paymentCompleted = true
                                                paymentInProgress = false
                                                statusMsg = "Payment successful!"
                                                successMsg = if (isMonthly) {
                                                    "Payment completed and monthly subscription set up successfully"
                                                } else {
                                                    "Payment completed successfully"
                                                }
                                            }

                                            is PaymentResult.Failed -> {
                                                error = result.error
                                                statusMsg = "Payment failed"
                                                paymentInProgress = false
                                            }
                                        }

                                    } catch (e: Exception) {
                                        error = "Payment failed: ${e.message}"
                                        statusMsg = "Payment failed"
                                        paymentInProgress = false
                                    }
                                }
                            },
                            colors = Brand.buttonColors()
                        ) {
                            Text(if (paymentInProgress) "Processing..." else "Start Payment")
                        }

                        // Success indicator
                        if (paymentCompleted) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Payment Complete") },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Brand.successColor(),
                                    labelColor = Color.White,
                                    leadingIconContentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Brand.successColor())
                                )
                            )
                        }
                    }
                }
            }

            // Communication Preferences
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
                        Spacer(Modifier.width(8.dp)); Text("Phone / SMS consent")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = emailConsent, onCheckedChange = { emailConsent = it }, colors = Brand.checkboxColors())
                        Spacer(Modifier.width(8.dp)); Text("Email consent")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mailConsent, onCheckedChange = { mailConsent = it }, colors = Brand.checkboxColors())
                        Spacer(Modifier.width(8.dp)); Text("Direct mail consent")
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

            // Submit Button
            val primaryCta = if (isMonthly)
                "Complete Monthly Setup"
            else
                "Complete Donation"

            Button(
                onClick = {
                    error = null
                    submitBusy = true
                    scope.launch {
                        try {
                            if (!paymentCompleted) {
                                error = "Please complete payment first."
                                submitBusy = false
                                return@launch
                            }
                            if (!signed) {
                                error = "Signature required."
                                submitBusy = false
                                return@launch
                            }

                            // Save consents
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
                                error = "Failed to store communication preferences: ${e.message}"
                                submitBusy = false
                                return@launch
                            }

                            successMsg = "Transaction completed successfully!"
                            onDone()
                        } catch (e: Exception) {
                            error = e.message ?: "Failed to complete transaction."
                        } finally {
                            submitBusy = false
                        }
                    }
                },
                enabled = paymentCompleted && signed && !submitBusy,
                colors = Brand.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (submitBusy) "Processing…" else primaryCta)
            }

            successMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.successColor()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

// Keep existing helper functions...
@Composable
private fun AcceptedRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandPill("Visa"); BrandPill("Mastercard"); BrandPill("AmEx"); BrandPill("Google Pay"); BrandPill("Apple Pay")
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
                        onDragStart = { offset -> currentPath = listOf(offset) },
                        onDrag = { change, _ -> currentPath = currentPath + change.position },
                        onDragEnd = {
                            if (currentPath.size > 1) paths = paths + listOf(currentPath)
                            currentPath = emptyList()
                        }
                    )
                }
        ) {
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
                colors = ButtonDefaults.textButtonColors(contentColor = Brand.secondaryColor())
            ) { Text("Clear") }
        }
    }
}