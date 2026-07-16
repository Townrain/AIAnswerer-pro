---
version: v2.0
name: claude-warm-design
description: Anthropic/Claude brand-inspired theme for AIAnswerer. Light mode captures the warm cream editorial aesthetic of anthropic.com — tinted canvas, dark-ink typography, coral accents, zero-shadow minimalism. Dark mode channels the deep terminal aesthetic of Claude Code — dark developer surfaces, coral-red highlights, cream-tinted text, code-editor syntax warmth. Both modes share a single design DNA: warm over cool, editorial over generic, humanist over corporate.
---

## Overview

AIAnswerer is an AI-powered quiz answering Android app (Kotlin + Jetpack Compose + Material3). Its theme system uses a custom `Th` data class with 25+ color slots, 5 background gradient stops, glass morphism, and a built-in theme manager.

**Claude Warm (克劳德暖)** is the 7th built-in theme — directly inspired by Anthropic's brand design system as deployed on [anthropic.com/claude/opus](https://www.anthropic.com/claude/opus) (light) and [claude.com/product/claude-code](https://claude.com/product/claude-code) (dark).

### Design DNA
- **Cream canvas** (`#FAF9F5`): The signature Anthropic page floor — tinted, warm, never pure white. This single color difference from standard white backgrounds creates the brand's "editorial publication" feel.
- **Coral primary** (`#CC785C` light / `#D97757` dark): The Anthropic coral is warm, slightly muted, deliberately not cyan/blue. It's the Claude logo spike color and the most recognizable Anthropic design element.
- **Dark ink text** (`#141413`): Warm dark, slightly off-pure-black. Used for all headlines and body text on cream. Paired with `#6C6A64` for muted/secondary text.
- **Dark terminal surfaces** (`#181715`): The Claude Code dark aesthetic — code editor deep backgrounds, elevated surfaces at `#1F1E1B`, with cream-tinted `#FAF9F5` text (on-dark).
- **Zero-shadow minimalism**: The Anthropic design uses color-block contrast (cream vs dark) for depth instead of drop shadows. Borders are subtle `#E6DFD8` hairline, components use flat backgrounds.
- **Accent amber** (`#D4A44B`): Used sparingly for secondary highlights — the warm gold counterpoint to coral.
- **Teal accent** (`#5DB8A6`): Anthropic's secondary accent, used for terminal status indicators and subtle product surface differentiation in dark mode.

### Surface Hierarchy
1. **Cream canvas** (`bg1→bg5` gradient): Default body floor
2. **Cream card surfaces** (`#EFE9DE` at bg3 center): Slightly warmer than canvas for cards
3. **Dark terminal surfaces** (`#181715` in dark mode): Code-editor product mockups
4. **Coral accents** (`#CC785C`): Primary CTAs, floating button, links
5. **Deep void** (`#0D0C0A` in dark mode): Darkest background point

---

## Colors

### Claude Warm — Light Mode (克劳德暖 · 亮色)

