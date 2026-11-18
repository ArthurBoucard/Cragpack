# 01 · Product brief

## One-liner

Cragpack is a collaborative trip planner for climbers. You pick your objectives, plan each day, take the beta offline, and see afterwards what the group sent.

## The problem

Planning a climbing trip is scattered today:
- Objectives live in a notes app.
- Beta videos sit in Instagram saves.
- Guidebook photos are buried in a camera roll.
- Gear is sorted out in a group chat.

At the crag there is often no signal, so half of this is out of reach just when it's needed.

## Who it is for

- Small groups of friends (2–8 people) going on bouldering or sport/trad trips, from a weekend to a few weeks.
- People who plan ahead, from intermediate to advanced, who keep a tick list and research their projects.
- Also solo climbers. Thanks to local-first use, someone can plan a trip alone without creating an account.

## Core features

1. **Trips.** Each trip has a name, crag, dates and cover photo. You invite friends with a link, and everyone in the trip can see and edit the plan.
2. **Wishlist (objectives).** Boulders and routes with name, style (boulder / sport / trad), grade, sector and an optional location pin. Each climber stars what they want to try. The list can be filtered by sector, grade or person.
3. **Day-by-day plan.** Each day has a sector or is a rest day, plus notes. You drag objectives from the unplanned tray onto a day, or tap + to add them.
4. **Offline beta pack.** Each objective can hold videos, photos, topo scans and notes. "Save offline" downloads the whole trip. YouTube links play online only.
5. **Gear.** List what the trip or a single day needs, and who brings what. Missing gear is flagged, and a "Bring 1" button lets someone fill the gap.
6. **Logbooks and recap.**
   - Each climber logs Attempt, Sent or Flash on an objective.
   - **Trip log:** everyone's sends for one trip.
   - **My logbook:** a personal logbook across all trips, plus climbs logged outside any trip.
   - **Trip recap:** grade pyramid, sends per day and per climber, and a shareable card.

## Design direction

- Mobile first and usable outdoors: high contrast, big tap targets, dark theme plus a sunlight theme.
- Offline first: download and sync status is always visible.
- Earthy palette (rock, sandstone) with one bright accent for actions and ticks.
- Grades shown as coloured chips, like the colour circuits in a climbing gym.

## Out of scope for v1

Map view, importing crag data from 27crags / theCrag / Vertical-Life / OpenBeta, weather and rest-day suggestions, and a public social layer. See `07-build-plan.md`.
