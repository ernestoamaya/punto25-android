package ar.com.mandados.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Punto25ThemeFoundationsTest {
    @Test
    fun `REG-P11-D1-COLOR-TOKENS-001 frozen light and dark palettes are exact`() {
        assertPalette(
            Punto25LightPalette,
            expected = listOf(
                0xFF006B5E, 0xFFFFFFFF, 0xFFD8EEE8, 0xFF0A332C,
                0xFF536B64, 0xFFFFFFFF, 0xFFDDE7E2, 0xFF253D36,
                0xFF9A630F, 0xFFFFFFFF, 0xFFF6E1B6, 0xFF4C350B,
                0xFFF4F1E9, 0xFFFCF8F0, 0xFFFFFDF8, 0xFFE9E3D8,
                0xFF202522, 0xFF565C57, 0xFFC9C2B6, 0xFF7A817C
            )
        )
        assertSemantic(
            Punto25LightPalette.semantic,
            listOf(
                Triple(0xFF146C2E, 0xFFDDF4E3, 0xFF14351D),
                Triple(0xFF8A5600, 0xFFFCE8BF, 0xFF3E2A00),
                Triple(0xFF315DA8, 0xFFDDE8FF, 0xFF162D52),
                Triple(0xFFA84D4D, 0xFFF4D9D7, 0xFF542323),
                Triple(0xFF565C57, 0xFFE9E3D8, 0xFF2C302D)
            )
        )

        assertPalette(
            Punto25DarkPalette,
            expected = listOf(
                0xFF62D7C3, 0xFF00382F, 0xFF174C43, 0xFFD2F5ED,
                0xFFAFCBC2, 0xFF19372F, 0xFF29453D, 0xFFD2E8E1,
                0xFFF1B44C, 0xFF442B00, 0xFF5F430F, 0xFFFFE7B2,
                0xFF0D1513, 0xFF14201D, 0xFF1A2824, 0xFF20302C,
                0xFFE3ECE8, 0xFFBBC9C4, 0xFF384A45, 0xFF7D918A
            )
        )
        assertSemantic(
            Punto25DarkPalette.semantic,
            listOf(
                Triple(0xFF6DD58C, 0xFF0B3D1D, 0xFFC8F2D4),
                Triple(0xFFF1B44C, 0xFF442B00, 0xFFFFE3A3),
                Triple(0xFF9FC2FF, 0xFF17325F, 0xFFD9E6FF),
                Triple(0xFFE18B86, 0xFF542D2D, 0xFFFFDAD7),
                Triple(0xFFBBC9C4, 0xFF20302C, 0xFFDDE7E2)
            )
        )

        assertEquals(Color(0xFF9A630F), MandadosLightColors.tertiary)
        assertEquals(Punto25LightPalette.borderSubtle, MandadosLightColors.outlineVariant)
        assertEquals(Punto25DarkPalette.borderSubtle, MandadosDarkColors.outlineVariant)
    }

    @Test
    fun `REG-P11-D1-DANGER-CONTRAST-001 danger normal text uses on-container with accessible contrast`() {
        val danger = Punto25LightPalette.semantic.danger
        assertEquals(Color(0xFFA84D4D), danger.foreground)
        assertEquals(Color(0xFFF4D9D7), danger.container)
        assertEquals(Color(0xFF542323), danger.onContainer)
        assertEquals(danger.onContainer, danger.normalTextOnContainer)
        assertEquals(danger.onContainer, MandadosLightColors.onErrorContainer)

        val foregroundContrast = contrastRatio(danger.foreground, danger.container)
        val normalTextContrast = contrastRatio(danger.normalTextOnContainer, danger.container)
        assertTrue("danger.on/container must exceed 4.5:1, was $normalTextContrast", normalTextContrast >= 4.5)
        assertTrue("danger.fg/container is not the normal-text pairing", foregroundContrast < 4.5)
        assertTrue(normalTextContrast > foregroundContrast)
    }

    @Test
    fun `REG-P11-D1-TYPOGRAPHY-001 frozen type scale is exact and system-font based`() {
        assertType(Punto25Type.display, 32, 38, FontWeight.Bold)
        assertType(Punto25Type.h1, 28, 34, FontWeight.Bold)
        assertType(Punto25Type.h2, 22, 28, FontWeight.Bold)
        assertType(Punto25Type.h3, 18, 24, FontWeight.SemiBold)
        assertType(Punto25Type.bodyLarge, 16, 24, FontWeight.Normal)
        assertType(Punto25Type.bodyMedium, 14, 20, FontWeight.Normal)
        assertType(Punto25Type.bodySmall, 12, 18, FontWeight.Normal)
        assertType(Punto25Type.labelLarge, 14, 20, FontWeight.SemiBold)
        assertType(Punto25Type.labelMedium, 12, 16, FontWeight.SemiBold)
        assertType(Punto25Type.action, 14, 20, FontWeight.Bold)
        assertType(Punto25Type.moneyData, 20, 28, FontWeight.Bold)

        assertEquals(Punto25Type.h1, Punto25Typography.headlineLarge)
        assertEquals(Punto25Type.h2, Punto25Typography.headlineMedium)
        assertEquals(Punto25Type.h3, Punto25Typography.headlineSmall)
        assertEquals(Punto25Type.bodyLarge, Punto25Typography.bodyLarge)
        assertEquals(Punto25Type.action, Punto25Typography.labelLarge)
    }

    @Test
    fun `REG-P11-D1-GEOMETRY-001 frozen spacing geometry sizing elevation and motion are exact`() {
        assertEquals(
            listOf(0.dp, 4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp, 40.dp, 48.dp),
            Punto25Spacing.scale
        )
        assertEquals(8.dp, Punto25Radii.control)
        assertEquals(12.dp, Punto25Radii.card)
        assertEquals(16.dp, Punto25Radii.prominent)
        assertEquals(24.dp, Punto25Radii.heroDialog)
        assertEquals(999.dp, Punto25Radii.pill)
        assertEquals(0.dp, Punto25Elevation.e0)
        assertEquals(1.dp, Punto25Elevation.e1)
        assertEquals(4.dp, Punto25Elevation.e2)
        assertEquals(48.dp, Punto25Sizing.minimumTouchTarget)
        assertEquals(52.dp, Punto25Sizing.clientPrimaryButtonMinHeight)
        assertEquals(56.dp, Punto25Sizing.riderPrimaryCtaMinHeight)
        assertEquals(48.dp, Punto25Sizing.adminPrimaryButtonMinHeight)
        assertEquals(20.dp, Punto25Sizing.iconInline)
        assertEquals(24.dp, Punto25Sizing.iconStandard)
        assertEquals(32.dp, Punto25Sizing.iconExpressive)
        assertEquals(120, Punto25Motion.PRESS_STATE_MS)
        assertEquals(180, Punto25Motion.COMPONENT_CONTAINER_MS)
        assertEquals(240, Punto25Motion.CONTENT_REORDER_MS)
    }

    @Test
    fun `REG-P11-D1-THEME-MODE-001 theme mode and persistence remain exactly SYSTEM LIGHT DARK`() {
        val models = projectSource("src/main/java/ar/com/mandados/app/Models.kt")
        val store = projectSource("src/main/java/ar/com/mandados/app/LocalStore.kt")
        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")

        assertTrue(models.contains("enum class ThemeMode { SYSTEM, LIGHT, DARK }"))
        assertTrue(models.contains("val themeMode: ThemeMode = ThemeMode.SYSTEM"))
        assertTrue(store.contains("themeMode = enumOrDefault(prefs.getString(\"theme_mode\", null), ThemeMode.SYSTEM)"))
        assertTrue(store.contains(".putString(\"theme_mode\", c.themeMode.name)"))
        assertTrue(app.contains("ThemeMode.SYSTEM -> systemDark"))
        assertTrue(app.contains("ThemeMode.LIGHT -> false"))
        assertTrue(app.contains("ThemeMode.DARK -> true"))
        assertTrue(app.contains("EnumRadio(\"Predeterminado del sistema\", cfg.themeMode == ThemeMode.SYSTEM)"))
        assertTrue(app.contains("EnumRadio(\"Claro\", cfg.themeMode == ThemeMode.LIGHT)"))
        assertTrue(app.contains("EnumRadio(\"Oscuro\", cfg.themeMode == ThemeMode.DARK)"))
    }

    @Test
    fun `REG-P11-D1-NO-FUNCTIONAL-CHANGE-001 wiring preserves root surface and navigation`() {
        val app = projectSource("src/main/java/ar/com/mandados/app/MandadosApp.kt")
        assertTrue(app.contains("Surface(modifier = Modifier.fillMaxSize())"))
        assertTrue(app.contains("MandadosNavigation(controller)"))
        assertTrue(app.contains("typography = Punto25Typography"))
        assertTrue(app.contains("shapes = Punto25Shapes"))
        assertTrue(app.contains("colorScheme = if (useDark) MandadosDarkColors else MandadosLightColors"))
    }

    @Test
    fun `REG-P11-D1-SCOPE-001 foundation stays presentation-only and no screen migration is embedded`() {
        val theme = projectSource("src/main/java/ar/com/mandados/app/Punto25Theme.kt")
        val forbidden = listOf(
            "MandadosController",
            "NavController",
            "Firebase",
            "MapLibre",
            "GeoPoint",
            "PaymentRecord",
            "OrderStatus",
            "SharedPreferences"
        )
        forbidden.forEach { token ->
            assertFalse("Foundation must not depend on functional surface: $token", theme.contains(token))
        }
        assertFalse(theme.contains("@Composable"))
    }

    private fun assertPalette(palette: Punto25Palette, expected: List<Long>) {
        val actual = listOf(
            palette.brandPrimary,
            palette.onBrandPrimary,
            palette.brandPrimaryContainer,
            palette.onBrandPrimaryContainer,
            palette.brandSecondary,
            palette.onBrandSecondary,
            palette.brandSecondaryContainer,
            palette.onBrandSecondaryContainer,
            palette.brandAmber,
            palette.onBrandAmber,
            palette.brandAmberContainer,
            palette.onBrandAmberContainer,
            palette.surfaceCanvas,
            palette.surfaceBase,
            palette.surfaceRaised,
            palette.surfaceMuted,
            palette.textPrimary,
            palette.textSecondary,
            palette.borderSubtle,
            palette.borderStrong
        )
        assertEquals(expected.map(::Color), actual)
    }

    private fun assertSemantic(semantic: Punto25SemanticColors, expected: List<Triple<Long, Long, Long>>) {
        val actual = listOf(semantic.success, semantic.warning, semantic.info, semantic.danger, semantic.neutral)
        expected.zip(actual).forEach { (colors, role) ->
            assertEquals(Color(colors.first), role.foreground)
            assertEquals(Color(colors.second), role.container)
            assertEquals(Color(colors.third), role.onContainer)
        }
    }

    private fun assertType(style: androidx.compose.ui.text.TextStyle, size: Int, lineHeight: Int, weight: FontWeight) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(lineHeight.sp, style.lineHeight)
        assertEquals(weight, style.fontWeight)
        assertEquals(FontFamily.Default, style.fontFamily)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val high = max(relativeLuminance(a), relativeLuminance(b))
        val low = min(relativeLuminance(a), relativeLuminance(b))
        return (high + 0.05) / (low + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun projectSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("No se encontró source de regresión: $relativePath")
    }
}
