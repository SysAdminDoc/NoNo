# Typography

## Observed family

`CONFIRMED`: The native app uses a rounded, heavy, humanist sans-serif with distinctive single-story/soft forms and unusually bold body copy. `UNKNOWN`: exact font family, license, fallback stack, variable axes, and whether all weights are one family. Static font extraction was not performed.

Reconstruction must use an authorized font or an original/openly licensed substitute after visual comparison. Do not copy an embedded proprietary font from the APK.

## Estimated scale

Screenshot-derived estimates at density 2.625; confidence MEDIUM:

| Role | Approx. sp | Weight | Behavior |
|---|---:|---:|---|
| History count | 48 | 700-800 | Single line, left aligned |
| Rule-builder hero sentence | 26-28 | 700-800 | Wraps freely; token-level colors/underlines |
| Page title | 24-26 | 700-800 | Center or left by surface |
| Section title | 20-22 | 700-800 | Left aligned |
| Card title/body large | 18-20 | 700 | Multi-line |
| Standard body | 16-18 | 600-700 | Secondary text uses gray |
| Navigation/label | 13-15 | 700-800 | Selected label yellow |

## Line and wrapping behavior

- Rule sentences use generous line height, approximately 1.35-1.45×, with no ellipsis in observed states.
- History card body can wrap; app/time remain on one row.
- Settings summaries wrap to multiple lines and determine row height.
- Dialog options are sentence case except `CANCEL`, which is uppercase.
- Times use locale-formatted 12-hour values in en-US (`1:52 PM`); timeline labels use `12AM`, `6AM`, `12PM`, `6PM`, `12AM`.
- Retention and duration menus expose grammatical strings exactly as observed, including “1 hours” and “1 days”; a faithful visual reconstruction may preserve them, though product QA should consider correcting them.

## Accessibility requirements for rebuild

Use scalable `sp`, reflow at 200% font scale, avoid fixed-height text containers, and expose each editable rule token as a separately labeled semantic action even if visually rendered inside one sentence.
