# 04 · Design system

Visual reference: `design/design-system.png`. Source of truth for values: `tokens/cragpack.tokens.json`. Source of truth for behaviour: the header of `design/source/cragpack.css`. CSS `px` = `dp`/`pt` one to one.

Build this layer **first**. Every screen is made only from these pieces.

## Rules

1. **Accent (orange) is for doing:** actions, sent ticks, "now", progress. Never a warning.
2. **A dashed outline means missing or not done yet:** a gear gap, not saved, edits to sync, empty states.
3. **Offline dot:** full = saved · half = partly saved · ring filling up = saving (0–100) · empty = not saved. The ↓ icon is only ever the Download *action*.
4. **Sync status pill** (top bar centre), one of:
   - `✓ Synced`
   - `cloud-off On this phone` (no account)
   - `N edits to sync` (dashed)
   - `◐ 14/18 saved`
   - `Saving 62%`
   - `Paused` (dashed)
5. **Grades:** the colour band is the grade's first digit (see below). The chip always shows the grade text too, so colour is never the only signal.
6. **People:** one colour per climber, given in join order (5 colours, then repeat).
7. **One primary action per screen**, in the dock.
8. **Sizes:** tap targets are 44 or more (52 by default), and text is 13 or more.
9. **Themes:** Dark and Sunlight (Auto follows the system). Photo surfaces and the media viewer stay dark in both. The recap sheet stays light in both.
10. **Safe areas:** content starts below the status bar, and the dock sits above the home indicator (use the OS insets: Compose `WindowInsets`). Scrolling lists end with enough bottom padding to clear the dock (dock bottom + 88).
11. **Overlays:** a bottom sheet for choices and short forms, a dialog only to confirm something that can't be undone, a toast for a quick result with Undo.
12. **Danger red** is only for destructive actions: delete, remove, leave.
13. **Icons:** Lucide only, 24 (18 small), stroke 2, round caps. The star fills when on.
14. **New value? Add a token first**, in both the JSON and the CSS.

## Colour roles

| Token | Dark | Sunlight | Use |
|---|---|---|---|
| bg | #1c1814 | #f3ebe0 | Screen background |
| ink | #f3ebe0 | #1c1814 | Main text, icons |
| ink2 | #dccfbb | #5c5145 | Secondary text, labels |
| surface | ink 9% | ink 6% | Cards, rows, inputs |
| surface2 | ink 18% | ink 12% | Icon buttons, tracks, quantity box |
| line | ink 20% | ink 30% | Outlines |
| raised | #2e2620 | #fffaf3 | Sheets, dialogs, toasts |
| solid / onSolid | #f3ebe0 / #1c1814 | #1c1814 / #f3ebe0 | Selected chip, dock, primary neutral button |
| accent / onAccent | #f58a3c / #1c1814 | same | Actions, ticks, now, progress |
| accentInk | #f58a3c | #a8480f | Accent used as text (contrast-safe) |
| danger / dangerInk / onDanger | #ff7a6b / #ff8f82 / #1c1814 | #b3261e / #b3261e / #fff | Destructive only |

## Grade bands

| Band class | Colour | Boulder (Font) | Route (French) | Boulder (V) |
|---|---|---|---|---|
| g1, g2 | soft lilac #d9cdef | 1 – 2+ | 1a – 2c | VB |
| g3, g4 | soft pink #f2b6c6 | 3 – 4+ | 3a – 4c | VB – V0+ |
| g5 | light green #a6d98c | 5 – 5+ | 5a – 5c | V1 – V2 |
| g6 | blue #6aa5e0 | 6A – 6C+ | 6a – 6c+ | V3 – V5 |
| g7 | red #e2604f | 7A – 7C+ | 7a – 7c+ | V6 – V10 |
| g8 | black #1c1814 (light text, ink outline) | 8A – 8C+ | 8a – 8c+ | V11 – V16 |
| g9 | gold gradient #f7dc7e → #d9a531 → #b9841c | 9A | 9a – 9c | V17 |

Text on bands 1–7 and 9 is #1c1814. The full Font↔V table and sort indexes are in `data/grades.json`. V grades on their own: VB counts as band 3.

## Type

