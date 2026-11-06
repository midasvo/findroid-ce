package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography as TypographyTv
import dev.jdtech.jellyfin.core.presentation.theme.OutfitFamily

val Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                lineHeight = 58.8.sp,
                letterSpacing = (-0.01).em,
            ),
        displayMedium =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                lineHeight = 50.4.sp,
                letterSpacing = (-0.01).em,
            ),
        displaySmall =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 37.8.sp,
                letterSpacing = (-0.01).em,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 36.8.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 32.2.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 27.6.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 27.5.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 22.5.sp,
                letterSpacing = 0.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 27.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

val TypographyTv =
    TypographyTv(
        displayLarge = Typography.displayLarge,
        displayMedium = Typography.displayMedium,
        displaySmall = Typography.displaySmall,
        headlineLarge = Typography.headlineLarge,
        headlineMedium = Typography.headlineMedium,
        headlineSmall = Typography.headlineSmall,
        titleLarge = Typography.titleLarge,
        titleMedium = Typography.titleMedium,
        titleSmall = Typography.titleSmall,
        bodyLarge = Typography.bodyLarge,
        bodyMedium = Typography.bodyMedium,
        bodySmall = Typography.bodySmall,
        labelLarge = Typography.labelLarge,
        labelMedium = Typography.labelMedium,
        labelSmall = Typography.labelSmall,
    )
