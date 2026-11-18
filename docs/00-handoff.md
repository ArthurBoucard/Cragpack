# Cragpack — build handoff

Cragpack is a collaborative trip planner for climbers: pick objectives, plan each day, take the beta offline, log what everyone sent, and share a recap. This pack has everything needed to start building the app.

**Name:** Cragpack (earlier working name: ClimbTrip). Domain, store names and trademark are still to be confirmed (see D13 in `docs/02-decisions.md`).

**Status:** design v1 is complete for the core app (31 screens, dark and sunlight themes). The tech stack is Kotlin Multiplatform; the backend and sync engine are still to be chosen (see `docs/06-tech-spec.md`).

## Start here (reading order)

| # | Read | Why |
|---|---|---|
| 1 | `docs/01-product-brief.md` | What the app is, who it is for |
| 2 | `docs/02-decisions.md` | Every product decision already made. Don't reopen these without the product owner |
| 3 | `design/flows-overview.png` | All screens laid out as user flows, in one image |
| 4 | `docs/03-screens-and-flows.md` | Each screen: purpose, where it comes from, where it goes |
| 5 | `docs/04-design-system.md` + `design/design-system.png` | Rules, tokens and components to build first |
| 6 | `docs/05-data-model.md` | Proposed entities and fields |
| 7 | `docs/06-tech-spec.md` | Architecture, offline and sync, auth, media, open technical choices |
| 8 | `docs/07-build-plan.md` | Phased plan, open design gaps, out of scope |

## What's in the pack

```
README.md                  this file
docs/                      the seven documents above
design/
  flows-overview.png       every screen, grouped into 11 flows (arrows = what the user taps)
  design-system.png        the design system reference page
  screens/dark/            31 screens, dark theme (PNG, 2x, 390×844 pt)
  screens/sunlight/        the same 31 screens, sunlight (light) theme
  source/                  design source files (see "Viewing the source" below)
tokens/
  cragpack.tokens.json    all colours, grade bands, type, sizes, radii (W3C design-tokens format)
icons/
  svg/                     the 38 icons used, one SVG each (Lucide, ISC licence)
  icons-sprite.svg         the same icons as one sprite
  LICENSE-lucide.txt
data/
  grades.json              Font, V and French grade lists with colour band and sort index
```

## The ten rules to keep in mind

1. **Local first.** No account is needed to plan. Data lives on the phone. The app asks for an account only when an action needs other people or the cloud: invite, join, share a link, sync devices.
2. **Offline at the crag.** Every screen works without signal. Status is always visible in the top bar: Synced, On this phone, edits to sync, Saving %, Paused.
3. **Orange means "do":** actions, sent ticks, "now", progress. It is never used for warnings.
4. **A dashed outline means missing or not done yet**: a gear gap, not saved, edits waiting to sync, empty states.
5. **Grades are stored exactly as entered** (Font, V or French). The colour band is the grade's first digit; V uses its Font equivalent.
6. **One primary action per screen**, in the dock at the bottom.
7. **Tap targets are 44 pt or more (52 by default), and text is 13 pt or more.** The app is used with chalky fingers in sunlight.
8. **Two themes: dark and sunlight.** Photos and the video viewer stay dark in both.
9. **Icons are Lucide**, never text symbols.
10. **New UI is built only from the design-system components and tokens.** A new value becomes a token first.

## Viewing the source

The PNGs in `design/` are the reference to build from. `design/source/` holds the original design files:
- `Cragpack Screens.dc.html` and `Cragpack System.dc.html` (Claude Design canvas files)
- `cragpack.css`: the design system as CSS. Its header lists every rule.
- `support.js`: the canvas runtime.

The live, clickable canvas is shared by the product owner. To open the files locally, serve the `design/source/` folder with any static server (for example `npx serve design/source`) while online; the page loads React and Google Fonts from the web.

## Fonts

| Use | Family | Source |
|---|---|---|
| Text | Instrument Sans (400–700) | Google Fonts |
| Headings and numbers | Bricolage Grotesque (500, 700) | Google Fonts |
| Links and codes | JetBrains Mono (400–700) | Google Fonts |

All three are open-licence (SIL OFL) and can be bundled with the app.
