# 05 · Data model (proposal)

This is a starting point derived from the screens and decisions. The developer owns the final schema. Every entity has `id` (UUID made on the device, so it works offline), `createdAt`, `updatedAt` and `deletedAt` (soft delete, needed for sync).

## Entities

```text
User
  name, email?, authProvider: apple | google | email | local
  settings: boulderGradeDisplay (asEntered | font | v), theme (auto | dark | light), wifiOnly (bool)
  // "local" = no account yet. On sign-up the local id is replaced by the account's id everywhere and its data is uploaded (decision D4, tech spec §2).

Trip
  name, crag (text), startDate, endDate, coverMediaId?
  ownerId -> User

TripMember
  tripId, userId, role: organiser | member, colorIndex (join order, 1–5 then repeat), joinedAt

Invite
  tripId, token, createdBy, createdAt, expiresAt?, revokedAt?
  // "Reset" revokes the current token and creates a new one -> old links show "Invite expired".

Sector
  tripId, name, approachNote?        // manual entry (D1); "5 min approach · dry from 11:00"

Objective
  tripId, name, style: boulder | sport | trad
  grade: Grade                       // value object, below
  sectorId?, location? {lat, lng}    // location stored now, map comes later
  createdBy

Grade (value object, stored on Objective and LogEntry)
  scale: font | v | french           // as entered (D7); room for yds/uk later
  value: "7A+" | "V6" | "6c+"
  band: 1..9                         // derived: first digit, V via its Font equivalent
  sortIndex: int                     // derived from data/grades.json, per discipline

Interest                              // the ★ "I want to try it"
  objectiveId, userId

Day
  tripId, date, sectorId?, isRest (bool), notes

PlanItem                              // an objective is planned on at most one day
  dayId, objectiveId, orderKey        // orderKey: fractional index for drag-reorder without renumbering

GearItem
  tripId, name, quantityNeeded, dayId? // null = whole trip

GearClaim
  gearItemId, userId, quantity

BetaItem
  objectiveId, type: video | photo | topo | note | youtube
  mediaId?        // video / photo / topo
  url?            // youtube (online only, never downloaded, D9)
  text?           // note
  durationSec?, createdBy

Media
  ownerId, mime, sizeBytes, remoteUrl?
  uploadState: queued | uploading | uploaded | failed
  // the local file path lives in device-only storage, not in the synced table

LogEntry                              // one record feeds BOTH the trip log and the personal logbook (D2)
  userId, style: attempt | sent | flash, attempts?, date
  tripId?, objectiveId?               // both null = "outside a trip"
  snapshot: name, grade, crag         // copied at log time so the personal logbook survives trip deletion
```

## Device-only tables (never synced)

```text
TripDownload   tripId, state: idle | saving | paused | done | failed, bytesDone, bytesTotal, pausedReason (storageFull | offline | wifiOnly | user)
MediaCache     mediaId, localPath, bytes, state: queued | saving | saved | failed
Outbox         pending local changes waiting to sync (if the chosen sync engine doesn't provide one)
```

The crew screen shows each climber's offline status ("Saved", "Saving 62%"). Each device publishes a small `MemberDeviceStatus { tripId, userId, savedBytes, totalBytes, updatedAt }` when online.

## Derived values (computed, not stored)

- Trip progress "7/18 sent": distinct objectives with at least one sent/flash entry ÷ objectives in the trip.
- "6 unplanned": objectives without a PlanItem.
- Gear "5/6": items whose claimed quantity ≥ needed.
- Grade pyramid: count of sent/flash entries per band.
- Recap: totals per climber, per day, hardest grade (max sortIndex).
