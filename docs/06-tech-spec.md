# 06 · Technical spec (starting point)

Items marked **[decide]** are open choices for the developer and product owner. Everything else follows from the product decisions (`02-decisions.md`).

## 1. App architecture

- **Kotlin Multiplatform** (D5). Shared module holds:
  - the domain model and use cases
  - the local database
  - the sync client
  - the grade translator
  - formatting
- **UI [decide]:** Compose Multiplatform for both platforms (the design system maps cleanly; see `04-design-system.md`) or native SwiftUI on iOS. Recommendation: Compose Multiplatform, so the component library is written once.
- **Local database:** SQLite through SQLDelight or Room (KMP). Every screen reads from the local database, never directly from the network.
- **Networking:** Ktor client.
- **Theme:** generate Kotlin constants from `tokens/cragpack.tokens.json` (a small script or Gradle task) so tokens never drift from the design.

## 2. Local first and accounts (D3, D4)

- On first launch the app creates a **local user** (`authProvider = local`). Everything works offline and without an account.
- **Account prompt** (screen 13) is shown only before these actions:
  - invite (Crew +, Copy, Share)
  - join (open or paste an invite link)
  - Copy link on the share card
  - turning on sync across devices
- Saving the recap image works without an account.
- **Sign-in methods:** Sign in with Apple, Google, email + password. Use the official Apple and Google buttons and SDKs, following their brand rules.
- **After sign-up:** keep the local user id, upload all local data, then continue the action that triggered the prompt (for example, show the invite link).
- **[decide]** what happens if someone signs in to an *existing* account that already has data while the phone also has local trips. Proposal: merge (ids are UUIDs, so nothing collides), then show a short confirmation.
- **Account deletion** inside the app (Settings → Delete account). Apple requires this for apps that let people create accounts, and Google Play has a similar rule.
- When you offer Google sign-in on iOS, Apple's review rules expect a privacy-focused login option too; Sign in with Apple covers this. Check the current App Store guidelines before submitting.

## 3. Sync and conflicts (D6)

- **Sync engine [decide]:** compare options with a Kotlin Multiplatform client:
  - PowerSync + Postgres
  - Supabase (with a custom outbox)
  - your own API with an outbox and change feed

  Criteria:
  - works fully offline
  - partial sync per trip
  - row-level permissions (a member sees only their trips)
  - cost
- Writes go to the local database first and then into an outbox. Sync runs when online, signed in and the trip is shared.
- Use server-assigned order (or hybrid logical clocks) to decide "last" in last-write-wins.

| Data | Rule |
|---|---|
| Text fields (trip name, notes, objective name, grade) | Last write wins, field by field |
| Interest ★, crew membership | Add and remove operations. Never a conflict |
| PlanItem (objective → day) | If two people moved the same objective to **different days** while offline: the first change to reach the server wins, and the other person sees the **Sync conflict sheet (19)** to keep theirs or accept. Reorders within a day are last write wins (fractional `orderKey`) |
| Gear claims | The server enforces total claimed ≤ needed. A claim that would overflow is rejected and the app shows "Already covered" |
| LogEntry | Append-only per user. No conflicts. Undo = soft delete |
| Deletes | Soft delete (tombstones). A deleted trip disappears for everyone, but each user's LogEntries keep their snapshot (D2) |

- The status pill reflects the outbox: `N edits to sync` while there are pending changes, `Synced` when empty, `On this phone` without an account.

## 4. Offline pack and media (D9)

- **Save offline** downloads every beta media file (videos, photos, topos) and the trip data for one trip.
  - It runs in the background (iOS background URLSession, Android WorkManager).
  - It can resume after interruption.
  - It shows the states from screens 11 and 18: saving %, queued, saved, failed with Retry, and paused because storage is full, the phone is offline, Wi-Fi only is on, or the user paused it.
  - Before starting, check free space against the pack size. When the phone is full, pause and show **Free up**.
- **Uploads:** compress video before upload, for example to around 720p H.264 using the platform encoders, and resize photos. Queue uploads in the outbox; with **Wi-Fi only** on they wait ("Waits for Wi-Fi").
- **YouTube:** store the URL only. Play with YouTube's official embedded player or open the YouTube app. Never download. Always marked "online only".
- **Storage [decide]:** object storage (S3-compatible) with signed URLs, plus per-trip quotas and a maximum video length or size.

## 5. Invite links

- Format `https://cragpack.app/j/<token>` (**[decide]** the domain is not secured yet; see D13), opened as Universal Links (iOS) and App Links (Android).
- If the app isn't installed, the link opens a small web page with the store links.
- Opening a link signed out → Join (10) → account prompt (13) → join → Saving offline (11).
- **Reset** in Trip settings revokes the token. Old links show **Invite expired (17)**.
- **[decide]** whether links expire by default, and whether only organisers can reset links and remove climbers (the design assumes organisers can).

## 6. Grades (D7, D8)

- Store `scale + value` exactly as entered.
- `band` and `sortIndex` come from `data/grades.json`. Keep that table in the shared module as the single source.
- The display setting (As entered / Font / V) only changes how grades are shown, never what is stored. Convert Font ↔ V with the table, and keep French route grades as they are.
- Filters and the pyramid group by band, so mixed scales sort correctly.

## 7. Recap share image

- Render the share card (screen 28) to an image on the device, for example 1080×1350, and hand it to the phone's share menu.
- "Copy link" creates a read-only public recap link and needs an account.

## 8. Platform details

- **Safe areas:** use OS insets for the status bar and home indicator. Lists get bottom padding so the last item clears the dock.
- **Theme:** Auto follows the system setting; the user can force Dark or Sunlight in Settings.
- **Accessibility:**
  - every icon-only button has a label (the mock's `aria-label` text is the wording to use)
  - support dynamic text size
  - keep text at 13 pt or more and tap targets at 44 pt or more
- **Localisation:** English first. French is likely, so plan for strings and check that the five dock tabs fit.

## 9. Privacy and legal (EU)

- Privacy policy and terms (linked from the account prompt).
- A way to export and delete personal data (GDPR).
- Location pins are stored only on objectives the user creates; there's no background location.
- Guidebook scans are shared only inside a private trip. A future public layer would need a copyright review.
