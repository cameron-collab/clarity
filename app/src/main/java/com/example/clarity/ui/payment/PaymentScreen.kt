@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.clarity.ui.payment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
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
import com.example.clarity.data.SessionStore
import com.example.clarity.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentScreen(
    sessionId: String,
    donorId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Brand.ApplySystemBars()

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }

    val gift = SessionStore.selectedGift
    val isMonthly = (gift?.type ?: "OTG").equals("MONTHLY", ignoreCase = true)
    val amountCents = gift?.amountCents ?: 2000
    val currency = (gift?.currency ?: "CAD").lowercase()

    var cardCaptured by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(false) }
    var submitBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    var smsConsent by remember { mutableStateOf(true) }
    var emailConsent by remember { mutableStateOf(true) }
    var mailConsent by remember { mutableStateOf(true) }

    // NFC adapter (null if device has no NFC)
    val nfcAdapter: NfcAdapter? = remember(activity) { activity?.let { NfcAdapter.getDefaultAdapter(it) } }

    // Make sure we turn reader mode off if the composable leaves
    DisposableEffect(activity) {
        onDispose { disableReaderMode(activity) }
    }

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

            // Tap to pay (NFC detector only)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = Brand.cardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Tap your card", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "This simply opens NFC reader mode. When any contactless card/phone is detected, we’ll mark success. No details are saved; no payment is attempted.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    val nfcAvailable = nfcAdapter != null && (nfcAdapter?.isEnabled == true)
                    if (!nfcAvailable) {
                        Text(
                            "NFC not available or disabled on this device.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !capturing && nfcAvailable,
                            onClick = {
                                error = null
                                successMsg = null
                                cardCaptured = false

                                val act = activity
                                if (act == null) {
                                    error = "No activity context for NFC."
                                    return@OutlinedButton
                                }

                                val ok = enableReaderMode(
                                    act,
                                    onTag = { _ ->
                                        // Tag discovered — flip success and stop reading
                                        act.runOnUiThread {
                                            cardCaptured = true
                                            capturing = false
                                            successMsg = "✅ NFC card detected."
                                            disableReaderMode(act)
                                        }
                                    },
                                    onError = { e ->
                                        act.runOnUiThread {
                                            capturing = false
                                            error = e ?: "NFC read failed."
                                            disableReaderMode(act)
                                        }
                                    }
                                )
                                capturing = ok
                                if (!ok) error = "Failed to enable NFC reader mode."
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Brand.primaryColor()
                            ),
                            border = ButtonDefaults.outlinedButtonBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Brand.primaryColor())
                            )
                        ) {
                            Text(if (capturing) "Waiting for tap…" else "Tap Card (Test)")
                        }

                        if (cardCaptured) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Card captured") },
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

            // Submit (placeholder; same gating as before)
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
                            if (!cardCaptured) { error = "Please tap a card first."; return@launch }
                            if (!signed) { error = "Signature required."; return@launch }

                            // Best-effort: save consents
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
                                error = "Saved payment, but failed to store communication preferences: ${e.message}"
                            }

                            // Placeholder success
                            successMsg = "Card detected & signed. (No payment attempted.)"
                            onDone()
                        } catch (e: Exception) {
                            error = e.message ?: "Payment failed."
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

            successMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/* ---------- NFC helpers ---------- */

private fun enableReaderMode(
    activity: Activity,
    onTag: (Tag) -> Unit,
    onError: (String?) -> Unit
): Boolean {
    val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
    if (!adapter.isEnabled) return false

    val flags =
        NfcAdapter.FLAG_READER_NFC_A or      // Most contactless credit cards
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

    adapter.enableReaderMode(
        activity,
        NfcAdapter.ReaderCallback { tag ->
            try {
                onTag(tag)
            } catch (t: Throwable) {
                onError(t.message)
            }
        },
        flags,
        null
    )
    return true
}

private fun disableReaderMode(activity: Activity?) {
    try {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) } ?: return
        adapter.disableReaderMode(activity)
    } catch (_: Throwable) { /* no-op */ }
}

/* ---------- Small utilities ---------- */

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

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
                        onDragStart = { offset -> currentPath = listOf(offset) },
                        onDrag = { change, _ -> currentPath = currentPath + change.position },
                        onDragEnd = {
                            if (currentPath.size > 1) paths = paths + listOf(currentPath)
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
                colors = ButtonDefaults.textButtonColors(contentColor = Brand.secondaryColor())
            ) { Text("Clear") }
        }
    }
}
