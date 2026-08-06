# Product

## Register

product

## Users

People who want to listen to story-driven PDF and EPUB books on an Android phone without uploading their library or depending on a network connection. The primary context is one-handed listening at home, in transit, or before sleep, often in a dim and quiet environment. The core job is to import a book, let the app prepare it locally, and move fluidly between listening and synchronized reading.

## Product Purpose

Whisperbook turns PDF and EPUB files into chapter-based offline audiobooks. It extracts chapters, divides prose into passages, attributes quoted dialogue to recurring characters with deterministic heuristics, and uses explicit textual age/gender cues plus first-person narration evidence to improve the initial local voice cast. It synthesizes audio on the phone and keeps playback synchronized with a color-and-label read-along view. Success means that the complete import-to-listen flow works in airplane mode, survives process death, and remains understandable and editable when automatic attribution is uncertain.

## Brand Personality

Calm, tactile, enchanted. Whisperbook should feel like opening a carefully crafted adult fairytale book in a quiet room: warm and imaginative without becoming childish, theatrical without becoming busy, and reassuring about privacy without sounding technical or defensive.

## Anti-references

- A generic Material dashboard with flat, interchangeable cards.
- Neon, purple-gradient, glassmorphic, or futuristic “AI app” styling.
- Childish cartoon UI, game-like rewards, or noisy fantasy ornament.
- Cloud-first flows, account gates, download-at-first-use promises, or hidden network dependence.
- Full-screen screenshot facsimiles with invisible tap targets instead of accessible native controls.
- Speaker highlighting that relies on color alone.

## Design Principles

1. **Offline trust is visible.** The app states what is local, works without an account, and has no hidden internet dependency.
2. **The story stays primary.** Decoration frames the content; it never competes with passages, chapters, or playback controls.
3. **Tactile but familiar.** Papercraft depth gives personality while controls keep conventional Android behavior, semantics, and hit targets.
4. **Automation remains editable.** Character detection and voice assignment expose confidence and allow correction instead of pretending to be perfect.
5. **Calm survives every state.** Loading, errors, empty states, and long-running preparation use stable layouts, plain language, and restrained motion.

## Accessibility & Inclusion

Target WCAG 2.2 AA contrast and native Android accessibility semantics. All touch targets are at least 48 dp. Speaker identity uses a name, portrait or icon, and color; color is never the only signal. Support TalkBack, reduced motion, font scaling through 200 percent with reflow, explicit slider values, descriptive playback state, and an app-level larger-text preference. Decorative imagery is excluded from the accessibility tree.
