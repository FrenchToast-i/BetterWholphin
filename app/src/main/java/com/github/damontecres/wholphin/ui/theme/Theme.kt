package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Shapes
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BoldBlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BrownThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GreenThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OledThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OrangeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PurpleThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RedThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.PURPLE }

fun getThemeColors(appThemeColors: AppThemeColors): ThemeColors =
    when (appThemeColors) {
        AppThemeColors.PURPLE -> PurpleThemeColors
        AppThemeColors.BLUE -> BlueThemeColors
        AppThemeColors.GREEN -> GreenThemeColors
        AppThemeColors.ORANGE -> OrangeThemeColors
        AppThemeColors.OLED_BLACK -> OledThemeColors
        AppThemeColors.BOLD_BLUE -> BoldBlueThemeColors
        AppThemeColors.RED -> RedThemeColors
        AppThemeColors.BROWN -> BrownThemeColors
        AppThemeColors.UNRECOGNIZED -> PurpleThemeColors
    }

@Composable
fun WholphinTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    // Glass mode is its own design system, including its typeface. It is passed in rather than
    // read from LocalLiquidGlass because this theme wraps the provider.
    liquidGlass: Boolean = false,
    content: @Composable () -> Unit,
) {
    val themeColors = getThemeColors(appThemeColors)

    val baseScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    // Glass mode is monochrome by design: focus is a lit white surface with black type, the same
    // signal the top bar uses. Leaving the palette's lavender in place makes the one focusable
    // row inside a glass sheet look like a widget from a different app.
    val colorScheme =
        if (liquidGlass) {
            baseScheme.copy(
                primary = Color.White,
                onPrimary = Color.Black,
                primaryContainer = Color.White,
                onPrimaryContainer = Color.Black,
                // The accent of a monochrome interface is white: progress, sliders and seek bars
                // all read their fill from `border` in this app, and a lavender spinner over
                // glass looks like a widget from the theme the user turned off.
                border = Color.White,
                secondary = Color.White,
                tertiary = Color.White,
                onSurface = Color.White,
                onBackground = Color.White,
                onSurfaceVariant = Color.White.copy(alpha = 0.78f),
                // A focused list row fills with `inverseSurface` (a light slab in the dark
                // scheme), which on a glass sheet reads as a lavender widget from another app.
                // Dark glass plus white type keeps settings part of the material.
                inverseSurface = Color.Black.copy(alpha = 0.38f),
                inverseOnSurface = Color.White,
            )
        } else {
            baseScheme
        }
    CompositionLocalProvider(LocalTheme provides appThemeColors) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = androidx.compose.material3.Typography(),
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = if (liquidGlass) GlassTypography else AppTypography,
                // Glass has no sharp corners. The scale catches every component that does not set
                // its own shape (settings rows, sliders, plain surfaces); panels, cards and pills
                // already carry their own radii.
                shapes = if (liquidGlass) GlassShapes else Shapes(),
                content = content,
            )
        }
    }
}
