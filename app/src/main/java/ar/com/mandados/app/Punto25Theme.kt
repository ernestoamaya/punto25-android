package ar.com.mandados.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class Punto25SemanticColor(
    val foreground: Color,
    val container: Color,
    val onContainer: Color
) {
    /** Normal text rendered on [container] always uses [onContainer]. */
    val normalTextOnContainer: Color get() = onContainer
}

internal data class Punto25SemanticColors(
    val success: Punto25SemanticColor,
    val warning: Punto25SemanticColor,
    val info: Punto25SemanticColor,
    val danger: Punto25SemanticColor,
    val neutral: Punto25SemanticColor
)

internal data class Punto25Palette(
    val brandPrimary: Color,
    val onBrandPrimary: Color,
    val brandPrimaryContainer: Color,
    val onBrandPrimaryContainer: Color,
    val brandSecondary: Color,
    val onBrandSecondary: Color,
    val brandSecondaryContainer: Color,
    val onBrandSecondaryContainer: Color,
    val brandAmber: Color,
    val onBrandAmber: Color,
    val brandAmberContainer: Color,
    val onBrandAmberContainer: Color,
    val surfaceCanvas: Color,
    val surfaceBase: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val semantic: Punto25SemanticColors
)

internal val Punto25LightPalette = Punto25Palette(
    brandPrimary = Color(0xFF006B5E),
    onBrandPrimary = Color(0xFFFFFFFF),
    brandPrimaryContainer = Color(0xFFD8EEE8),
    onBrandPrimaryContainer = Color(0xFF0A332C),
    brandSecondary = Color(0xFF536B64),
    onBrandSecondary = Color(0xFFFFFFFF),
    brandSecondaryContainer = Color(0xFFDDE7E2),
    onBrandSecondaryContainer = Color(0xFF253D36),
    brandAmber = Color(0xFF9A630F),
    onBrandAmber = Color(0xFFFFFFFF),
    brandAmberContainer = Color(0xFFF6E1B6),
    onBrandAmberContainer = Color(0xFF4C350B),
    surfaceCanvas = Color(0xFFF4F1E9),
    surfaceBase = Color(0xFFFCF8F0),
    surfaceRaised = Color(0xFFFFFDF8),
    surfaceMuted = Color(0xFFE9E3D8),
    textPrimary = Color(0xFF202522),
    textSecondary = Color(0xFF565C57),
    borderSubtle = Color(0xFFC9C2B6),
    borderStrong = Color(0xFF7A817C),
    semantic = Punto25SemanticColors(
        success = Punto25SemanticColor(Color(0xFF146C2E), Color(0xFFDDF4E3), Color(0xFF14351D)),
        warning = Punto25SemanticColor(Color(0xFF8A5600), Color(0xFFFCE8BF), Color(0xFF3E2A00)),
        info = Punto25SemanticColor(Color(0xFF315DA8), Color(0xFFDDE8FF), Color(0xFF162D52)),
        danger = Punto25SemanticColor(Color(0xFFA84D4D), Color(0xFFF4D9D7), Color(0xFF542323)),
        neutral = Punto25SemanticColor(Color(0xFF565C57), Color(0xFFE9E3D8), Color(0xFF2C302D))
    )
)

