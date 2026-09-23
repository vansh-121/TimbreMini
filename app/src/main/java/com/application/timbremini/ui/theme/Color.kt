package com.application.timbremini.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * TimbreMini palette — a warm, neutral "studio" dark theme.
 *
 * One confident accent (amber) carries every primary action; everything else is
 * a calm neutral ramp. No multi-hue gradients, no glow — contrast and spacing do
 * the work instead.
 */

// Neutral ramp (very slightly warm, near-black to hairline)
val Ink = Color(0xFF0C0C0E)          // app background
val Surface1 = Color(0xFF151517)     // cards / primary surface
val Surface2 = Color(0xFF1E1E22)     // elevated / inset controls
val Hairline = Color(0xFF2A2A30)     // 1dp borders & dividers
val HairlineStrong = Color(0xFF3A3A42)

// Single accent
val Amber = Color(0xFFF5B841)        // primary actions, playhead, active states
val AmberDim = Color(0xFFB98A2C)     // pressed / de-emphasized accent

// Semantic
val Success = Color(0xFF4ADE80)
val Danger = Color(0xFFF2555A)

// Text
val TextPrimary = Color(0xFFF4F4F2)  // warm white
val TextSecondary = Color(0xFFA1A1AA)
val TextMuted = Color(0xFF6B6B74)

// Track for sliders / progress
val TrackInactive = Color(0xFF2A2A30)
