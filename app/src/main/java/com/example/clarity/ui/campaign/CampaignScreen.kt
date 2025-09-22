package com.example.clarity.ui.campaign

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.clarity.data.SessionStore
import com.example.clarity.ui.theme.Brand
import com.google.accompanist.systemuicontroller.rememberSystemUiController


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignScreen(
    sessionId: String,
    fundraiserId: String,
    onStartDonation: () -> Unit
) {
    Brand.ApplySystemBars()
    // Pull campaign/charity/fundraiser from the session cache
    val campaign = SessionStore.campaign
    val charityName = SessionStore.charityName ?: "Your Charity"
    val charityLogoUrl = SessionStore.charityLogoUrl.orEmpty()
    val charityBlurb = SessionStore.charityBlurb.orEmpty()
    val fundraiserName = SessionStore.fundraiserDisplayName ?: fundraiserId

    // SAFE: read MaterialTheme inside a composable
    val fallbackBrand = MaterialTheme.colorScheme.primary
    val brandColor: Color = remember(SessionStore.brandPrimaryHex, fallbackBrand) {
        hexToColorOrNull(SessionStore.brandPrimaryHex) ?: fallbackBrand
    }

    println("=== DEBUG: SessionStore.charityLogoUrl = '${SessionStore.charityLogoUrl}' ===")
    println("=== DEBUG: SessionStore.charityName = '${SessionStore.charityName}' ===")


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
        ) {


            // Charity card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (charityLogoUrl.isNotBlank()) {
                        println("=== DEBUG: Loading image from URL: $charityLogoUrl ===")
                        AsyncImage(
                            model = charityLogoUrl,
                            contentDescription = "$charityName logo",
                            modifier = Modifier
                                .size(140.dp)
                                .padding(top = 4.dp),
                            onError = { error ->
                                println("=== DEBUG: Image load failed: ${error.result.throwable} ===")
                            },
                            onSuccess = {
                                println("=== DEBUG: Image loaded successfully ===")
                            }
                        )
                    } else {
                        println("=== DEBUG: No logo URL provided - charityLogoUrl is empty ===")
                    }

                    Text(
                        text = charityName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )

                    if (charityBlurb.isNotBlank()) {
                        Text(
                            text = charityBlurb,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStartDonation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = Brand.buttonColors(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Start Donation", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.weight(1f)) // pushes following to the bottom

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${SessionStore.charityName} — ${SessionStore.campaign?.name.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${SessionStore.fundraiserDisplayName.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------- Helpers (non-composable) ---------- */

private fun hexToColorOrNull(hex: String?): Color? {
    val s = hex?.trim()?.removePrefix("#") ?: return null
    val parsed = s.toLongOrNull(16) ?: return null
    return when (s.length) {
        6 -> {
            val r = ((parsed shr 16) and 0xFF).toInt()
            val g = ((parsed shr 8) and 0xFF).toInt()
            val b = (parsed and 0xFF).toInt()
            Color(r, g, b)
        }
        8 -> {
            val a = ((parsed shr 24) and 0xFF).toInt()
            val r = ((parsed shr 16) and 0xFF).toInt()
            val g = ((parsed shr 8) and 0xFF).toInt()
            val b = (parsed and 0xFF).toInt()
            Color(r, g, b, a)
        }
        else -> null
    }
}

private fun firstName(displayName: String): String =
    displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