internal val Punto25DarkPalette = Punto25Palette(
    brandPrimary = Color(0xFF62D7C3),
    onBrandPrimary = Color(0xFF00382F),
    brandPrimaryContainer = Color(0xFF174C43),
    onBrandPrimaryContainer = Color(0xFFD2F5ED),
    brandSecondary = Color(0xFFAFCBC2),
    onBrandSecondary = Color(0xFF19372F),
    brandSecondaryContainer = Color(0xFF29453D),
    onBrandSecondaryContainer = Color(0xFFD2E8E1),
    brandAmber = Color(0xFFF1B44C),
    onBrandAmber = Color(0xFF442B00),
    brandAmberContainer = Color(0xFF5F430F),
    onBrandAmberContainer = Color(0xFFFFE7B2),
    surfaceCanvas = Color(0xFF0D1513),
    surfaceBase = Color(0xFF14201D),
    surfaceRaised = Color(0xFF1A2824),
    surfaceMuted = Color(0xFF20302C),
    textPrimary = Color(0xFFE3ECE8),
    textSecondary = Color(0xFFBBC9C4),
    borderSubtle = Color(0xFF384A45),
    borderStrong = Color(0xFF7D918A),
    semantic = Punto25SemanticColors(
        success = Punto25SemanticColor(Color(0xFF6DD58C), Color(0xFF0B3D1D), Color(0xFFC8F2D4)),
        warning = Punto25SemanticColor(Color(0xFFF1B44C), Color(0xFF442B00), Color(0xFFFFE3A3)),
        info = Punto25SemanticColor(Color(0xFF9FC2FF), Color(0xFF17325F), Color(0xFFD9E6FF)),
        danger = Punto25SemanticColor(Color(0xFFE18B86), Color(0xFF542D2D), Color(0xFFFFDAD7)),
        neutral = Punto25SemanticColor(Color(0xFFBBC9C4), Color(0xFF20302C), Color(0xFFDDE7E2))
    )
)

internal val MandadosLightColors = lightColorScheme(
    primary = Punto25LightPalette.brandPrimary,
    onPrimary = Punto25LightPalette.onBrandPrimary,
    primaryContainer = Punto25LightPalette.brandPrimaryContainer,
    onPrimaryContainer = Punto25LightPalette.onBrandPrimaryContainer,
    secondary = Punto25LightPalette.brandSecondary,
    onSecondary = Punto25LightPalette.onBrandSecondary,
    secondaryContainer = Punto25LightPalette.brandSecondaryContainer,
    onSecondaryContainer = Punto25LightPalette.onBrandSecondaryContainer,
    tertiary = Punto25LightPalette.brandAmber,
    onTertiary = Punto25LightPalette.onBrandAmber,
    tertiaryContainer = Punto25LightPalette.brandAmberContainer,
    onTertiaryContainer = Punto25LightPalette.onBrandAmberContainer,
    background = Punto25LightPalette.surfaceCanvas,
    onBackground = Punto25LightPalette.textPrimary,
    surface = Punto25LightPalette.surfaceBase,
    onSurface = Punto25LightPalette.textPrimary,
    surfaceVariant = Punto25LightPalette.surfaceMuted,
    onSurfaceVariant = Punto25LightPalette.textSecondary,
    outline = Punto25LightPalette.borderStrong,
    outlineVariant = Punto25LightPalette.borderSubtle,
    error = Punto25LightPalette.semantic.danger.foreground,
    errorContainer = Punto25LightPalette.semantic.danger.container,
    onErrorContainer = Punto25LightPalette.semantic.danger.normalTextOnContainer
)

internal val MandadosDarkColors = darkColorScheme(
    primary = Punto25DarkPalette.brandPrimary,
    onPrimary = Punto25DarkPalette.onBrandPrimary,
    primaryContainer = Punto25DarkPalette.brandPrimaryContainer,
    onPrimaryContainer = Punto25DarkPalette.onBrandPrimaryContainer,
    secondary = Punto25DarkPalette.brandSecondary,
    onSecondary = Punto25DarkPalette.onBrandSecondary,
    secondaryContainer = Punto25DarkPalette.brandSecondaryContainer,
    onSecondaryContainer = Punto25DarkPalette.onBrandSecondaryContainer,
    tertiary = Punto25DarkPalette.brandAmber,
    onTertiary = Punto25DarkPalette.onBrandAmber,
    tertiaryContainer = Punto25DarkPalette.brandAmberContainer,
    onTertiaryContainer = Punto25DarkPalette.onBrandAmberContainer,
    background = Punto25DarkPalette.surfaceCanvas,
    onBackground = Punto25DarkPalette.textPrimary,
    surface = Punto25DarkPalette.surfaceBase,
    onSurface = Punto25DarkPalette.textPrimary,
    surfaceVariant = Punto25DarkPalette.surfaceMuted,
    onSurfaceVariant = Punto25DarkPalette.textSecondary,
    outline = Punto25DarkPalette.borderStrong,
    outlineVariant = Punto25DarkPalette.borderSubtle,
    error = Punto25DarkPalette.semantic.danger.foreground,
    errorContainer = Punto25DarkPalette.semantic.danger.container,
    onErrorContainer = Punto25DarkPalette.semantic.danger.normalTextOnContainer
)

