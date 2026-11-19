# 03 · Screens and flows

There are 31 screens, grouped into 11 flows. `design/flows-overview.png` shows them all with arrows. Each screen exists in both themes:

- `design/screens/dark/NN-name.png`
- `design/screens/sunlight/NN-name.png`

Screen numbers are IDs, not an order. The device frame is 390×844 pt (iPhone 14 size), with a 47 pt status-bar inset at the top and 34 pt at the bottom.

## Navigation model

- **Trips home** is the root. The avatar on Trips home opens **My logbook**, and Settings is inside My logbook.
- **Inside a trip**, a 5-tab dock: **Trip · Projects · Plan · Gear · Log** (the Projects tab was called Wishlist in the design files).
- **Forms and flows** (new trip, add objective, edit day, create account) close with **✕** at top left and finish with one main button in the dock.
- **Drill-downs** (boulder detail, crew, trip settings) go back with **‹**.
- **Overlays**:
  - a bottom sheet for choices and short forms (add beta, add gear, account prompt, climber, sync conflict)
  - a dialog only to confirm something that can't be undone (delete trip)
  - a toast to offer Undo (after logging)
- **The top bar always shows status** in the middle: Synced · On this phone · N edits to sync · Saving N% · Paused.

## Flow 0 · First launch, no account needed

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 15 | Trips home · first launch | Empty state. A generic avatar, the status "On this phone", a "No trips yet" card with "Paste invite link". The dock button is **New trip**. | New trip (09). Paste link → Account prompt (13). Avatar → My logbook (26) |

## Flow 1 · Start a trip

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 01 | Trips home | The current trip as a large photo card ("Now · day 2/5", crew, offline status), upcoming trips with **Download**, past trips with **Recap**. The dock button is **New trip**. | Trip overview (02), New trip (09), Recap (08), Saving offline (11), My logbook (26) |
| 09 | New trip | Cover, name, crag, start and end dates. Dock: **Create trip**. | Trip overview (02). ✕ → Trips home |
| 02 | Trip overview | Photo hero with day N/total, name, dates, crew and an Invite (+) button. Below: sent progress (7/18), tiles for Today, Wishlist, Gear (dashed when something is missing) and Beta (with a Download button). | Every tab, Crew (07), Trip settings (29), Saving offline (11), Trip log (25) |
| 07 | Crew / invite | The invite link with Copy and Share, then every climber with their offline status (Saved, Saving 62%, Not saved). | Climber sheet (31), Trip settings (29) |

## Flow 2 · Account only when sharing

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 13 | Account prompt (sheet) | Opens when someone without an account invites, joins, shares a link or turns on sync. "Create an account to invite your crew. Your trips on this phone come with you." Continue with Apple / Google / email. ✕ = Not now. | After sign-in, back to the action that triggered it (for example Crew). Email → Create account (14) |
| 14 | Create account | Name, email, password, and an "I have an account" link. Dock: **Create account**. | Back to the action that triggered it |

## Flow 3 · Join with a link

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 10 | Join | Opened from an invite link. Trip photo, "Léa invited you", name, dates, crew, and a "Save offline · 412 MB" switch that is on by default. Dock: **Join trip**. | Account prompt (13) if signed out, then Saving offline (11) |
| 11 | Saving offline | A progress ring (62%), MB and objectives saved, a Wi-Fi only switch, and a per-item state for each objective (saving bar, Queued, Saved). Dock: **Pause**. | Trip overview (02) |

## Flow 4 · Build the wishlist

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 03 | Wishlist | Filters: Sector, Grade, Who. Rows are grouped by sector. Each row shows a grade chip, name, interested avatars, sent/not-saved state and a ★ toggle (I want to try it). The top-right **+** adds an objective. | Add objective (12), Boulder detail (05) |
| 12 | Add objective | Name, style (Boulder / Sport / Trad), a grade picker in band colours, sector chips (plus a new sector), and Pin location. Dock: **Add to projects**. | Boulder detail (05) of the new objective, to add beta right away |
| 05 | Boulder / route detail | Photo hero with grade and name, and a Navigate chip for the sector. A beta strip (+ add, videos, topo; YouTube items marked online only), a note from a climber, and tiles for who wants it and who sent it. Dock: **Attempt · Sent · Flash**. | Add beta (20), Media viewer (21), Logged (24) |

