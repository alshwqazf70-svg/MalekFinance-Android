package com.malekfinance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object FinanceColors {
    val Background = Color(0xFF0D0D12)
    val Surface = Color(0xFF17151D)
    val SurfaceElevated = Color(0xFF211A2B)
    val Primary = Color(0xFFA855F7)
    val PrimaryLight = Color(0xFFC084FC)
    val PrimaryTint = Color(0xFF2A1B38)
    val TextPrimary = Color(0xFFF5F3F7)
    val TextSecondary = Color(0xFFA7A1B0)
    val Income = Color(0xFF86C99A)
    val Expense = Color(0xFFE58B91)
    val Warning = Color(0xFFE7BD70)
    val Divider = Color(0xFF39313F)
}

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FinanceColors.Primary,
            onPrimary = Color.White,
            secondary = FinanceColors.PrimaryLight,
            background = FinanceColors.Background,
            onBackground = FinanceColors.TextPrimary,
            surface = FinanceColors.Surface,
            onSurface = FinanceColors.TextPrimary,
            surfaceVariant = FinanceColors.SurfaceElevated,
            onSurfaceVariant = FinanceColors.TextSecondary,
            error = FinanceColors.Expense
        ),
        content = content
    )
}