private fun frozenTextStyle(sizeSp: Int, lineHeightSp: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp
)

internal data class Punto25TypeScale(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val action: TextStyle,
    val moneyData: TextStyle
)

internal val Punto25Type = Punto25TypeScale(
    display = frozenTextStyle(32, 38, FontWeight.Bold),
    h1 = frozenTextStyle(28, 34, FontWeight.Bold),
    h2 = frozenTextStyle(22, 28, FontWeight.Bold),
    h3 = frozenTextStyle(18, 24, FontWeight.SemiBold),
    bodyLarge = frozenTextStyle(16, 24, FontWeight.Normal),
    bodyMedium = frozenTextStyle(14, 20, FontWeight.Normal),
    bodySmall = frozenTextStyle(12, 18, FontWeight.Normal),
    labelLarge = frozenTextStyle(14, 20, FontWeight.SemiBold),
    labelMedium = frozenTextStyle(12, 16, FontWeight.SemiBold),
    action = frozenTextStyle(14, 20, FontWeight.Bold),
    moneyData = frozenTextStyle(20, 28, FontWeight.Bold)
)

internal val Punto25Typography = Typography(
    displayLarge = Punto25Type.display,
    headlineLarge = Punto25Type.h1,
    headlineMedium = Punto25Type.h2,
    headlineSmall = Punto25Type.h3,
    titleLarge = Punto25Type.h3,
    titleMedium = Punto25Type.labelLarge,
    titleSmall = Punto25Type.labelMedium,
    bodyLarge = Punto25Type.bodyLarge,
    bodyMedium = Punto25Type.bodyMedium,
    bodySmall = Punto25Type.bodySmall,
    labelLarge = Punto25Type.action,
    labelMedium = Punto25Type.labelMedium,
    labelSmall = Punto25Type.bodySmall
)

internal object Punto25Spacing {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp

    val scale = listOf(none, xxs, xs, sm, md, lg, xl, xxl, xxxl, huge)
}

internal object Punto25Radii {
    val control = 8.dp
    val card = 12.dp
    val prominent = 16.dp
    val heroDialog = 24.dp
    val pill = 999.dp
}

internal val Punto25Shapes = Shapes(
    extraSmall = RoundedCornerShape(Punto25Radii.control),
    small = RoundedCornerShape(Punto25Radii.control),
    medium = RoundedCornerShape(Punto25Radii.card),
    large = RoundedCornerShape(Punto25Radii.prominent),
    extraLarge = RoundedCornerShape(Punto25Radii.heroDialog)
)

internal object Punto25Elevation {
    val e0 = 0.dp
    val e1 = 1.dp
    val e2 = 4.dp
}

internal object Punto25Sizing {
    val minimumTouchTarget = 48.dp
    val clientPrimaryButtonMinHeight = 52.dp
    val riderPrimaryCtaMinHeight = 56.dp
    val adminPrimaryButtonMinHeight = 48.dp
    val iconInline = 20.dp
    val iconStandard = 24.dp
    val iconExpressive = 32.dp
}

internal object Punto25Motion {
    const val PRESS_STATE_MS = 120
    const val COMPONENT_CONTAINER_MS = 180
    const val CONTENT_REORDER_MS = 240
}
