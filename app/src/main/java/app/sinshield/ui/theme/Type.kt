package app.sinshield.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.sinshield.R

@OptIn(ExperimentalTextApi::class)
private fun switzer(weight: FontWeight) = Font(
    resId = R.font.switzer,
    weight = weight,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal)
)

val SwitzerFontFamily = FontFamily(
    switzer(FontWeight.Thin),
    switzer(FontWeight.ExtraLight),
    switzer(FontWeight.Light),
    switzer(FontWeight.Normal),
    switzer(FontWeight.Medium),
    switzer(FontWeight.SemiBold),
    switzer(FontWeight.Bold),
    switzer(FontWeight.ExtraBold),
    switzer(FontWeight.Black)
)

val SwitzerHeavyFontFamily = FontFamily(
    Font(R.font.switzer_heavy, FontWeight.Black)
)

private val MaterialTypography = Typography()

val Typography = Typography(
    displayLarge = MaterialTypography.displayLarge.copy(fontFamily = SwitzerFontFamily),
    displayMedium = MaterialTypography.displayMedium.copy(fontFamily = SwitzerFontFamily),
    displaySmall = MaterialTypography.displaySmall.copy(fontFamily = SwitzerFontFamily),
    headlineLarge = MaterialTypography.headlineLarge.copy(fontFamily = SwitzerFontFamily),
    headlineMedium = MaterialTypography.headlineMedium.copy(fontFamily = SwitzerFontFamily),
    headlineSmall = MaterialTypography.headlineSmall.copy(fontFamily = SwitzerFontFamily),
    titleLarge = MaterialTypography.titleLarge.copy(fontFamily = SwitzerFontFamily),
    titleMedium = MaterialTypography.titleMedium.copy(fontFamily = SwitzerFontFamily),
    titleSmall = MaterialTypography.titleSmall.copy(fontFamily = SwitzerFontFamily),
    bodyLarge = MaterialTypography.bodyLarge.copy(fontFamily = SwitzerFontFamily),
    bodyMedium = MaterialTypography.bodyMedium.copy(fontFamily = SwitzerFontFamily),
    bodySmall = MaterialTypography.bodySmall.copy(fontFamily = SwitzerFontFamily),
    labelLarge = MaterialTypography.labelLarge.copy(fontFamily = SwitzerFontFamily),
    labelMedium = MaterialTypography.labelMedium.copy(fontFamily = SwitzerFontFamily),
    labelSmall = MaterialTypography.labelSmall.copy(fontFamily = SwitzerFontFamily)
)
