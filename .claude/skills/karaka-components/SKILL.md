---
name: karaka-components
description: Reusable component markup + interaction patterns for Karaka (every product in the suite, plus the login page) — rail nav shell, buttons, inputs, cards, tables, badges, modals, focus states. Load this when building or extending a screen in this repo so components look and behave identically across products instead of being re-invented per screen.
---

# Karaka — component patterns

Companion to [[karaka-tokens]] (colors/type/tokens) — this covers *how components are
actually built*: markup shape, class names, and interaction rules. The CSS for every
class named below lives in `packages/tokens/theme.css`; this doc explains when and how
to use them.

Every app is a single static HTML file with a small hand-rolled render loop (a `state`
object, an `app` object of action methods that mutate `state` then call `render()`, and
view functions that return template-literal HTML strings) — no framework. Follow that
same shape in every product rather than introducing React/Vue/a build step for one and
not the others. The existing app folders under `apps/` are the reference
implementation for this pattern.

## App shell: rail + topbar

Every product uses the same two-pane shell — a fixed 246px dark rail on the left, a
light content pane on the right with its own topbar:

```html
<div id="app">
  <aside class="rail"> … brand mark, nav, org footer … </aside>
  <div class="main">
    <div class="topbar"> … page title, toast slot … </div>
    <div class="content"><div class="content-inner"> … view content … </div></div>
  </div>
</div>
```

- **Brand mark**: an inline SVG (not a raster logo) so it scales cleanly from favicon to
  sidebar size — gradient stroke via `<linearGradient>`, a `.ring`-or-equivalent group
  that can get a slow CSS spin rotation, wrapped in a `.rail-mark-wrap` with a soft
  `.rail-mark-glow` radial gradient behind it. Each product should get its **own** mark
  concept (not a copy-paste of another product's), sharing only the cobalt→teal
  gradient stops for family resemblance. See [[karaka-shell]] for the suite-level mark
  used on the login/product-picker page.
- **Nav items**: `.rail-link` (with `.active` when current view matches), grouped under
  `.rail-label` section headers. Cross-links to other suite products that aren't wired
  up yet use `.rail-soon` + `.rail-soon-tag` ("SOON") rather than being hidden. Once a
  product is real, promote its cross-link to a real `.rail-link` (or drop it if the
  login/picker already covers cross-navigation).
- **Org footer**: `.rail-foot` pinned via `margin-top:auto`, theme toggle button above a
  `.rail-org` identity row (avatar-initials badge + name + org/role line).

## Buttons

`.btn` (default/secondary), `.btn-primary` (the one filled CTA color per screen — don't
add a second competing filled button next to it), `.btn-ghost` (chromeless, for a
tertiary action), `.btn-sm` (compact, for inline/toolbar use). All buttons get a
`scale(.98)` tap-down on `:active` — keep that; it's the one tactile micro-interaction
in the system, don't add competing ones (bounce, color-flash) elsewhere.

Primary action goes on the right in a header/footer action row; secondary/cancel to its
left, in that order — see the Add-record modal pattern below.

## Auth forms (sign-in, sign-up) — floating-label inputs

Dense admin screens (filters, add/edit forms) use the plain `.input`/`.select` from the
section below. **Auth forms are the one exception**: the login page uses a
floating-label variant instead — `.field-float` wrapping an `.input-float` + `<label>`
(label must follow the input as its next sibling; input needs `placeholder=" "` so
`:not(:placeholder-shown)` can detect a value). Label sits as placeholder text at rest
and floats up + shrinks on focus/value. See `apps/login/index.template.html` for the
working CSS. Don't use `.field-float` in dense data-entry forms (records, vouchers,
etc.) — it reads as a login/marketing pattern, not a data-density one; keep those on
`.field-label` + `.input`.

On a failed submit, apply `.shake` to the containing card/panel for one animation cycle
(450ms horizontal shake) — this is the one destructive/error micro-interaction in the
system; don't invent a second error-shake variant elsewhere.

## Inputs, selects, filters

`.input` / `.select`, fixed 34px height, `.field-label` above each (never
placeholder-as-label). A `.toolbar` row (`display:flex;gap`) holds search + filter
selects together above a table — `.toolbar .input` is wider (216px) than the filter
`.select`s. Use native `<select>` (no custom dropdown chrome) — don't rebuild a select
component per app.

## Cards & tables

`.card` is the one surface-elevation unit — a bordered, shadowed white/dark panel. Add
the raw `onclick` attribute (not just a JS handler) on a clickable card and the CSS
(`.card[onclick]:hover`) auto-applies the lift-on-hover; don't hand-roll hover styling
per instance.

Tables: `.table-wrap` wraps for horizontal scroll (page body never scrolls sideways),
`th` uses `--surface-2` background + uppercase/letter-spaced label, `tr.row-click` for
clickable rows (hover → `--surface-3`, distinct from the header tone). Empty state is
always `.empty-state` with a `.glyph` (⌀) + one line of copy — never a blank table.

Status/state is a `.badge` (pill, tonal background + matching border + a small `.dot`),
using the semantic classes `b-active` / `b-inactive` / `b-exited` / `b-human` (extend
this set in `theme.css` + this doc if a product needs a new status color — don't invent
a one-off inline-styled badge).

## Modals / dialogs

`.overlay` (blurred backdrop, click-outside-to-close via
`onclick="if(event.target===this) app.close…()"`) wrapping a `.modal` (entrance
animation already defined — fade + slight scale, `prefers-reduced-motion`-safe). Always
include:
- a `.modal-close` (×) button, absolutely positioned top-right,
- an `Escape` keydown listener that closes it,
- primary action button first, `Cancel`/secondary second, in a `display:flex;gap` row.

`.notice` (info-tone callout box) goes above a form when an action has a non-obvious
consequence — write that sentence in plain language, not system/implementation terms.

## Feedback

`.toast` — a single inline pill in the topbar (not a stacking corner-toast system),
cleared by an explicit × or auto-timeout. Copy is a plain-language confirmation of what
happened, not what the system did internally.

## Focus & accessibility

`:focus-visible` is a token-driven double-ring (`box-shadow: 0 0 0 2px var(--bg), 0 0 0
4px var(--accent)`), not a plain outline — keep this global rule as-is rather than
overriding focus styles per component.

## What NOT to do

- Don't reach for emoji as icons (see [[karaka-tokens]]).
- Don't build a second modal/dialog implementation with different animation timing or a
  different close pattern — extend `.overlay`/`.modal`.
- Don't introduce a competing accent color for "this app's brand" — see [[karaka-shell]]
  for how each product differentiates itself (mark shape, not palette).
