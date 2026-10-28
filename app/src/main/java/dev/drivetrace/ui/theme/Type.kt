package dev.drivetrace.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.drivetrace.R

val Plex = FontFamily(
    Font(R.font.plex_sans, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plex_sans_bold, FontWeight.Bold)
)
val Instruments = FontFamily(Font(R.font.plex_mono))
val TraceTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Plex), displayMedium = t.displayMedium.copy(fontFamily = Plex), displaySmall = t.displaySmall.copy(fontFamily = Plex),
        headlineLarge = t.headlineLarge.copy(fontFamily = Plex), headlineMedium = t.headlineMedium.copy(fontFamily = Plex), headlineSmall = t.headlineSmall.copy(fontFamily = Plex),
        titleLarge = t.titleLarge.copy(fontFamily = Plex), titleMedium = t.titleMedium.copy(fontFamily = Plex), titleSmall = t.titleSmall.copy(fontFamily = Plex),
        bodyLarge = t.bodyLarge.copy(fontFamily = Plex), bodyMedium = t.bodyMedium.copy(fontFamily = Plex), bodySmall = t.bodySmall.copy(fontFamily = Plex),
        labelLarge = t.labelLarge.copy(fontFamily = Plex), labelMedium = t.labelMedium.copy(fontFamily = Plex), labelSmall = t.labelSmall.copy(fontFamily = Plex)
    )
}
