# Design system

## Visual character

BuzzKill uses a high-contrast, playful dark system: near-black background, chunky rounded typography, bright pastel accents, large sentence-like controls, thick outlines, and hand-drawn wavy underlines for editable rule tokens. Cards use broad rounded rectangles rather than conventional Material list rows. `CONFIRMED` from representative native captures `010`, `013`, `029`, `063`, `071`, and `081`.

## Color system

Exact solid-pixel histogram sampling produced the following reliable values:

| Role | Observed value | Evidence/confidence |
|---|---:|---|
| App background | `#0A0B0D` | HIGH; dominant on every native dark screen |
| Bottom bar/general surface | `#1A1C21` | HIGH |
| Yellow accent | `#FFF387` | HIGH |
| Primary text | `#FFFFFF` | HIGH |
| Secondary text | `#858586` | HIGH |
| Muted icons/dividers | `#535559` / `#3F414B` | HIGH |
| Validation/error | `#FF7070` | HIGH; `059` |
| Enabled rule frame | `#93D1F3` | HIGH; `063` |
| Disabled rule frame | `#3F414B` | HIGH; `064` |
| Suggestion green | `#80DB94` | HIGH; `081` |
| Suggestion purple | `#A16FFF` | HIGH; `081` |

Full per-screen histograms: `evidence/measurements/color-analysis.json`. Anti-aliased edge shades are not tokens.

## Layout and spacing

- Primary full-width buttons: x=63..1017 on a 1080 px canvas, giving 24 dp horizontal margins; height 126 px/48 dp. Evidence: `029`, `059`, `062` XML.
- List/card margins: often 42 px/16 dp. Evidence: `063`, `071` XML.
- Bottom navigation: y=2154..2337, approximately 70 dp; system gesture area below is approximately 24 dp. Evidence: `010`, `013`, `063` XML.
- Icon button boxes frequently measure 116 px/44.2 dp. This is slightly below the usual 48 dp accessibility target. Evidence: Rules close/search/More buttons in screen specs.
- Rule cards use an outer colored frame plus inset black sentence surface. Estimated outer radius 18 dp and inner radius 14-16 dp; confidence MEDIUM because radius is screenshot-derived.
- Dividers are very dark 1-3 dp lines. Shadows are subtle and mainly visible under floating buttons/dialogs; exact elevation is UNKNOWN.

## Rule sentence styling

Editable tokens are yellow (`#FFF387`), bold, and underlined with a custom wavy stroke. Missing tokens become red with a red wavy underline. Action icons sit in colored circles immediately before the action name. Disabled rule cards apply a strike-through to every line. Evidence: `029`, `043`, `059`, `063`, `064`, `082`.

`STRONG INFERENCE`: Implement the sentence renderer as semantic inline tokens with separate focus/tap targets while preserving line wrapping. The original UI often exposes the sentence as a single accessibility node; a rebuild should improve this without changing visible behavior.

## Components

Recurring components include onboarding step cards, four-item bottom navigation, empty-state hero, primary button, floating pill action, rule card, history summary/timeline, dual segmented controls, notification card, category/action card, setting row, radio-selection dialog, text-entry dialog, anchored popup menu, colorful suggestion card, and activity Rules/Changes tabs. Detailed requirements: `component-catalog.md`.

## States

- Selected: yellow outline/fill; dialog rows have yellow radio dot.
- Disabled: gray frame, gray switch, sentence strike-through.
- Validation: red token and explicit warning row.
- Empty: centered/lower hero copy, no skeletons.
- Search no-result: no explicit error panel, just absent list results.
- Loading: no native loading indicator was observed; do not fabricate one without product direction.

## System bars

Native dark screens use a transparent/dark status bar and white system icons. Gesture navigation retains a dark background with a light gesture handle. Evidence: `device/display-and-insets.md`.
