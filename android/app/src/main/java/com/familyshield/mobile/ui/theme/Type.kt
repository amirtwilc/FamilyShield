package com.familyshield.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.familyshield.mobile.R

// Hanken Grotesk — the FamilyShield design-system typeface (per the Stitch spec):
// contemporary, highly legible, a refined alternative to Roboto.
val Hanken = FontFamily(
    Font(R.font.hanken_regular, FontWeight.Normal),
    Font(R.font.hanken_medium, FontWeight.Medium),
    Font(R.font.hanken_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_bold, FontWeight.Bold),
    Font(R.font.hanken_extrabold, FontWeight.ExtraBold),
)

// Kept as aliases so existing call sites compile; both resolve to Hanken now.
val Sora = Hanken
val Jakarta = Hanken

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)
