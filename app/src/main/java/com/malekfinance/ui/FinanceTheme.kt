package com.malekfinance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object FinanceColors {
    val Background = Color(0xFF080F13)
    val Surface = Color(0xFF111D23)
    val SurfaceElevated = Color(0xFF1B2B31)
    val Primary = Color(0xFFE3C68D)
    val PrimaryLight = Color(0xFFF2DBAE)
    val PrimaryTint = Color(0xFF332E24)
    val TextPrimary = Color(0xFFF4F1E9)
    val TextSecondary = Color(0xFFA5B7BD)
    val Income = Color(0xFF73D9B2)
    val Expense = Color(0xFFFFA399)
    val Warning = Color(0xFFE9C879)
    val Accent = Color(0xFF6BD5C0)
    val HeroStart = Color(0xFF243B3C)
    val HeroEnd = Color(0xFF29271F)
    val Divider = Color(0xFF2B3C43)
}

@Composable
fun FinanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = FinanceColors.Primary,
            onPrimary = FinanceColors.Background,
            primaryContainer = FinanceColors.PrimaryTint,
            onPrimaryContainer = FinanceColors.PrimaryLight,
            secondary = FinanceColors.Accent,
            onSecondary = FinanceColors.Background,
            secondaryContainer = FinanceColors.HeroStart,
            onSecondaryContainer = FinanceColors.Accent,
            tertiary = FinanceColors.PrimaryLight,
            onTertiary = FinanceColors.Background,
            tertiaryContainer = FinanceColors.PrimaryTint,
            onTertiaryContainer = FinanceColors.PrimaryLight,
            background = FinanceColors.Background,
            onBackground = FinanceColors.TextPrimary,
            surface = FinanceColors.Surface,
            onSurface = FinanceColors.TextPrimary,
            surfaceVariant = FinanceColors.SurfaceElevated,
            onSurfaceVariant = FinanceColors.TextSecondary,
            surfaceContainer = FinanceColors.Surface,
            surfaceContainerLow = FinanceColors.Surface,
            surfaceContainerLowest = FinanceColors.Background,
            surfaceContainerHigh = FinanceColors.SurfaceElevated,
            surfaceContainerHighest = FinanceColors.SurfaceElevated,
            outline = FinanceColors.TextSecondary,
            outlineVariant = FinanceColors.Divider,
            inverseSurface = FinanceColors.TextPrimary,
            inverseOnSurface = FinanceColors.Background,
            inversePrimary = FinanceColors.PrimaryTint,
            error = FinanceColors.Expense,
            onError = FinanceColors.Background,
            errorContainer = FinanceColors.PrimaryTint,
            onErrorContainer = FinanceColors.Expense
        ),
        shapes = androidx.compose.material3.Shapes(
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