> **Brand source**: [anthropic.com/claude/opus](https://www.anthropic.com/claude/opus) — Canvas `#FAF9F5`, Text `#141413`, Primary coral, CTA dark-on-cream

#### Background Gradient (Warm Cream Canvas)

| Slot | Hex | Description |
|------|-----|-------------|
| `bg1` | `#FAF9F5` | Canvas cream — the Anthropic signature page floor |
| `bg2` | `#F5F0E8` | Surface soft — warm off-white transition |
| `bg3` | `#EFE9DE` | Surface card — warmer center, card-background tone |
| `bg4` | `#F5F0E8` | Surface soft |
| `bg5` | `#FAF9F5` | Canvas cream |

#### Primary — Anthropic Coral

| Slot | Hex | Description |
|------|-----|-------------|
| `p` | `#CC785C` | **Anthropic coral** — CTA background, floating button, brand accent |
| `pe` | `#E0A090` | Lighter coral — button gradient end |
| `pd` | `#A9583E` | Darker coral — pressed/active state |
| `pc` | `#F5E8E0` | Coral container — warm tinted answer highlight |
| `opc` | `#3D1A0A` | On primary container — deep warm brown text |

#### Accent

| Slot | Hex | Description |
|------|-----|-------------|
| `ac` | `#D4A44B` | **Accent amber** — Anthropic secondary accent (rare, for badges/category markers) |
| `ua` | `#CC785C` | UI accent — matches primary coral |
| `ual` | `#E0A090` | UI accent light — lighter coral for hover/focus |

#### Text

| Slot | Hex | Description |
|------|-----|-------------|
| `ob` | `#141413` | **Warm ink** — Anthropic's near-black text; slightly warm, never pure `#000` |
| `osv` | `#6C6A64` | Muted — secondary text, captions, breadcrumbs |

#### Glass & Header

| Slot | ARGB | Description |
|------|------|-------------|
| `gt` | `0x0DFFFFFF` | Glass top — subtle white mist |
| `gb` | `0xF0FFFFFF` | Glass border — near-white hairline |
| `gdp` | `0x08FFFFFF` | Glass bottom — near-transparent |
| `ht` | `0xC0FFF8F0` | Header top — warm cream-tinted glass |
| `hdp` | `0x8DFFF0E0` | Header bottom — slightly deeper warm tint |

#### Semantic

| Slot | Hex | Description |
|------|-----|-------------|
| `ok` | `#5DB872` | **Anthropic success green** — checkmarks, correct indicators |
| `to` | `#E6DFD8` | Track off — matches Anthropic hairline color |
| `err` | `#C64545` | **Anthropic error red** — warm, slightly muted |
| `w` | `#FFFFFF` | White |

---

### Claude Warm — Dark Mode (克劳德暖 · 暗色)

> **Brand source**: [claude.com/product/claude-code](https://claude.com/product/claude-code) — Dark terminal surfaces `#181715`, Claude logo coral `#D97757`, cream text `#FAF9F5`

#### Background Gradient (Deep Terminal)

| Slot | Hex | Description |
|------|-----|-------------|
| `bg1` | `#0D0C0A` | Void black — deepest dark point |
| `bg2` | `#181715` | Surface dark — Anthropic's standard dark surface |
| `bg3` | `#1F1E1B` | Surface dark soft — slightly elevated, code-block background |
| `bg4` | `#181715` | Surface dark |
| `bg5` | `#0D0C0A` | Void black |

#### Primary — Claude Logo Coral

| Slot | Hex | Description |
|------|-----|-------------|
| `p` | `#D97757` | **Claude coral** — matches Claude logo spike accent; glows against dark surfaces |
| `pe` | `#E8A09A` | Lighter coral — gradient end |
| `pd` | `#C56A4A` | Darker coral — pressed state |
| `pc` | `#3D1A17` | Dark coral container |
| `opc` | `#FAF0E8` | Light warm on container text |

#### Accent

| Slot | Hex | Description |
|------|-----|-------------|
| `ac` | `#5DB8A6` | **Anthropic teal** — secondary accent, terminal status indicators |
| `ua` | `#D97757` | UI accent — matches primary coral |
| `ual` | `#E8A09A` | UI accent light |

#### Text

| Slot | Hex | Description |
|------|-----|-------------|
| `ob` | `#FAF9F5` | **On-dark text** — cream-tinted white, echoes canvas tone |
| `osv` | `#A09D96` | On-dark soft — secondary text on dark surfaces |

#### Glass & Header (Dark)

| Slot | ARGB | Description |
|------|------|-------------|
| `gt` | `0x08FFFFFF` | Glass top — very subtle |
| `gb` | `0x12FFFFFF` | Glass border — faint hairline |
| `gdp` | `0x04FFFFFF` | Glass bottom |
| `ht` | `0x0AFFFFFF` | Header top |
| `hdp` | `0x06FFFFFF` | Header bottom |

#### Semantic (Dark)

| Slot | Hex | Description |
|------|-----|-------------|
| `ok` | `#5DB872` | Success green |
| `to` | `#2A2825` | Track off — dark |
| `err` | `#C64545` | Error red |
| `w` | `#FFFFFF` | White |

---

## Typography

Claude Warm uses the existing AIAnswerer typography (`Type.kt`), which already aligns well with Anthropic's typographic principles:

- **Display/headlines**: `FontFamily.Serif` at Black (900) or Bold (700) — Anthropic uses Anthropic Serif for editorial headings; system serif is the best Android approximation.
- **Body/UI**: `FontFamily.SansSerif` at Normal (400) to SemiBold (600) — Anthropic uses Anthropic Sans; system sans provides the humanist, readable quality.
- **Code**: `FontFamily.Monospace` — matches Claude Code's terminal and code-block aesthetic.

### Key Typographic Values

| Token | Size | Weight | Use in Claude Warm |
|-------|------|--------|--------------------|
| `displayLarge` | 40sp | 900 | Page title ("AI答题助手") |
| `headlineLarge` | 22sp | 700 | Card titles |
| `bodyLarge` | 16sp | 400 | Primary reading text |
| `bodyMedium` | 14sp | 400 | Secondary descriptions |
| `labelLarge` | 13sp | 600 | Button text |

---

## Spacing & Shape

### Component Constants (unchanged from `SandboxTheme.kt`)

| Token | Value | Description |
|-------|-------|-------------|
| `BtnR` | 32dp | Button corner radius (matches Anthropic's pill-style CTAs) |
| `CardR` | 24dp | Card corner radius |
| `ChipR` | 20dp | Chip/filter corner radius |
| `CardPad` | 24dp | Internal card padding |

### Anthropic Alignment
- Anthropic buttons use 8px radius — our 32dp on mobile creates a similarly soft, approachable pill shape relative to screen size.
- Anthropic cards have generous internal padding (32px) — our `CardPad` of 24dp on mobile is comparably spacious.
- The Anthropic design uses minimal shadows — our `shadowSubtle` (1dp) aligns well. Elevated states use `shadowCard` (4dp) sparingly.

---

## Component Styling Guide

### Floating Window Button
- **Shape**: Circle, 40dp diameter
- **Background**: `primaryGradient()` — coral (`#CC785C` → `#E0A090` in light, `#D97757` → `#E8A09A` in dark)
- **Icon**: White, 20dp
- **Shadow**: `shadowButton` — subtle coral-tinted shadow
- **Note**: The coral floating button is the app's most direct Anthropic brand signal — it's the equivalent of Anthropic's coral CTA

### Answer Cards
- **Surface**: `glassSurface()` — frosted cream in light, subtle glass in dark
- **Header**: Warm tinted glass gradient
- **Answer highlight**: `pc` (coral container `#F5E8E0` light / `#3D1A17` dark)
- **Correct answer**: `ok` (green `#5DB872`) background at 6% alpha, border at 12%
- **Close/stop button**: Standard close, turns `err` (red) during active requests

### Home Page
- **Background**: 5-stop cream gradient (`bg1→bg5`) — the Anthropic canvas
- **Title section**: `ht→hdp` gradient header
- **Cards**: `glassSurface()` — Anthropic's editorial card treatment
- **CTA button**: `primaryGradient()` coral with `shadowButton`

### Settings
- **Toggle switches**: `ua` (coral) when on, `to` (hairline `#E6DFD8`) when off
- **Chip selection**: `pc` background, `opc` text when active
- **Text inputs**: White background, 1dp `gb` border, `ua` focus ring

### Dark Mode Specific
- **Code-like answer cards**: Dark `glassSurfaceDark()` with `#FAF9F5` (cream) text — channels Claude Code's terminal aesthetic
- **Status indicators**: `ac` (teal `#5DB8A6`) for "active connection" dots — Anthropic's terminal status pattern
- **Recording mode**: `err` (red) indicator — stands out against the dark terminal background

---

## Do's and Don'ts

### Do
- ✅ Anchor every page on the cream canvas (`#FAF9F5` gradient). Pure white reads as "any other app"; the warm tint is the Anthropic brand differentiator.
- ✅ Use coral (`#CC785C` / `#D97757`) for primary CTAs and the floating window button. It's the Claude brand's most recognizable color.
- ✅ Reserve accent amber (`#D4A44B`) and teal (`#5DB8A6`) for rare secondary highlights — they're sprinkles, not the main dish.
- ✅ Use dark ink (`#141413`) for body text on light backgrounds. Never pure `#000000`.
- ✅ Use cream-tinted white (`#FAF9F5`) for text on dark surfaces. The on-dark color should echo the canvas tone.
- ✅ Keep shadows minimal. Depth should come from cream-vs-dark color blocking and glass opacity, not drop shadows.
- ✅ Use `#E6DFD8` (hairline) for borders on light surfaces. Borders should feel like one elevation step, not ink lines.
- ✅ In dark mode, use the deep surface hierarchy: `#0D0C0A` (deepest) → `#181715` (surfaces) → `#1F1E1B` (elevated).

### Don't
- ❌ Don't use pure white (`#FFFFFF`) as the page background. Cream (`#FAF9F5`) is the brand.
- ❌ Don't overuse coral. It's the CTA color, not a decorative accent for every element. One coral element per view is enough.
- ❌ Don't use cool blues or saturated cyans as primary. Anthropic deliberately counters the AI industry's default blue.
- ❌ Don't add heavy drop shadows. Anthropic's design system is flat + color-block. Shadows are 1-4dp, never 12dp+.
- ❌ Don't use pure black (`#000000`) in dark mode. `#0D0C0A` has subtle warmth; pure black would break the Anthropic dark-surface feel.
- ❌ Don't repeat the same surface mode in consecutive bands. Alternate cream → cream-card → dark-surface for pacing.
- ❌ Don't use `#E6DFD8` hairline as a deep border. It's intentionally subtle — if you need visible separation, use a 1-darker cream surface instead.

---

## Implementation Notes

### Files Modified
- `app/src/main/java/com/hwb/aianswerer/ui/theme/ThemePreset.kt` — added `CLAUDE_WARM` constant, `BUILT_IN` map entry, `ClaudeWarmLight` and `ClaudeWarmDark` Th definitions.
- `DESIGN.md` — this file (design documentation).

### No Changes Required In
- `ThemeManager.kt` — dynamically discovers themes from `ThemePresets.BUILT_IN`.
- `SandboxTheme.kt` — uses `ThemeManager.getCurrentTheme()` for resolution.
- `Theme.kt`, `Color.kt`, `Type.kt`, `Glass.kt`, `Shadow.kt`, `Spacing.kt` — unchanged; Claude Warm colors work within the existing infrastructure.

### Testing Checklist
- [ ] Theme "克劳德暖" appears in settings → theme selector (7th option)
- [ ] Light mode: cream canvas gradient visible on home page
- [ ] Light mode: coral buttons and floating window button
- [ ] Dark mode: deep terminal background (`#0D0C0A → #181715`)
- [ ] Dark mode: cream-tinted text (`#FAF9F5`) readable on dark surfaces
- [ ] Glass cards have correct cream-tinted opacity in both modes
- [ ] Coral primary visible on status indicators and switches
- [ ] Success green (`#5DB872`) distinct from primary coral
- [ ] Error red visible in both light and dark modes
- [ ] Recording mode indicator stands out against dark background

### Brand Color Reference

| Color | Hex | Source |
|-------|-----|--------|
| Canvas cream | `#FAF9F5` | anthropic.com - page background |
| Warm ink | `#141413` | anthropic.com - body text |
| Coral | `#CC785C` | Anthropic design system primary |
| Claude coral | `#D97757` | Claude logo spike accent |
| Cream card | `#EFE9DE` | Anthropic surface-card |
| Dark surface | `#181715` | Anthropic surface-dark |
| Dark elevated | `#1F1E1B` | Anthropic surface-dark-soft |
| Success green | `#5DB872` | Anthropic success color |
| Accent amber | `#D4A44B` | Anthropic accent-amber |
| Teal | `#5DB8A6` | Anthropic accent-teal |
| Hairline | `#E6DFD8` | Anthropic hairline border |
| Error red | `#C64545` | Anthropic error color |
| On-dark text | `#FAF9F5` | Anthropic on-dark |
| On-dark soft | `#A09D96` | Anthropic on-dark-soft |
