package com.jaagrit.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jaagrit.app.R

/**
 * Bundled Mukta font family (OFL license) for matched Latin and Devanagari baselines.
 * No synthetic bold: explicitly maps weights to bundled bold and medium font assets.
 */
val MuktaFontFamily = FontFamily(
    Font(R.font.mukta_regular, FontWeight.Normal),
    Font(R.font.mukta_medium, FontWeight.Medium),
    Font(R.font.mukta_bold, FontWeight.SemiBold),
    Font(R.font.mukta_bold, FontWeight.Bold),
    Font(R.font.mukta_bold, FontWeight.ExtraBold),
    Font(R.font.mukta_bold, FontWeight.Black)
)

/**
 * Builds Material3 Typography token set tuned per language (AGENTS.md & UI-Horizon rules):
 * - Devanagari: lineHeight ~1.45x font size, letterSpacing = 0 on all Devanagari text,
 *   no uppercase transforms, ~9% larger font size than Latin for equivalent visual weight.
 * - Latin: standard line height (~1.3x) and standard letter spacing.
 */
fun buildJaagritTypography(isHindi: Boolean): Typography {
    val scaleFactor = if (isHindi) 1.09f else 1.0f
    val letterSpacing = if (isHindi) 0.sp else 0.sp // Devanagari strictly 0 letterSpacing

    fun style(
        baseSizeSp: Float,
        weight: FontWeight,
        latinLineHeightRatio: Float = 1.30f,
        latinLetterSpacingSp: Float = 0f
    ): TextStyle {
        val fontSize = (baseSizeSp * scaleFactor).sp
        val lineHeight = if (isHindi) {
            (fontSize.value * 1.45f).sp
        } else {
            (fontSize.value * latinLineHeightRatio).sp
        }
        val sp = if (isHindi) 0.sp else latinLetterSpacingSp.sp
        return TextStyle(
            fontFamily = MuktaFontFamily,
            fontWeight = weight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = sp
        )
    }

    return Typography(
        displayLarge = style(57f, FontWeight.Bold, 1.15f),
        displayMedium = style(45f, FontWeight.Bold, 1.20f),
        displaySmall = style(36f, FontWeight.Bold, 1.25f),

        headlineLarge = style(32f, FontWeight.Bold, 1.25f),
        headlineMedium = style(28f, FontWeight.Bold, 1.30f),
        headlineSmall = style(24f, FontWeight.Bold, 1.35f),

        titleLarge = style(22f, FontWeight.SemiBold, 1.30f),
        titleMedium = style(16f, FontWeight.SemiBold, 1.35f, 0.15f),
        titleSmall = style(14f, FontWeight.Bold, 1.40f, 0.10f),

        bodyLarge = style(16f, FontWeight.Normal, 1.40f, 0.50f),
        bodyMedium = style(14f, FontWeight.Normal, 1.40f, 0.25f),
        bodySmall = style(12f, FontWeight.Normal, 1.40f, 0.40f),

        labelLarge = style(14f, FontWeight.Medium, 1.40f, 0.10f),
        labelMedium = style(12f, FontWeight.Medium, 1.40f, 0.50f),
        labelSmall = style(11f, FontWeight.Medium, 1.40f, 0.50f)
    )
}

// Default Typography (defaults to Hindi per Rule 2)
val Typography = buildJaagritTypography(isHindi = true)