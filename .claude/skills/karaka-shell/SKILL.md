---
name: karaka-shell
description: How Karaka's products relate to each other — the shared login page, product picker, per-product brand marks, and cross-app navigation conventions. Load this when building the login page, adding a new suite product, or wiring cross-links between products.
---

# Karaka — multi-product shell

Karaka is a suite of peer products sharing one login and one visual system — see
[[karaka-tokens]] and [[karaka-components]] for the actual tokens/components they
share. This skill covers what's specific to the *suite* level: the login page, the
product picker, and how products refer to each other.

## Naming: Karaka (the suite) vs. a customer workspace (e.g. "Opal")

**Karaka is the product/suite name** — the wordmark on the login page, the brand every
product sits under. It comes from Sanskrit grammar: a *kāraka* is "the agent that
brings the action about," the noun–verb relation everything else in a sentence relates
back to — the same role Karaka plays across the suite.

A customer's own org/workspace name (e.g. **"Opal"**) is a *separate*, smaller piece of
context — shown as a quiet `.org-tag` pill next to the Karaka wordmark on the sign-in
and picker screens (see `apps/login/app.js`'s `signinView()`/`pickerView()`), not as a
second competing logo. Don't conflate the two: Karaka never changes per customer, the
org tag does.

The brand banner (mark + wordmark + tagline, navy→teal gradient matching the login
hero) lives at `packages/brand/karaka-banner.svg` — use it wherever the suite needs a
full lockup instead of just the mark (docs, README headers, etc.).

## Login page

**`apps/login/`** is the canonical split-hero login. Single self-contained HTML file,
same theme tokens as every other app, two states in one page (no real backend — this is
a static prototype):

1. **Sign-in view** — a two-panel `.shell`: a fixed-dark hero panel (own gradient, not
   themed — see "Hero panel" below) on one side, the sign-in `.glass` card on a shared
   page ground on the other. Suite mark + short generic line ("Sign in to continue") —
   no invented company name as a headline. Fields use the floating-label pattern
   (`.field-float`/`.input-float`, documented in [[karaka-components]]), not the plain
   `.input`/`.field-label` used in dense admin screens.
2. **Product picker** — shown after submit (client-side only, no real auth check), full
   width, no hero panel (nothing to straddle). One tile per product: product mark,
   name, one-line description, and either a real link (if built) or the `SOON`
   treatment reused from [[karaka-components]]'s rail-soon pattern rather than a
   separately-invented "disabled tile" style.

### Hero panel — deliberately fixed-dark, not themed

The hero is a permanent navy→teal gradient (`#0A1120 → #122A4A → #0F4A50 → #0F8B8D`,
155deg), independent of light/dark theme — the same "fixed surface" exception the rail
nav gets in [[karaka-tokens]]. Its own text (`h1`, `p`) is painted with literal light
values (`#FFFFFF`, `#5FE0D2` highlight, `#AFC1DB` body), never `--foreground`/`--muted`
— those are the page's light-mode text tokens and are the wrong surface here. Populate
it with a handful of `.float-ic` inline-SVG icons literal to the suite's domain
(document/chart/shield/grid/spark/clock), not the login page's own invented imagery —
gentle `float` keyframe, `prefers-reduced-motion`-safe.

### Seam: cut, not a border

The hero and form panel read as **one composed page**, not two boxes glued together:

- Both panels sit on one shared page-level ground: `body { background: var(--surface) }`
  — never give the form side its own background fill.
- The hero is *shaped* with `clip-path: polygon(0 0, 100% 0, 82% 100%, 0 100%)` (an
  angled cut, gated behind a `min-width:760px` media query — a diagonal makes no sense
  once the layout stacks), so the shared ground shows through the wedge as a deliberate
  transition rather than a hard vertical line between two colors.
- `box-shadow` does **not** follow `clip-path` — use `filter: drop-shadow(...)` on the
  hero instead, or the shadow silently renders as a rectangle behind the clipped shape.
- The sign-in `.glass` card is pulled slightly onto the seam (`margin-left:-34px` at
  `min-width:760px`) so it visually straddles both panels instead of living entirely in
  the light half. **Scope this to the sign-in view only** — give it a dedicated class
  (`.form-wrap-signin`, not the generic `.form-wrap`) — the product picker has no hero
  panel to straddle against and would just shift off-center for no reason.

### Single-theme by design

The login page is a deliberate single-theme exception: no `@media
(prefers-color-scheme: dark)` block, no `[data-theme="dark"]` override — every token is
still painted explicitly (not left to inherit) so the page holds regardless of
host/OS theme. Don't reintroduce dark-mode switching here without deciding it's
actually wanted; every *other* screen in the suite still follows the normal light/dark
handling in `theme.css` — this is a named exception, not evidence that rule is wrong.

## Suite mark vs. product marks

The login page's mark is **not** any single product's mark. The suite mark is three
small nodes converging on one point, echoing the name itself: kāraka = "the agent that
brings the action about," the point everything else relates back to.

Each product gets its **own** inline-SVG mark expressing what that product *is*,
sharing only the cobalt (`#2451FF`) → teal (`#0F8B8D`) gradient stops for family
resemblance — literal to the product's name/purpose, simple enough to read at favicon
size, built as inline SVG (not a raster export) so it's crisp everywhere. Don't
copy-paste one product's mark concept for another's; design each on its own terms.

## Cross-linking between products

Until a second product is actually built:
- An existing product's sidebar can list a not-yet-built product as a `.rail-soon`
  entry — keep that pattern rather than hiding the upcoming product entirely.
- The login page's product picker is the canonical place a user chooses which product
  to enter; individual products don't need a full app-switcher UI themselves as long as
  the suite stays small — a "back to picker" link from each app's rail footer is enough
  once more than one product is real.

## File layout

```
karaka-ui/
  .claude/skills/          # this skill + karaka-tokens + karaka-components
  packages/
    tokens/
      theme.css             # canonical tokens+components — see karaka-tokens skill
      fonts/                  # font binaries + pre-encoded .b64 data
      karaka-tokens.json      # design-tool token export
      karaka-tokens.penpot.json
    brand/
      karaka-banner.svg      # full brand lockup
  apps/
    login/                   # canonical split-hero login (this skill)
    <product>/               # one folder per product
```

`packages/tokens/theme.css` is a **reference to copy from**, not something apps `<link>`
at runtime — every app stays a single portable file. See [[karaka-tokens]] for why.
This skill's own `reference/` mirrors the login source and brand banner — copy from
there when scaffolding a new login-style screen rather than reverse-engineering the
pattern from prose.
