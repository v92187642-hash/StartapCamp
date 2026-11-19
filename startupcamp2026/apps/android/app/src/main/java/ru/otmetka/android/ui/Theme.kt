package ru.otmetka.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Large text for older users: every size is a step or two above Material's
 * defaults. Sizes are in sp, so the phone's own font-size setting still
 * applies on top. Screens are single scrolling columns, which keeps them
 * usable when the text grows further.
 */
private val LargeType = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodyMedium = bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = labelLarge.copy(fontSize = 19.sp, lineHeight = 24.sp),
    )
}

private val Light = lightColorScheme(primary = Color(0xFF1C5FA8), error = Color(0xFFA3261C))
private val Dark = darkColorScheme(primary = Color(0xFF7DB7F5), error = Color(0xFFFF8A80))

/** Green of a marked entry; Material has no "success" colour of its own. */
val MarkedGreen @Composable get() = if (isSystemInDarkTheme()) Color(0xFF6FCF97) else Color(0xFF1A6B3C)

@Composable
fun OtmetkaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = LargeType,
        content = content,
    )
}

/** Buttons are tall enough to hit easily. */
fun Modifier.bigTouchTarget(): Modifier = heightIn(min = 56.dp)
