---
name: Whisperbook
description: A calm offline story-listening experience crafted from moonlit paper theatre.
colors:
  midnight-paper: "#10243D"
  raised-navy: "#183451"
  action-blue: "#355A83"
  parchment: "#E1CBAA"
  parchment-light: "#F7EDD7"
  ink: "#1D1B18"
  muted-ink: "#526174"
  antique-gold: "#D6B06A"
  terracotta: "#9B493F"
  fox-orange: "#A45A2A"
  pale-blue: "#AEBFD2"
typography:
  display:
    fontFamily: "Cormorant Garamond, Georgia, serif"
    fontSize: "36sp"
    fontWeight: 600
    lineHeight: 1.1
  title:
    fontFamily: "Libre Baskerville, Georgia, serif"
    fontSize: "23sp"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "Libre Baskerville, Georgia, serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: 1.35
  label:
    fontFamily: "Inter, system-ui, sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: 1.3
rounded:
  small: "6dp"
  medium: "10dp"
  large: "14dp"
  panel: "18dp"
  full: "999dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.raised-navy}"
    textColor: "{colors.parchment-light}"
    typography: "{typography.title}"
    rounded: "{rounded.medium}"
    padding: "14dp 24dp"
    height: "52dp"
  button-accent:
    backgroundColor: "{colors.terracotta}"
    textColor: "{colors.parchment-light}"
    typography: "{typography.title}"
    rounded: "{rounded.medium}"
    padding: "14dp 24dp"
    height: "52dp"
  paper-panel:
    backgroundColor: "{colors.parchment}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "16dp"
  bottom-navigation:
    backgroundColor: "{colors.midnight-paper}"
    textColor: "{colors.parchment-light}"
    rounded: "{rounded.large}"
    height: "72dp"
---

# Design System: Whisperbook

## 1. Overview

**Creative North Star: "The Moonlit Paper Theatre"**

Whisperbook feels like a small handcrafted stage opened at bedtime: layered midnight paper, warm parchment, terracotta curtains, antique-gold trim, and story illustrations built from visible cut-paper planes. The scene is a person holding a phone in one hand in a dim room, already inside a story; low luminance and stable geometry are required, not optional decoration.

Functional text, controls, progress, and accessibility semantics remain native Compose content. High-detail papercraft appears in reusable transparent assets, textures, nine-patch skins, and decorative layers. The system rejects generic dashboards, neon AI styling, glassmorphism, childish cartoon treatment, and bitmap-only fake interfaces.

**Key Characteristics:**

- Moonlit navy surfaces with warm parchment reading areas.
- Restrained terracotta and antique-gold accents.
- Tactile, layered depth around familiar controls.
- Adult storybook typography with readable metadata.
- Calm spacing, large targets, and explicit offline reassurance.

## 2. Colors

The palette is a committed nocturnal blue field balanced by warm paper and quiet theatrical accents.

### Primary

- **Midnight Paper** (`#10243D`): global background, player shell, and navigation.
- **Raised Navy** (`#183451`): primary actions, selected navigation, and elevated blue paper.
- **Action Blue** (`#355A83`): narrator identity, progress, focus, and interactive emphasis.

### Secondary

- **Terracotta Curtain** (`#9B493F`): primary import actions, Elara identity, and theatre curtains.
- **Fox Orange** (`#A45A2A`): Fox identity and warm story accents.
- **Antique Gold** (`#D6B06A`): rims, rules, focus outlines, and rare decorative glints.

### Neutral

- **Parchment Light** (`#F7EDD7`): high-contrast text over navy and light paper highlights.
- **Parchment** (`#E1CBAA`): reading panels and tactile controls.
- **Story Ink** (`#1D1B18`): primary text on paper.
- **Muted Ink** (`#526174`): secondary metadata.
- **Moonlit Steel** (`#AEBFD2`): inactive blue ornaments and secondary routes.

**The Character Constancy Rule.** Narrator is blue, Elara is burgundy, and Fox is orange across cast, player, and read-along screens. Labels and portraits always accompany color.

## 3. Typography

**Display Font:** Cormorant Garamond SemiBold (with Georgia and serif fallback)
**Body Font:** Libre Baskerville (with Georgia and serif fallback)
**Label Font:** Inter (with Android system sans fallback)

