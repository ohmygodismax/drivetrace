package dev.drivetrace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import dev.drivetrace.ui.App
import dev.drivetrace.ui.theme.Accent
import dev.drivetrace.ui.theme.Bg
import dev.drivetrace.ui.theme.Ink
import dev.drivetrace.ui.theme.Line
import dev.drivetrace.ui.theme.Muted
import dev.drivetrace.ui.theme.Panel
import dev.drivetrace.ui.theme.Secondary
import dev.drivetrace.ui.theme.TraceTypography

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { MaterialTheme(typography = TraceTypography, colorScheme = darkColorScheme(primary = Accent, onPrimary = Bg, secondary = Secondary, background = Bg, surface = Panel, onSurface = Ink, onBackground = Ink, outline = Line, surfaceVariant = Color(0xFF2B323B), onSurfaceVariant = Muted)) { App((application as DriveTraceApplication).engine) } }
    }
}
