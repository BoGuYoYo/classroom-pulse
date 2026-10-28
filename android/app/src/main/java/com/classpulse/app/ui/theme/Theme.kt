package com.classpulse.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF142A2E)
val InkSoft = Color(0xFF314545)
val Teal = Color(0xFF0F5B55)
val TealDeep = Color(0xFF073D39)
val TealLight = Color(0xFF3E8178)
val Mint = Color(0xFFD7E9DF)
val MintDark = Color(0xFFB7D8C8)
val Coral = Color(0xFFE86A4A)
val CoralSoft = Color(0xFFF7D8CE)
val Gold = Color(0xFFE9B44C)
val GoldSoft = Color(0xFFF4E5BE)
val Paper = Color(0xFFF7F3E8)
val Cream = Color(0xFFFFFCF5)
val Line = Color(0xFFE5DDCC)
val Muted = Color(0xFF687572)
val Success = Color(0xFF2D7A56)
val SuccessSoft = Color(0xFFDCEEE3)
val Danger = Color(0xFFB43D2C)
val DangerSoft = Color(0xFFF8DDD7)
val BlueSoft = Color(0xFFDCE7ED)
val BlueDeep = Color(0xFF2C5364)

private val ClassPulseColors = lightColorScheme(
    primary = Teal,
    onPrimary = Cream,
    primaryContainer = Mint,
    onPrimaryContainer = TealDeep,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = Cream,
    tertiaryContainer = CoralSoft,
    onTertiaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EBDD),
    onSurfaceVariant = InkSoft,
    outline = Line,
    outlineVariant = Color(0xFFF0E9D9),
    error = Danger,
    onError = Cream,
    errorContainer = DangerSoft,
    onErrorContainer = Ink,
)

private val ClassPulseTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 54.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
)

private val ClassPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun ClassPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClassPulseColors,
        typography = ClassPulseTypography,
        shapes = ClassPulseShapes,
        content = content,
    )
}
