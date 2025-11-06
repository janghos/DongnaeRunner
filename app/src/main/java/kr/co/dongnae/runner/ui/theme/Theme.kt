package kr.co.dongnae.runner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NRCColorScheme = darkColorScheme(
    primary = NRCPumpkin,
    onPrimary = Color.White,
    primaryContainer = NRCPumpkinDark,
    onPrimaryContainer = Color.White,
    secondary = NRCVolt,
    onSecondary = Color.Black,
    secondaryContainer = NRCVoltDark,
    onSecondaryContainer = Color.Black,
    background = NRCBackground,
    onBackground = NRCOnBackground,
    surface = NRCSurface,
    onSurface = NRCOnBackground,
    surfaceVariant = NRCSurfaceVariant,
    onSurfaceVariant = NRCOnSurfaceVariant,
    outline = NRCOutline,
    inverseOnSurface = NRCBackground,
    inverseSurface = NRCOnBackground
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun DongneRunnerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NRCColorScheme,
        typography = Typography,
        content = content
    )
}