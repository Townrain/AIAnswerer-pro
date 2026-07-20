package com.hwb.aianswerer.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════
//  Premium Luxury Palette
//  Rich indigo-violet primary, warm ivory light,
//  deep carbon dark, luminous accents
// ═══════════════════════════════════════════════

// ── Dark Accent — rich carbon with subtle warmth ──
val DarkAccent = Color(0xFF17141F)
val DarkAccentGradientEnd = Color(0xFF252030)

// ── Primary — vibrant indigo-violet with depth ──
val PremiumPrimary = Color(0xFF6C5CE7)
val PremiumPrimaryVariant = Color(0xFF8B7CF0)
val PremiumPrimaryLight = Color(0xFFB8B0F8)
val PremiumPrimaryContainer = Color(0xFFEDEBF9)
val PremiumOnPrimaryContainer = Color(0xFF1E1050)

// ── Secondary Accent — warm champagne gold ──
val AccentBronze = Color(0xFFBE8B5E)
val AccentGold = Color(0xFFD4A44B)

// ── Background & Surface — deeper contrast ──
val PremiumBgLight = Color(0xFFF2EFE6)
val PremiumBgLightEnd = Color(0xFFECE8DF)
val PremiumCardLight = Color(0xFFFFFFFF)
val PremiumSurfaceVariant = Color(0xFFF0EDF5)

// ── Glass — luminous frosted glass ──
val GlassWhite = Color(0xFFFFFFFF).copy(alpha = 0.70f)
val GlassWhiteStrong = Color(0xFFFFFFFF).copy(alpha = 0.82f)
val GlassWhiteBorder = Color(0xFFFFFFFF).copy(alpha = 0.88f)
val GlassDark = Color(0xFFFFFFFF).copy(alpha = 0.04f)
val GlassDarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// ── Glow Orbs — rich ambient warmth ──
val WarmGlow = Color(0xFFD4A44B).copy(alpha = 0.08f)
val IndigoGlow = Color(0xFF6C5CE7).copy(alpha = 0.08f)

// ── Primary Glow — luminous CTA gradient ──
val PrimaryGlow = Color(0xFF9B8DF7)
val PrimaryGlowEnd = Color(0xFFCBC4FF)

// ── Input — clean premium inputs ──
val InputBackground = Color(0xFFF5F3F8)
val InputBorder = Color(0xFFE8E5F0)
val InputBorderFocus = Color(0xFF6C5CE7)
val InputBgFocus = Color(0xFF6C5CE7).copy(alpha = 0.03f)

// ── State — vibrant Apple-style ──
val SuccessGreen = Color(0xFF34C759)
val SuccessGreenLight = Color(0xFF67D480)
val ErrorRed = Color(0xFFFF3B30)
val ErrorRedLight = Color(0xFFFF6961)

// ── Recording Mode — red accent ──
val RecordingRed = Color(0xFFFF3B30)
val RecordingRedDark = Color(0xFFD32F2F)

// ── Image Collection Mode — purple accent ──
val ImageCollectingPurple = Color(0xFF7C3AED)
val ImageCollectingPurpleDark = Color(0xFF5B21B6)
val ImageCollectingPurpleLight = Color(0xFFA855F7)

// ── Chip — refined segmented control ──
val ChipUnselected = Color(0xFF000000).copy(alpha = 0.04f)
val ChipSelected = Color(0xFF6C5CE7).copy(alpha = 0.10f)

// ── Toggle — Apple switch ──
val ToggleOff = Color(0xFFE5E5EA)

// ── Text — premium grays with warmth ──
val TextDark = Color(0xFF1C1B1F)
val TextSecondary = Color(0xFF6B6878)
val TextTertiary = Color(0xFFADA8BE)

// ── Shadow tint — warm violet depth ──
val ShadowPurple = Color(0xFF4A3CC8)

// ── Shadow alpha — centralized constants for shadow/glass system ──
const val ShadowSubtleAlpha = 0.04f
const val ShadowCardAlpha = 0.08f
const val ShadowElevatedAlpha = 0.08f
const val ShadowFloatingAlpha = 0.10f
const val ShadowFloatingDarkAlpha = 0.25f
const val ShadowButtonAlpha = 0.08f