| Style | Font | Size / weight | Use |
|---|---|---|---|
| titleXl | Bricolage Grotesque | 40 / 700 | Hero titles (trip, boulder) |
| h1 | Bricolage Grotesque | 34 / 700 | Screen titles |
| h2 / h3 | Bricolage Grotesque | 32 / 26, 700 | Card titles, sheet titles |
| big | Bricolage Grotesque | 24 / 700 | Numbers in tiles (14/18) |
| title | Bricolage Grotesque | 20 / 700 | Centred top-bar title in forms |
| rowName | Instrument Sans | 17 / 600 | List row names |
| body | Instrument Sans | 15 / 500 | Default |
| label | Instrument Sans | 13 / 600 | Field labels, meta. The smallest size allowed |
| mono | JetBrains Mono | 13 / 500 | Links, codes, times |

## Sizes and shapes

- **Tap targets and gaps:** tap 52, small tap 44, screen edge 16, gap 8.
- **Component heights:** dock 64, input 56, row 76, slim row 64.
- **Radii:** sm 14, md 24, lg 32, xl 36, pill 999. The device frame corner is 40 (mock only).

## Components → suggested Compose names

| CSS class (mock) | Suggested component | Variants / notes |
|---|---|---|
| `.ib` | `CtIconButton` | default, `accent`, `on` (selected), `sm` 44, `xl` 76 |
| `.btn` | `CtButton` | solid, `accent`, `ghost`, `danger`, `danger solid`; sizes md 48, `sm` 40, `wide` 56 full width |
| `.chip`, `.chips` | `CtChip`, `ChipRow` | toggle or single choice (radio); the selected one is solid |
| `.seg` | `CtSegmented` | 2–4 options; Style, grade display, theme |
| `.field` + `.input` | `CtTextField` | label above, 56 high, pill radius; `area` = multiline notes |
| `.switch` | `CtSwitch` | the on state is accent |
| `.stepper` | `CtStepper` | − value + |
| `.radio` in `.row` | `CtRadioRow` | the selected row gets an ink outline |
| `.gpick` | `GradePicker` | 4 columns of grade pills; the selected one gets an outline ring |
| `.status` | `SyncStatusPill` | states listed in rule 4; `todo` = dashed |
| `.tag` | `StatusTag` | small pill with the offline dot; `todo` = dashed |
| `.st` | `OfflineDot` | full / part / busy(progress) / none |
| `.now` | `NowBadge` | accent pill, "Now · day 2/5" |
| `.grade`, `.gtag` | `GradeChip`, `GradeTag` | circle sizes 68 / 54 / 44 / 40; the tag is a 24-high pill |
| `.pbar`, `.pyramid` | `GradePyramid` | bars per band, length = value |
| `.av`, `.avs` | `Avatar`, `AvatarStack` | 26 / 36 / 40 / 52; overlap −10 with a ring |
| `.row`, `.row.slim` | `ListRow` | leading (grade, avatar, qty, icon), title + meta, trailing action; `todo` = dashed |
| `.card.tile` | `StatTile` | label on top, big number at the bottom, optional corner action |
| `.qty` | `QuantityBox` | 2/3 |
| `.photo`, `.hero`, `.pcard` | `PhotoSurface`, `PhotoHero`, `PhotoCard` | always dark scrim, text at the bottom |
| `.media` | `BetaThumb` | 108×128; `add` variant; online-only dot |
| `.ring`, `.meter`, `.ticks` | `ProgressRing`, `ProgressBar`, `SegmentedProgress` | accent on a surface2 track |
| `.days`, `.day` | `DayTabs` | date and sector, or Rest; scrolls sideways |
| `.drop`, `.drag`, `.grip` | drag-and-drop states | dashed accent slot, lifted card tilted −3° |
| `.tray`, `.pick` | `UnplannedTray` | floating, scrolls sideways; each item has a + |
| `.dock` | `TabDock` | 5 tabs; the current tab is filled with bg plus an accent dot |
| `.dock.cta` | `CtaDock` | accent icon at left plus a centred label |
| `.dock.log` | `LogDock` | Attempt / Sent / Flash; the chosen one is accent |
| `.bottom-sheet` + `.scrim` | `CtBottomSheet` | grab handle, title row with ✕ |
| `.dialog` | `CtConfirmDialog` | title, short consequence text, Cancel + danger |
| `.toast` | `CtToast` | icon, message, Undo; above the dock |
| `.empty` | `EmptyState` | inside a dashed card: title + one action |
| `.skeleton` | `Skeleton` | loading placeholder with a shimmer |

## States

- **Pressed:** scale 0.97.
- **Focus:** a 3-wide ink outline.
- **Disabled:** 40% opacity, no touch.
- **Loading:** skeleton rows.
- **Errors:** never shown in red text. Use a dashed row explaining what's wrong plus one action (see Storage full).
