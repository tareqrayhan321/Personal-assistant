package com.personalmentor.app.presentation.macro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.personalmentor.app.domain.macro.MacroSection

/** Colours of the macro screens: navy list/editor bars, red triggers, blue actions, green constraints. */
object MacroColors {
    val Navy = Color(0xFF3B4F72)
    val NavyDark = Color(0xFF34476A)
    val Teal = Color(0xFF00838F)

    fun bar(section: MacroSection): Color = when (section) {
        MacroSection.TRIGGER -> Color(0xFFE53935)
        MacroSection.ACTION -> Color(0xFF0277BD)
        MacroSection.CONSTRAINT -> Color(0xFF388E3C)
    }

    fun header(section: MacroSection): Color = when (section) {
        MacroSection.TRIGGER -> Color(0xFFE8504E)
        MacroSection.ACTION -> Color(0xFF1E96D8)
        MacroSection.CONSTRAINT -> Color(0xFF52A855)
    }

    fun body(section: MacroSection): Color = when (section) {
        MacroSection.TRIGGER -> Color(0xFFF6C1C0)
        MacroSection.ACTION -> Color(0xFFB0D8F0)
        MacroSection.CONSTRAINT -> Color(0xFFC3E2C4)
    }
}

fun categoryIcon(category: String?): ImageVector = when (category) {
    "Applications" -> Icons.Default.Apps
    "Battery/Power" -> Icons.Default.Power
    "Camera/Photo" -> Icons.Default.PhotoCamera
    "Conditions/Loops" -> Icons.Default.Repeat
    "Connectivity" -> Icons.Default.Router
    "Date/Time" -> Icons.Default.Today
    "Device Actions" -> Icons.Default.AutoFixHigh
    "Device Events", "Device State" -> Icons.Default.PhoneAndroid
    "Files" -> Icons.Default.Description
    "Logging" -> Icons.AutoMirrored.Filled.List
    "MacroDroid Specific" -> Icons.Default.Android
    "AI" -> Icons.Default.Psychology
    "Macros" -> Icons.Default.Layers
    "Media" -> Icons.Default.PlayCircleFilled
    "Notification" -> Icons.Default.Notifications
    "Screen" -> Icons.Default.ScreenLockPortrait
    "User Input" -> Icons.Default.Person
    "Variables" -> Icons.Default.Code
    "Volume" -> Icons.Default.VolumeUp
    "Web Interactions" -> Icons.Default.Public
    else -> Icons.Default.Info
}

/** "Ran 4 hours ago" style text; [millis] null means never. */
fun relativeTime(millis: Long?, now: Long = System.currentTimeMillis()): String {
    if (millis == null || millis <= 0) return "never"
    val minutes = ((now - millis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60} hour${if (minutes / 60 == 1L) "" else "s"} ago"
        else -> "${minutes / (60 * 24)} day${if (minutes / (60 * 24) == 1L) "" else "s"} ago"
    }
}