## Flow 5 · Add beta

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 20 | Add beta (sheet) | A YouTube link field (online only), plus Video, Photo, Topo and Note options. Pending uploads show "Waits for Wi-Fi". | Boulder detail |
| 21 | Media viewer | Full-screen video or photo, always dark. Source and offline status, position (3/3), who added it, and a scrubber. | Boulder detail |

## Flow 6 · At the crag

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 04 | Day planner | Day tabs (date and sector, or Rest) that scroll for long trips, the sector card (approach, conditions), and the day's objectives in order. Items are dragged with a grip handle and a drop slot. A floating **unplanned tray** lets you drag an item onto the day or tap +. | Edit day (22), Boulder detail (05) |
| 22 | Edit day | Sector chips, a Rest day switch and notes. Dock: **Save**. | Day planner |
| 06 | Gear board | Filters: whole trip or a single day. Rows show needed/claimed (2/3) and who brings it. A missing item gets a dashed row and a **Bring 1** button. | Add gear (23) |
| 23 | Add gear (sheet) | Item name, a quantity stepper, which day it's for, and an "I'll bring it" switch. **Add**. | Gear board |

## Flow 7 · Log sends

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 24 | Logged | The boulder detail just after tapping **Sent**: the Sent button is filled, the climber's avatar is added to Sent, the status shows "1 edit to sync", and a toast says "Sent · La Joker" with **Undo**. | Undo → Boulder detail |
| 25 | Trip log (Log tab) | Filters: Everyone, Me, Sends. Entries are grouped by day and sector, each showing who did what (Flash · Sent, or 3 attempts). | Boulder detail |
| 26 | My logbook | The personal logbook across all trips. Filters: All, Boulder, Route, year. Total sends, hardest grade, grade pyramid, and recent entries with trip and date (including "Outside a trip"). Dock: **Log a send**. | Settings (27), Boulder detail |
| 27 | Settings | The account (provider), boulder grade display (As entered / Font / V), theme (Auto / Dark / Sunlight), Wi-Fi only, storage used, Sign out and Delete account. | Saving offline / storage |

## Flow 8 · After the trip

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 08 | Trip recap | Total sends and flashes, a grade pyramid by band, sends per day (with rest days), and each climber's sends and hardest grade. Dock: **Share recap**. | Share card (28) |
| 28 | Share card | A preview of the recap image (photo, total, pyramid, crew, wordmark), with Save image and Copy link. Dock: **Share** opens the phone's share menu. | Recap. Copy link needs an account |

## Flow 9 · Manage the trip

| # | Screen | Purpose and content | Goes to |
|---|---|---|---|
| 29 | Trip settings | Cover, name, crag, dates, and invite link **Reset**. Then **Leave trip** and **Delete trip** (danger). Changes save automatically. | Confirm delete (30) |
| 30 | Confirm delete (dialog) | "Delete Font Easter? The plan, beta and logs go for all 5 climbers. Sends stay in each personal logbook." Buttons: Cancel and Delete. | Trips home |
| 31 | Climber (sheet) | Name and offline status, **Make organiser**, **Remove from trip** (danger). | Crew |

## Flow 10 · Empty and problem states

| # | Screen | When it shows |
|---|---|---|
| 16 | Wishlist · empty | A trip with no objectives yet. A dashed card with **Add objective**. |
| 17 | Invite expired | The invite link was reset or has expired. Dock: **Go to my trips**. |
| 18 | Storage full | Saving paused because the phone is full: a dashed row with **Free up** (opens storage), Retry on failed items, and **Retry all** in the dock. |
| 19 | Sync conflict (sheet) | Two climbers moved the same objective to different days while offline. Pick which edit to keep. |

## Demo data used in the mocks

- **Font Easter:** Fontainebleau, 17–21 Apr, 5 climbers (Léa = you, Tom, Inès, Max, Sam), 18 objectives.
- **Magic Wood:** an upcoming trip.
- **Albarracín:** a past trip, 31 sends.

Boulder names and grades are examples only; don't use them as real data.