**Character:** Display text is literary and theatrical. Reading text is sturdy and book-like. Compact metadata uses a quiet sans serif so technical details remain effortless to scan.

### Hierarchy

- **Display** (600, 36sp, 40sp): wordmark and major screen titles.
- **Headline** (600, 32sp, 36sp): book and chapter hero titles.
- **Title** (700, 23sp, 28sp): sections and high-priority actions.
- **Body** (400, 17sp, 23sp): standard copy and passage metadata.
- **Reader** (400, 24sp, 30sp): synchronized chapter passages.
- **Label** (600, 14sp, 19sp): status, progress, privacy, and control metadata.

**The Native Metrics Rule.** Bundle fonts and lock their metrics. Do not depend on manufacturer fonts for golden layouts.

## 4. Elevation

Depth is structural: overlapping paper, inset tracks, raised buttons, and theatre layers communicate hierarchy. Use soft midnight-tinted contact shadows rather than floating Material cards. Texture overlays remain between 8 and 14 percent opacity.

### Shadow Vocabulary

- **Paper Contact** (`0 3dp 8dp rgba(8,21,38,0.32)`): parchment panels and list rows.
- **Raised Control** (`0 5dp 10dp rgba(8,21,38,0.36)`): transport buttons and selected tabs.
- **Hero Stage** (`0 8dp 18dp rgba(8,21,38,0.34)`): theatre and large illustration frames.

**The Attached Paper Rule.** Every shadow must make an element feel physically attached to the stage. Nothing floats like glass.

## 5. Components

### Buttons

- **Shape:** tactile cut-paper rectangles at 10dp; circular transport controls at 999dp.
- **Primary:** Raised Navy with parchment text, 52dp height, antique-gold 1dp rim.
- **Accent:** Terracotta with parchment text for the main import or background action.
- **Pressed / Focus:** reduce elevation on press; use a 2dp antique-gold focus ring with 2dp offset.
- **Disabled:** retain the shape, use reduced chroma and 50 percent opacity.

### Chips

- **Style:** small parchment tabs with ink text and a 1dp warm outline.
- **State:** selected chips use Raised Navy and Parchment Light; include a check or label change, never color alone.

### Cards / Containers

- **Corner Style:** 14 to 18dp for native geometry; use alpha or nine-patch skins for notched, clipped, or arched silhouettes.
- **Background:** Parchment with subtle texture and inner highlight.
- **Shadow Strategy:** Paper Contact by default; stronger only for the active read-along passage.
- **Border:** 1dp muted gold-brown plus a light inner rim.
- **Internal Padding:** 16 to 20dp with 8 to 12dp between content groups.

### Inputs / Fields

- **Style:** inset parchment field, 10dp radius, Story Ink value, Muted Ink helper text.
- **Focus:** Action Blue outline plus an explicit label state.
- **Error / Disabled:** icon and text accompany color; preserve AA contrast over texture.

### Navigation

The bottom bar is 64 to 76dp tall with Library, Listen, and Settings. The selected destination rises in an arched navy-and-gold tab. Processing and Voice Cast intentionally omit the bottom bar. Top back actions remain conventional and TalkBack-labeled.

### Speaker Passage

Each passage uses a speaker label, portrait or audio icon, a stable colored rail, and a pale paper tint. The active passage adds “Now speaking,” a waveform, a progress rule, and stronger elevation. Manual scrolling pauses automatic following until explicitly resumed.

## 6. Do's and Don'ts

### Do:

- **Do** use the 360×640 dp canonical viewport for golden screenshots at 941×1672 pixels.
- **Do** keep functional text, buttons, sliders, and switches as native Compose content.
- **Do** use transparent papercraft assets and nine-patch skins for ornamental silhouettes.
- **Do** maintain 48dp touch targets, AA contrast, TalkBack order, and color-independent state cues.
- **Do** preserve the stable character palette on every screen.

### Don't:

- **Don't** ship a full-screen screenshot with invisible tap targets instead of accessible native controls.
- **Don't** use a generic Material dashboard with flat, interchangeable cards.
- **Don't** use neon, purple gradients, glassmorphism, or futuristic AI styling.
- **Don't** turn the interface into a childish cartoon or game-like reward system.
- **Don't** require an account, cloud upload, or first-launch model download.
- **Don't** communicate the active speaker using color alone.
- **Don't** stretch papercraft borders or bake functional text into images.
