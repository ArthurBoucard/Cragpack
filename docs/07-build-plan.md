# 07 · Build plan

The order follows the local-first design. You get a working solo app first, then accounts and sharing on top, so nothing has to be rebuilt.

## Phase 0 · Foundations

- [ ] Check that the name Cragpack is available (domains, stores, trademark) before using it in bundle IDs and URLs
- [ ] KMP project, CI, iOS + Android app shells, decide the UI toolkit (see tech spec §1)
- [ ] Bundle the three fonts. Generate theme constants from `tokens/cragpack.tokens.json` (dark + sunlight)
- [ ] Import `icons/svg/` as vector resources
- [ ] Build the component library from `04-design-system.md`, and check each component against `design/design-system.png` in both themes
- [ ] Grade module from `data/grades.json`: band, sortIndex, Font↔V display
- [ ] Local database and schema from `05-data-model.md`, with a local user created on first launch

## Phase 1 · Solo app, offline, no account

Screens: 15, 01, 09, 02, 03, 12, 05, 04, 22, 06, 23, 24, 25, 26, 27, 08, 29, 30, 16

- [ ] Trips: create, edit, delete (trip settings, confirm dialog)
- [ ] Wishlist, add objective, ★ interest, filters, empty state
- [ ] Boulder detail with local beta (photos, notes, video files from the phone)
- [ ] Day planner: days, rest days, notes, drag and drop + tap to add, unplanned tray
- [ ] Gear board, add gear, claims
- [ ] Logging (Attempt / Sent / Flash + Undo toast), trip log, personal logbook, "Log a send" outside a trip
- [ ] Recap stats
- [ ] Settings: grade display, theme, Wi-Fi only
- [ ] Status pill shows "On this phone"

## Phase 2 · Accounts and sharing

Screens: 13, 14, 07, 31, 10, 17, 19

- [ ] Sign in with Apple, Google, email. Account prompt only on share actions. Upload local data on sign-up
- [ ] Choose and set up the sync engine. Outbox and status pill states
- [ ] Invite link (copy, share, reset), deep links, join flow, invite expired
- [ ] Crew list, climber sheet (make organiser, remove), leave trip
- [ ] Conflict rules from tech spec §3, including the sync-conflict sheet
- [ ] Account deletion, sign out

## Phase 3 · Offline pack and media

Screens: 11, 18, 20, 21

- [ ] Media upload queue (compression, Wi-Fi only, retry)
- [ ] Save offline per trip: background download, progress, pause, resume, storage full
- [ ] YouTube links (online only), media viewer
- [ ] Crew offline status per climber

## Phase 4 · Recap sharing

Screens: 28

- [ ] Share card image + phone share menu, Save image, public recap link (account needed)

## Phase 5 · Polish and release

- [ ] Accessibility pass (labels, dynamic type, contrast in sunlight), French strings
- [ ] Privacy policy, terms, data export, store listings
- [ ] Secure the name: domains, store names, trademark (see D13)

## Design gaps to fill during the build

Each of these is small and should follow the existing components. Ask the product owner, or draw them in the design file, before building.

| Gap | Notes |
|---|---|
| Log a send (outside a trip) form | Opened from My logbook. Likely: name, style, grade picker, crag, date, Attempt / Sent / Flash. Reuse Add objective's pieces |
| Settings without an account | Account row reads "Create account" and opens the account prompt |
| Sign in to an existing account | "I have an account" on Create account. Email + password, plus Apple and Google |
| Edit or delete an objective | The ⋮ menu on boulder detail → sheet with Edit and Delete (danger) |
| Storage management | "Storage" in Settings: offline size per trip with "Remove offline copy" |
| Account created / data moved | Short confirmation after local trips upload to a new account |
| Notifications | Not designed. Out of v1 unless the product owner says otherwise |

## Out of scope for v1 (decided)

- Map view (location pins are stored for later)
- Importing crag and route data (manual entry only)
- Weather and rest-day suggestions
- Public or social layer beyond private trips
