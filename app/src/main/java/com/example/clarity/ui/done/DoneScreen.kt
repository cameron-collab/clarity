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


@Composable
fun DoneScreen(
    onStartNextDonation: () -> Unit
) {
    val charity = SessionStore.charityName

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
            Text(
                text = "Thank you for your donation to \n$charity!",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, textAlign=TextAlign.Center)
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
