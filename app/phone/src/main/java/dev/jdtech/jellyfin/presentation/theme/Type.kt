package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jdtech.jellyfin.core.presentation.theme.OutfitFamily

/** Outfit roles from docs/identity/VISION.md §4. Body and label keep M3's Roboto defaults. */
private val base = Typography()

val Typography =
    base.copy(
        displayLarge =
            base.displayLarge.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                letterSpacing = (-0.01).em,
            ),
        displayMedium =
            base.displayMedium.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 33.6.sp,
                letterSpacing = (-0.01).em,
            ),
        headlineLarge =
            base.headlineLarge.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 32.2.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            base.headlineMedium.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 27.6.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            base.headlineSmall.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            base.titleLarge.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            base.titleMedium.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        titleSmall =
            base.titleSmall.copy(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.5.sp,
                letterSpacing = 0.sp,
            ),
    )
