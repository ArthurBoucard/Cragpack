# 02 · Decisions already made

These decisions come from the product owner. They are settled for v1; check with the product owner before changing any of them.

| # | Topic | Decision | Consequence for the build |
|---|---|---|---|
| D1 | Crag and route data | **Manual entry only.** No import from 27crags, theCrag, Vertical-Life or OpenBeta for now. | Add objective is a typed form. Sectors are created inside each trip. Leave room for an external ID later. |
| D2 | Logbook | **A personal logbook across all trips, in addition to the per-trip log.** | One log entry belongs to a user and optionally to a trip and objective. "Log a send" also works outside any trip. Deleting a trip keeps everyone's personal logbook entries. |
| D3 | Accounts | **Create an account with email and password, or sign in with Google or Apple.** | Three sign-in methods. Account deletion inside the app. |
| D4 | Local-first | **Planning needs no account.** Create trips, add boulders, plan and log on the phone only. The account prompt appears only for actions that need others or the cloud: invite, join, share a link, sync devices. | Local database from day one. Local data moves into the account when it is created. Status pill reads "On this phone". |
| D5 | Platform | **Kotlin Multiplatform (KMP)**, iOS and Android. | Shared domain, database and sync in Kotlin. UI is likely Compose Multiplatform; confirm early (see tech spec). |
| D6 | Offline sync | **Local-first sync with explicit conflict rules** (agreed in principle). | Rules are written in `06-tech-spec.md`. The sync engine is still to be chosen. |
| D7 | Grades | **Store the grade exactly as entered** (Font, V or French). A translator converts for display when the user asks. | Store `scale + value`. The display setting is As entered / Font / V. Reference table: `data/grades.json`. |
| D8 | Grade colours | Band = **first digit** of the grade. 1–2 soft lilac, 3–4 soft pink, 5 light green, 6 blue, 7 red, 8 black, 9 gold. V grades take their Font equivalent's band. | Colour comes from the grade, not from a lookup table. Bands are in the tokens. |
| D9 | Beta video | **YouTube links (online only), otherwise uploaded videos.** No Instagram, no downloading YouTube. | Use YouTube's embedded player and mark those videos "online only". Uploads queue and wait for Wi-Fi if the user wants. |
| D10 | Themes | Dark plus Sunlight (light), with Auto by default. | Every component works in both. Photos and the video viewer stay dark. |
| D11 | Map | Not in v1. | No map screen and no map filter. The location pin is stored for later. |
| D12 | Sport / trad detail | A route detail page is the same as a boulder's. | One detail screen for every style. |
| D13 | Name | **Cragpack** (one word, capital C; earlier working name: ClimbTrip). It means both the offline beta pack and your pack of friends. | No existing app with this name was found in web searches (Nov 2026). Before launch, confirm the App Store / Google Play names, the domains (cragpack.app, cragpack.com) and trademarks (INPI / EUIPO, classes 9 and 42). The store subtitle should say what it is, e.g. "Cragpack: climbing trip planner", because "crag pack" is also a backpack term. |
