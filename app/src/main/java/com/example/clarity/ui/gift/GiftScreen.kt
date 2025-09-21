package com.example.clarity.ui.gift

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.clarity.api.RetrofitProvider
import com.example.clarity.api.model.SendSmsIn
import com.example.clarity.data.SelectedGift
import com.example.clarity.data.SessionStore
import com.example.clarity.ui.theme.Brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftScreen(
    sessionId: String,
    donorId: String,
    mobileE164: String,
    onGoToPay: () -> Unit,
    onBackToDonor: () -> Unit
) {
    Brand.ApplySystemBars()

    val scope = rememberCoroutineScope()
    val campaign = SessionStore.campaign
    val currency = (campaign?.currency ?: "CAD").uppercase()
    val minOtgCents = campaign?.minAmount ?: 1000
    val charityName = SessionStore.charityName

    // --- UI state ---
    var isMonthly by remember { mutableStateOf(true) }
    var selectedCents by remember { mutableStateOf(campaign?.presetAmounts?.firstOrNull() ?: 2000) }
    var otgAmountInput by remember { mutableStateOf("") } // dollars text
    var sending by remember { mutableStateOf(false) }
    var polling by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Terms & Conditions
    val termsUrl = SessionStore.charityTermsUrl.orEmpty()
    var termsAgreed by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    fun otgDollarsToCentsOrNull(txt: String): Int? {
        val dollars = txt.trim().toDoubleOrNull() ?: return null
        return max(0.0, dollars).times(100.0).toInt()
    }

    LaunchedEffect(isMonthly) {
        error = null
        if (isMonthly) {
            selectedCents = campaign?.presetAmounts?.firstOrNull() ?: 2000
        } else {
            otgAmountInput = "%.0f".format(minOtgCents / 100.0)
        }
    }

    // Poll for SMS result after sending
    LaunchedEffect(polling) {
        if (!polling) return@LaunchedEffect
        statusNote = "Text sent. Waiting for donor reply…"
        while (polling) {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitProvider.api.getSmsStatus(sessionId = sessionId, donorId = donorId)
                }
                when ((resp.result ?: "PENDING").uppercase()) {
                    "YES" -> {
                        statusNote = "Donor confirmed ✅"
                        polling = false
                        onGoToPay()
                    }
                    "NO" -> {
                        statusNote = "Donor declined ❌"
                        polling = false
                        onBackToDonor()
                    }
                    else -> { /* still pending */ }
                }
            } catch (e: Exception) {
                statusNote = "Error checking status: ${e.message}"
            }
            delay(2000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gift") },
                colors = Brand.appBarColors()
            )
        }
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Constrain width and give a little elevation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = Brand.cardColors()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header + charity name
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            charityName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Frequency toggle (centered) — use brand chip
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Brand.FrequencyChip("Monthly", isMonthly) { isMonthly = true }
                        Brand.FrequencyChip("One-Time", !isMonthly) { isMonthly = false }
                    }

                    if (isMonthly) {
                        // Monthly preset chips
                        val presets = campaign?.presetAmounts?.takeIf { it.isNotEmpty() }
                            ?: listOf(2000, 3000, 4000, 5000)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Choose monthly amount", style = MaterialTheme.typography.titleMedium)
                            presets.chunked(5).forEach { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEach { cents ->
                                        val selected = selectedCents == cents
                                        FilterChip(
                                            selected = selected,
                                            onClick = { selectedCents = cents },
                                            label = { Text(centsToLabel(cents, currency)) },
                                            colors = Brand.chipColors()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // One-time entry
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Enter one-time amount (min ${centsToLabel(minOtgCents, currency)})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(
                                value = otgAmountInput,
                                onValueChange = { otgAmountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                singleLine = true,
                                prefix = { Text("$") },
                                trailingIcon = { Text(currency) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Brand.primaryColor(),
                                    focusedLabelColor  = Brand.primaryColor(),
                                    cursorColor        = Brand.primaryColor()
                                )
                            )
                        }
                    }

                    // Terms & Conditions block
                    TermsAndConditionsBlock(
                        termsUrl = termsUrl,
                        agreed = termsAgreed,
                        onToggleAgree = { termsAgreed = it },
                        onOpen = { if (termsUrl.isNotBlank()) showTermsDialog = true }
                    )

                    // Send SMS button (gated by termsAgreed)
                    Button(
                        onClick = {
                            if (mobileE164.isBlank()) {
                                error = "Missing donor phone — go back and re-enter."
                                return@Button
                            }
                            if (!termsAgreed) {
                                error = "Please agree to the Terms & Conditions."
                                return@Button
                            }
                            val (giftType, amountCents) =
                                if (isMonthly) "MONTHLY" to selectedCents
                                else {
                                    val cents = otgDollarsToCentsOrNull(otgAmountInput)
                                    if (cents == null) {
                                        error = "Enter a valid one-time amount."
                                        return@Button
                                    }
                                    if (cents < minOtgCents) {
                                        error = "Minimum one-time is ${centsToLabel(minOtgCents, currency)}."
                                        return@Button
                                    }
                                    "OTG" to cents
                                }

                            error = null
                            sending = true
                            statusNote = null
                            scope.launch {
                                try {
                                    val body = SendSmsIn(
                                        to_e164 = mobileE164,
                                        session_id = sessionId,
                                        donor_id = donorId,
                                        charity_name = SessionStore.charityName,
                                        gift_type = giftType,
                                        amount_cents = amountCents,
                                        currency = currency,
                                        preview_message = null
                                    )
                                    SessionStore.selectedGift = SelectedGift(
                                        type = giftType,
                                        amountCents = amountCents,
                                        currency = currency
                                    )
                                    withContext(Dispatchers.IO) { RetrofitProvider.api.sendSms(body) }
                                    polling = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to send SMS"
                                } finally {
                                    sending = false
                                }
                            }
                        },
                        enabled = !sending && !polling && termsAgreed,
                        colors = Brand.buttonColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(if (sending) "Sending…" else "Send Verification Text")
                    }

                    statusNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    // In-app Terms viewer (modal dialog with WebView)
    if (showTermsDialog) {
        TermsDialog(termsUrl) { showTermsDialog = false }
    }
}

/* ---------- UI pieces (top-level; NOT inside GiftScreen) ---------- */

@Composable
private fun TermsAndConditionsBlock(
    termsUrl: String,
    agreed: Boolean,
    onToggleAgree: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Terms & Conditions", style = MaterialTheme.typography.titleMedium)
            if (termsUrl.isNotBlank()) {
                TextButton(onClick = onOpen) { Text("View") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = agreed,
                onCheckedChange = onToggleAgree,
                colors = Brand.checkboxColors()
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "I agree to the Terms & Conditions" +
                        if (termsUrl.isNotBlank()) " (see link above)" else ""
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsDialog(termsUrl: String, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = Brand.container(),           // brand-tinted background
            contentColor = Brand.onContainer()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Terms & Conditions", style = MaterialTheme.typography.titleLarge)
                if (termsUrl.isBlank()) {
                    Text("No Terms URL provided.")
                } else {
                    TermsWebView(url = termsUrl)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        colors = Brand.textButtonColors()   // brand-colored "Close"
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TermsWebView(url: String) {
    Box(Modifier.height(500.dp)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
            update = { it.loadUrl(url) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/* ---------- Helpers (top-level) ---------- */

private fun centsToLabel(cents: Int, currency: String): String {
    val d = cents / 100.0
    return "$" + (if (d % 1.0 == 0.0) "%.0f" else "%.2f").format(d) + " $currency"
}
