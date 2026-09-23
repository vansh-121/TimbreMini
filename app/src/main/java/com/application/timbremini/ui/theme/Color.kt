package com.application.timbremini.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * TimbreMini design system palette supporting both Studio Dark and Clean Studio Light modes.
 */
data class TimbreColorPalette(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val accent: Color,
    val accentDim: Color,
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val trackInactive: Color,
    val isDark: Boolean
)

// Studio Dark (warm, deep near-black)
val DarkPalette = TimbreColorPalette(
    background = Color(0xFF0C0C0E),
    surface1 = Color(0xFF151517),
    surface2 = Color(0xFF1E1E22),
    hairline = Color(0xFF2A2A30),
    hairlineStrong = Color(0xFF3A3A42),
    accent = Color(0xFFF5B841),        // Warm Amber
    accentDim = Color(0xFFB98A2C),
    onAccent = Color(0xFF0C0C0E),
    success = Color(0xFF4ADE80),
    danger = Color(0xFFF2555A),
    textPrimary = Color(0xFFF4F4F2),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF6B6B74),
    trackInactive = Color(0xFF2A2A30),
    isDark = true
)

// Studio Light (crisp, modern slate & amber)
val LightPalette = TimbreColorPalette(
    background = Color(0xFFF8F9FA),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F3F5),
    hairline = Color(0xFFE2E8F0),
    hairlineStrong = Color(0xFFCBD5E1),
    accent = Color(0xFFD97706),        // High-contrast warm amber
    accentDim = Color(0xFFB45309),
    onAccent = Color.White,
    success = Color(0xFF16A34A),
    danger = Color(0xFFDC2626),
    textPrimary = Color(0xFF0F172A),   // Slate-900
    textSecondary = Color(0xFF475569), // Slate-600
    textMuted = Color(0xFF94A3B8),     // Slate-400
    trackInactive = Color(0xFFE2E8F0),
    isDark = false
)

val LocalTimbreColors = staticCompositionLocalOf { DarkPalette }

// Context-aware dynamic color tokens
val Ink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.background

val Surface1: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.surface1

val Surface2: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.surface2

val Hairline: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.hairline

val HairlineStrong: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.hairlineStrong

val Amber: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.accent

val AmberDim: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.accentDim

val OnAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.onAccent

val Success: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.success

val Danger: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.danger

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.textMuted

val TrackInactive: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalTimbreColors.current.trackInactive
