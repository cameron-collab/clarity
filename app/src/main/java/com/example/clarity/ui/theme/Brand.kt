@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.clarity.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.clarity.data.SessionStore
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CardColors
import androidx.compose.ui.graphics.lerp

object Brand {

    private fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t)
    /** Very light brand-tinted container (for cards/dialog backgrounds) */
    @Composable fun container(): Color {
        val brand = primaryColor()
        // 85% toward white = soft, non-purple tint
        return mix(brand, Color.White, 0.85f)
    }

    @Composable fun onContainer(): Color {
        // text on the light brand container: fall back to onSurface for readability
        return MaterialTheme.colorScheme.onSurface
    }
    /** Slightly tinted chip background when not selected */
    @Composable fun chipContainer(): Color = mix(primaryColor(), Color.White, 0.90f)

    /** Selected chip container is a stronger brand tint */
    @Composable fun chipSelectedContainer(): Color = mix(primaryColor(), Color.White, 0.20f)

    /** Card colors using our brand container */
    @Composable
    fun cardColors(): CardColors = CardDefaults.cardColors(
        containerColor = container(),
        contentColor   = onContainer()
    )

    @Composable fun successColor(): Color = Color(0xFF16A34A) // “money green”

    /** Parse charity hex or fall back to theme.primary */
    @Composable
    fun primaryColor(): Color =
        hexToColorOrNull(SessionStore.brandPrimaryHex) ?: MaterialTheme.colorScheme.primary

    /** Pick legible on-color (white/black) based on luminance */
    @Composable
    private fun onBrandColor(brand: Color): Color {
        val r = brand.red
        val g = brand.green
        val b = brand.blue
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        return if (luminance > 0.6f) Color.Black else Color.White
    }

    @Composable
    fun FrequencyChip(label: String, selected: Boolean, onClick: () -> Unit) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(label) },
            colors = chipColors()
        )
    }

    @Composable
    fun secondaryColor(): Color {
        val brand = primaryColor()
        // 15% toward black gives a subtle, readable accent
        return lerp(brand, Color.Black, 0.15f)
    }

    @Composable
    fun buttonColors(): ButtonColors {
        val brand = primaryColor()
        val on = onBrandColor(brand)
        return ButtonDefaults.buttonColors(containerColor = brand, contentColor = on)
    }

    @Composable
    fun chipColors(): SelectableChipColors {
        val brand = primaryColor()
        val on = onBrandColor(brand)
        return FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = brand,
            selectedLabelColor = on,
            selectedLeadingIconColor = on,
            selectedTrailingIconColor = on
        )
    }

    @Composable
    fun checkboxColors(): CheckboxColors {
        val brand = primaryColor()
        val on = onBrandColor(brand)
        return CheckboxDefaults.colors(
            checkedColor = brand,
            checkmarkColor = on,
            uncheckedColor = MaterialTheme.colorScheme.outline
        )
    }

    /** Branded text button (for dialog "Close") */
    @Composable
    fun textButtonColors(): ButtonColors =
        ButtonDefaults.textButtonColors(contentColor = primaryColor())

    /**  Use brand color for M3 TopAppBar */
    @Composable
    fun appBarColors(): TopAppBarColors {
        val brand = primaryColor()
        val on = onBrandColor(brand)
        return TopAppBarDefaults.topAppBarColors(   // use topAppBarColors for TopAppBar
            containerColor = brand,
            titleContentColor = on,
            navigationIconContentColor = on,
            actionIconContentColor = on
        )
    }

    /** Status/navigation bars */
    @Composable
    fun ApplySystemBars() {
        val brand = primaryColor()
        // Accompanist controller
        val sys = com.google.accompanist.systemuicontroller.rememberSystemUiController()
        androidx.compose.runtime.DisposableEffect(brand) {
            sys.setStatusBarColor(color = brand, darkIcons = true)
            sys.setNavigationBarColor(color = Color.White, darkIcons = true)
            onDispose { }
        }
    }
}

/** Hex → Color helper */
fun hexToColorOrNull(hex: String?): Color? {
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
