package com.jaagrit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaagrit.app.ui.theme.MuktaFontFamily

/**
 * Brand: "JAAGRIT" wordmark with "जागृत" as a separate Text element (AGENTS.md & Rule 3).
 * Never in the same line of text.
 */
@Composable
fun JaagritBrandHeader(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    wordmarkSize: TextUnit = 38.sp,
    subSize: TextUnit = 16.sp,
    wordmarkColor: Color = MaterialTheme.colorScheme.primary,
    subColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Text(
            text = "JAAGRIT",
            fontSize = wordmarkSize,
            fontWeight = FontWeight.Bold,
            color = wordmarkColor,
            fontFamily = MuktaFontFamily,
            lineHeight = (wordmarkSize.value * 1.15f).sp
        )
        Text(
            text = "जागृत",
            fontSize = subSize,
            fontWeight = FontWeight.Medium,
            color = subColor,
            fontFamily = MuktaFontFamily,
            lineHeight = (subSize.value * 1.45f).sp,
            letterSpacing = 0.sp
        )
    }
}

/**
 * Language switch toggle: "हिं | EN" (Rule 2).
 * Persisted in DataStore, default Hindi. Changes screen immediately without restart.
 */
@Composable
fun LanguageSwitch(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isHindi = currentLanguage != "en"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isHindi) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.clickable { onLanguageSelected("hi") }
            ) {
                Text(
                    text = "हिं",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHindi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    fontFamily = MuktaFontFamily,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (!isHindi) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.clickable { onLanguageSelected("en") }
            ) {
                Text(
                    text = "EN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!isHindi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    fontFamily = MuktaFontFamily,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
