package com.example.clarity.ui.done

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clarity.data.SessionStore
import androidx.compose.ui.text.style.TextAlign
import com.example.clarity.ui.theme.Brand
import coil.compose.AsyncImage


@Composable
fun DoneScreen(
    onStartNextDonation: () -> Unit
) {
    val charity = SessionStore.charityName
    val charityLogoUrl = SessionStore.charityLogoUrl.orEmpty()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Charity logo
            if (charityLogoUrl.isNotBlank()) {
                AsyncImage(
                    model = charityLogoUrl,
                    contentDescription = "Charity logo",
                    modifier = Modifier
                        .height(220.dp)
                        .padding(bottom = 16.dp)
                )
            }

            Text(
                text = "Thank you for your Donation!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            )

            Text(
                text = "We appreciate your support.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onStartNextDonation,
                colors = Brand.buttonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Back to Campaign")
            }
        }
    }
}