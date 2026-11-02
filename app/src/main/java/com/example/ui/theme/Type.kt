package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Circular dot-matrix display font (Doto) reserved strictly for primary screen titles
@OptIn(ExperimentalTextApi::class)
val NdotFontFamily: FontFamily = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            resId = R.font.doto,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(650))
        ),
        Font(
            resId = R.font.doto,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800))
        )
    )
} else {
    FontFamily(
        Font(R.font.doto, FontWeight.Normal),
        Font(R.font.doto, FontWeight.Bold)
    )
}

// Technical monospace font for codecs, bitrates, and counters
val MonoFontFamily = FontFamily(
    Font(R.font.space_mono, FontWeight.Normal),
    Font(R.font.space_mono, FontWeight.Bold)
)

// Crisp geometric sans-serif with explicit variable weight axis (300..700)
@OptIn(ExperimentalTextApi::class)
val GroteskFontFamily: FontFamily = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            resId = R.font.space_grotesk,
            weight = FontWeight.Light,
            variationSettings = FontVariation.Settings(FontVariation.weight(320))
        ),
        Font(
            resId = R.font.space_grotesk,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            resId = R.font.space_grotesk,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(520))
        ),
        Font(
            resId = R.font.space_grotesk,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(610))
        ),
        Font(
            resId = R.font.space_grotesk,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700))
        )
    )
} else {
    FontFamily(
        Font(R.font.space_grotesk, FontWeight.Light),
        Font(R.font.space_grotesk, FontWeight.Normal),
        Font(R.font.space_grotesk, FontWeight.Medium),
        Font(R.font.space_grotesk, FontWeight.SemiBold),
        Font(R.font.space_grotesk, FontWeight.Bold)
    )
}

// Wide-axis variable geometric sans (Outfit 280..900) for dramatic on-the-spot lyric thickening
@OptIn(ExperimentalTextApi::class)
val LyricsFontFamily: FontFamily = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    FontFamily(
        Font(
            resId = R.font.outfit,
            weight = FontWeight.Light,
            variationSettings = FontVariation.Settings(FontVariation.weight(300))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(520))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(650))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(780))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(850))
        ),
        Font(
            resId = R.font.outfit,
            weight = FontWeight.Black,
            variationSettings = FontVariation.Settings(FontVariation.weight(900))
        )
    )
} else {
    GroteskFontFamily
}

private val CleanPlatformTextStyle = PlatformTextStyle(includeFontPadding = true)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NdotFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.5.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    displayMedium = TextStyle(
        fontFamily = NdotFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.4.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    displaySmall = TextStyle(
        fontFamily = NdotFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.5.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
        platformStyle = CleanPlatformTextStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
        platformStyle = CleanPlatformTextStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = CleanPlatformTextStyle
    ),
    titleLarge = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
        platformStyle = CleanPlatformTextStyle
    ),
    titleMedium = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    titleSmall = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.1.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    bodySmall = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    labelLarge = TextStyle(
        fontFamily = GroteskFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        platformStyle = CleanPlatformTextStyle
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.6.sp,
        platformStyle = CleanPlatformTextStyle
    )
)
