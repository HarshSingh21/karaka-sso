---
name: karaka-tokens
description: The finalized visual design system for Karaka (every product in the suite, plus the shared login page) — color tokens, typography, shadows. Load this before styling any new screen or product in this repo so colors/fonts/spacing stay identical across apps instead of drifting per-build.
---

# Karaka — design tokens

This is the **single finalized theme** for the whole Karaka product suite. Every new
screen — in any product, the login page, or anything else added later — reuses these
exact tokens. Don't invent a new palette or font pairing per app; if a real gap shows
up, extend `packages/tokens/theme.css` and this skill, don't fork silently.

**Always build against `packages/tokens/theme.css` directly** — it's the source of
truth (tokens, `@font-face` blocks, light/dark theme handling, base element styles) and
is fully commented in place. Read it before styling anything; don't re-derive the
system from memory or from this doc's summary below.

## How each app consumes this

Every Karaka app is a **self-contained single HTML file** — no build step, no server,
opens directly via `file://`. So you never `<link>` this stylesheet at runtime.
Instead:

1. Read `packages/tokens/theme.css` and copy the `@font-face` blocks + the token/theme
   blocks verbatim into the new file's own `<style>`.
2. Copy whichever component classes you need from [[karaka-components]].
3. Base64-encode the font data from `packages/tokens/fonts/*.b64` (already generated —
   `plus-jakarta-sans.b64` and `jetbrains-mono.b64`) into the `@font-face`
   `src: url(data:font/woff2;base64,...)`. Source binaries (if you need to regenerate)
   are `packages/tokens/fonts/*-variable.woff2`.

The login screen (`apps/login/`) uses its own separate, deliberately single-theme
token set instead of the shared light/dark one — see [[karaka-shell]] for why, and
copy from `apps/login/index.template.html` directly rather than `theme.css` when
building anything login-adjacent.

## Quick reference (see theme.css for the real values)

- **Plus Jakarta Sans** — the only UI/body typeface, one variable-font file covering
  weights 300–800. **JetBrains Mono** — tabular/code content only (amounts, IDs,
  timestamps), via the `.mono` class. Never link Google Fonts by URL — always inlined.
- Color tokens are semantic, not literal — every component reads `var(--token)`, never
  a raw hex: surfaces (`--bg/--surface/--surface-2/--surface-3`), text
  (`--ink/--ink-soft/--ink-faint`), borders, the two brand colors (`--accent` cobalt,
  `--info` teal), semantic status (`--warn/--danger/--success`, kept separate from
  `--accent`), shadows, and the invariant `--rail-*` sidebar palette.
- Light/dark/system theming is already fully handled in `theme.css` — when adding a
  new token, follow the exact pattern already there for existing tokens rather than
  improvising a new one.

## Figma / design-tool import

`packages/tokens/karaka-tokens.json` — full token export (colors, shadows, type scale,
radius) for design tools. `packages/tokens/karaka-tokens.penpot.json` — a slimmed
variant (flat colors + radius only) built because Penpot's importer rejected the fuller
file's composite shadow arrays / `$themes` block. Regenerate both from `theme.css` if
tokens change — never hand-edit the JSON first.

## Rules

- No emoji as icons — inline SVG or simple glyph characters only.
- Respect `prefers-reduced-motion` for every animation.
- See [[karaka-components]] for how these tokens compose into actual components, and
  [[karaka-shell]] for the cross-app shell (rail nav, login, product picker).
