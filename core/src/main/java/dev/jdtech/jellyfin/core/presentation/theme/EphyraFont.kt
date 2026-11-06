package dev.jdtech.jellyfin.core.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.jdtech.jellyfin.core.R

/** Display face. Only display, headline and title roles use it; body and label stay Roboto. */
val OutfitFamily =
    FontFamily(
        Font(
            R.font.outfit,
            FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontWeight.Medium, FontStyle.Normal),
        ),
        Font(
            R.font.outfit,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontWeight.SemiBold, FontStyle.Normal),
        ),
        Font(
            R.font.outfit,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontWeight.Bold, FontStyle.Normal),
        ),
    )