// ── Surface Knob — white elements (toggle knob, slider thumb, radio dot) ──
val SurfaceKnob = Color(0xFFFFFFFF)
val SurfaceKnobShadow = Color(0xFF000000).copy(alpha = 0.08f)

// ── Card border — subtle separation in light mode ──
val CardBorderLight = Color(0xFF000000).copy(alpha = 0.04f)

// ── Button shadow — default shadow color for primary buttons ──
val ButtonShadowColor = Color(0xFF000000).copy(alpha = 0.15f)

// ── Answer highlight — green-tinted backgrounds for answer sections ──
val AnswerHighlightBg = Color(0xFF34C759).copy(alpha = 0.06f)
val AnswerHighlightBorder = Color(0xFF34C759).copy(alpha = 0.12f)
val CorrectOptionBg = Color(0xFF34C759).copy(alpha = 0.05f)
val CorrectOptionBorder = Color(0xFF34C759).copy(alpha = 0.10f)

// ── Explanation accent — purple-tinted backgrounds for analysis sections ──
val ExplanationBgLight = Color(0xFF6C5CE7).copy(alpha = 0.03f)
val ExplanationBgDark = Color(0xFF6C5CE7).copy(alpha = 0.06f)

// ── Dark Theme — deep carbon with luminous accents ──
val PremiumBgDark = Color(0xFF222230)
val PremiumSurfaceDark = Color(0xFFFFFFFF).copy(alpha = 0.06f)
val PremiumSurfaceDarkBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f)

val TextDarkPrimary = Color(0xFFFFFFFF).copy(alpha = 0.93f)
val TextDarkSecondary = Color(0xFFFFFFFF).copy(alpha = 0.58f)
val TextDarkTertiary = Color(0xFFFFFFFF).copy(alpha = 0.32f)

// ── M3 Compatibility ──

// Light
val LightPrimary = PremiumPrimary
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = PremiumPrimaryContainer
val LightOnPrimaryContainer = PremiumOnPrimaryContainer

val LightSecondary = Color(0xFF6B6878)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8E5F0)
val LightOnSecondaryContainer = Color(0xFF1C1B1F)

val LightTertiary = Color(0xFF8E8E93)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF5F3F8)
val LightOnTertiaryContainer = Color(0xFF1C1B1F)

val LightError = Color(0xFFFF3B30)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = PremiumBgLight
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0xFFF2F0F6)
val LightOnSurfaceVariant = Color(0xFF6B6878)
val LightOutline = Color(0xFFADA8BE)
val LightOutlineVariant = Color(0xFFD8D5E0)

val LightScrim = Color(0xFF000000)
val LightInverseSurface = Color(0xFF1C1B1F)
val LightInverseOnSurface = Color(0xFFF5F3F8)
val LightInversePrimary = Color(0xFFB8B0F8)

// Dark
val DarkPrimary = Color(0xFFB8B0F8)
val DarkOnPrimary = Color(0xFF1E1050)
val DarkPrimaryContainer = Color(0xFF3A3570)
val DarkOnPrimaryContainer = Color(0xFFEDEBF9)

val DarkSecondary = Color(0xFFADA8BE)
val DarkOnSecondary = Color(0xFF1C1B1F)
val DarkSecondaryContainer = Color(0xFF3A3840)
val DarkOnSecondaryContainer = Color(0xFFE8E5F0)

val DarkTertiary = Color(0xFF8E8E93)
val DarkOnTertiary = Color(0xFFFFFFFF)
val DarkTertiaryContainer = Color(0xFF3A3840)
val DarkOnTertiaryContainer = Color(0xFFF5F3F8)

val DarkError = Color(0xFFFF6961)
val DarkOnError = Color(0xFF410002)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = PremiumBgDark
val DarkOnBackground = Color(0xFFF5F3F8)
val DarkSurface = PremiumBgDark
val DarkOnSurface = Color(0xFFF5F3F8)
val DarkSurfaceVariant = Color(0xFF1A1A20)
val DarkOnSurfaceVariant = Color(0xFFD8D5E0)
val DarkOutline = Color(0xFF6B6878)
val DarkOutlineVariant = Color(0xFF3A3840)

val DarkScrim = Color(0xFF000000)
val DarkInverseSurface = Color(0xFFF5F3F8)
val DarkInverseOnSurface = Color(0xFF1C1B1F)
val DarkInversePrimary = Color(0xFF6C5CE7)
