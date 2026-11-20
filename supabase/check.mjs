// Runs the supabase/*.sql files on an in-memory Postgres (PGlite) with a stand-in for Supabase's auth,
// then checks the access rules and the pull function. Nothing touches the real project.
//   npm i --no-save @electric-sql/pglite && node supabase/check.mjs
import { readFileSync, readdirSync } from 'node:fs'
import assert from 'node:assert/strict'

const { PGlite } = await import(process.env.PGLITE ?? '@electric-sql/pglite')
const db = new PGlite()
const dir = new URL('.', import.meta.url)

// Supabase gives us auth.users, auth.uid() (the caller, from the JWT) and the "authenticated" role.
await db.exec(`
  create schema auth;
  create table auth.users (id uuid primary key);
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  create role authenticated;
  create role anon;
  grant usage on schema auth to authenticated, anon;
`)
for (const file of readdirSync(dir).filter(f => f.endsWith('.sql')).sort()) {
  await db.exec(readFileSync(new URL(file, dir), 'utf8'))
}
// Supabase's defaults: both roles may use every table and function; the access rules and revokes decide.
await db.exec(`grant usage on schema public to authenticated, anon;
  grant select, insert, update on all tables in schema public to authenticated, anon;`)
for (const file of readdirSync(dir).filter(f => f.endsWith('.sql')).sort()) {
  await db.exec(readFileSync(new URL(file, dir), 'utf8')) // again, so each file's revokes win over the grants above
}

const LEA = '00000000-0000-4000-8000-00000000000a'
const TOM = '00000000-0000-4000-8000-00000000000b'
const TRIP = '00000000-0000-4000-8000-0000000000f1'
const OBJ = '00000000-0000-4000-8000-0000000000b1'
await db.exec(`insert into auth.users values ('${LEA}'), ('${TOM}')`)

/** Runs sql as the given signed-in user (or anon without one), like PostgREST does. */
async function as(user, sql, params = []) {
  await db.exec(`set role ${user ? 'authenticated' : 'anon'}; select set_config('request.jwt.claim.sub', '${user ?? ''}', false);`)
  try { return await db.query(sql, params) } finally { await db.exec('reset role') }
}
const fails = async (user, sql) => assert.rejects(as(user, sql), /row-level security|violates/, sql)

// Léa creates a trip, joins it as organiser, adds an objective and stars it.
await as(LEA, `insert into profile (id, name, updated_at) values ('${LEA}', 'Léa', 1)`)
await as(LEA, `insert into trip (id, name, crag, start_date, end_date, owner_id, created_at, updated_at)
  values ('${TRIP}', 'Font Easter', 'Fontainebleau', '2026-04-17', '2026-04-21', '${LEA}', 1, 1)
  on conflict (id) do update set name = excluded.name`) // the phone's push is always an upsert
await as(LEA, `insert into trip_member values ('${TRIP}', '${LEA}', 'organiser', 1, 1, 1)
  on conflict (trip_id, user_id) do update set role = excluded.role`)
await fails(LEA, `insert into objective (id, trip_id, name, style, grade_scale, grade_value, created_by, created_at, updated_at)
  values (gen_random_uuid(), '${TRIP}', 'Forged', 'boulder', 'Font', '6A', '${TOM}', 1, 1)`) // made by someone else? no
await as(LEA, `insert into objective (id, trip_id, name, style, grade_scale, grade_value, created_by, created_at, updated_at)
  values ('${OBJ}', '${TRIP}', 'La Joker', 'boulder', 'Font', '7A', '${LEA}', 1, 1)`)
await as(LEA, `insert into interest (objective_id, user_id, updated_at) values ('${OBJ}', '${LEA}', 1)`)
assert.equal((await as(LEA, `select trip_id from interest`)).rows[0].trip_id, TRIP, 'interest takes its trip from the objective')

// An edit is pushed as an update of just the changed fields (PostgREST PATCH), like the phone does.
assert.equal((await as(LEA, `update trip set name = 'Font Easter!', updated_at = 2 where id = '${TRIP}'`)).affectedRows, 1)
await assert.rejects(as(LEA, `update trip set owner_id = '${TOM}' where id = '${TRIP}'`), /owner_id can't change/)

// A logged climb can be undone (deleted_at) but not rewritten.
const LOG = '00000000-0000-4000-8000-0000000000c1'
await as(LEA, `insert into log_entry (id, user_id, style, date, trip_id, objective_id, snapshot_name, snapshot_grade_scale,
  snapshot_grade_value, snapshot_crag, created_at, updated_at) values ('${LOG}', '${LEA}', 'sent', '2026-04-18', '${TRIP}', '${OBJ}',
  'La Joker', 'Font', '7A', 'Fontainebleau', 1, 1) on conflict (id) do update set style = excluded.style`)
assert.equal((await as(LEA, `update log_entry set deleted_at = 3, updated_at = 3 where id = '${LOG}'`)).affectedRows, 1)
await assert.rejects(as(LEA, `update log_entry set style = 'flash' where id = '${LOG}'`), /only be undone/)

// Tom isn't in the trip: he sees nothing and can't write into it or add himself.
assert.equal((await as(TOM, `select * from trip`)).rows.length, 0)
assert.equal((await as(TOM, `select * from objective`)).rows.length, 0)
assert.equal((await as(TOM, `select * from profile where id = '${LEA}'`)).rows.length, 0)
await fails(TOM, `insert into trip_member values ('${TRIP}', '${TOM}', 'member', 2, 1, 1)`)
await fails(TOM, `insert into sector (id, trip_id, name, created_at, updated_at) values (gen_random_uuid(), '${TRIP}', 'Bas Cuvier', 1, 1)`)
await fails(TOM, `insert into interest (objective_id, user_id, updated_at) values ('${OBJ}', '${TOM}', 1)`)
assert.equal((await as(TOM, `update trip set name = 'Mine' where id = '${TRIP}'`)).affectedRows ?? 0, 0)
await fails(TOM, `insert into trip (id, name, crag, start_date, end_date, owner_id, created_at, updated_at)
  values (gen_random_uuid(), 'x', 'x', '2026-01-01', '2026-01-01', '${LEA}', 1, 1)`)

// Léa can't star or claim on someone else's behalf.
await fails(LEA, `insert into interest (objective_id, user_id, updated_at) values ('${OBJ}', '${TOM}', 2)`)

// Pull: everything on a new trip; nothing new after the cursor; Tom gets nothing.
const pull = async (user, cursors) => (await as(user, `select sync_pull($1::jsonb) as r`, [JSON.stringify(cursors)])).rows[0].r
const first = await pull(LEA, {})
assert.deepEqual(first.trips, [TRIP])
assert.equal(first.trip[0].name, 'Font Easter!')
assert.equal(first.objective.length, 1)
assert.equal(first.interest.length, 1)
assert.equal(first.profile[0].name, 'Léa')
// Inside the 30 s overlap the same rows come again (harmless: the phone upserts them)…
const again = await pull(LEA, { [TRIP]: first.cursor, me: first.cursor })
assert.equal(again.trip.length, 1)
// …and after it, nothing has changed.
const later = await pull(LEA, { [TRIP]: new Date().toISOString(), me: new Date().toISOString() })
assert.equal(later.trip.length + later.objective.length, 0)
const tom = await pull(TOM, {})
assert.deepEqual([tom.trips, tom.trip, tom.objective], [[], [], []])

// ── Invites (002) ──
const fn = async (user, sql) => Object.values((await as(user, sql)).rows[0])[0]
const token = await fn(LEA, `select invite_token('${TRIP}')`)
assert.equal(await fn(LEA, `select invite_token('${TRIP}')`), token, 'one live link per trip')
await assert.rejects(as(TOM, `select invite_token('${TRIP}')`), /crew can invite/)
await assert.rejects(as(null, `select join_trip('${token}')`), /permission denied|Sign in/)
await assert.rejects(as(null, `select * from invite`), /permission denied/)

const preview = await fn(null, `select invite_preview('${token}')`) // no account needed to see it
assert.deepEqual([preview.expired, preview.name, preview.inviter.name, preview.inviter.color_index, preview.crew.length], [false, 'Font Easter!', 'Léa', 1, 1])
assert.equal(await fn(null, `select invite_preview('nope')`), null)

assert.equal(await fn(TOM, `select join_trip('${token}')`), TRIP)
assert.equal(await fn(TOM, `select join_trip('${token}')`), TRIP, 'joining twice is harmless')
assert.equal((await as(TOM, `select * from objective`)).rows.length, 1, 'a new member sees the trip')
const tomPull = await pull(TOM, {})
assert.deepEqual([tomPull.trips, tomPull.objective.length, tomPull.profile.length], [[TRIP], 1, 1])
assert.equal((await as(TOM, `select color_index from trip_member where user_id = '${TOM}'`)).rows[0].color_index, 2)

await assert.rejects(as(TOM, `select reset_invite('${TRIP}')`), /Only organisers/)

// A member can't promote themselves, move their membership to another trip, or undo a removal.
await assert.rejects(as(TOM, `update trip_member set role = 'organiser' where user_id = '${TOM}'`), /Only organisers/)
await assert.rejects(as(TOM, `update trip_member set trip_id = gen_random_uuid() where user_id = '${TOM}'`), /trip_id can't change/)
await as(LEA, `update trip_member set deleted_at = 9 where user_id = '${TOM}'`) // the organiser removes Tom
assert.equal((await as(TOM, `select * from objective`)).rows.length, 0, 'removed: the trip is gone')
await assert.rejects(as(TOM, `update trip_member set deleted_at = null where user_id = '${TOM}'`), /invite link/)
assert.equal(await fn(TOM, `select join_trip('${token}')`), TRIP, '…until he joins again with the link')
const fresh = await fn(LEA, `select reset_invite('${TRIP}')`)
assert.notEqual(fresh, token)
assert.equal((await fn(null, `select invite_preview('${token}')`)).expired, true)
await assert.rejects(as(TOM, `select join_trip('${token}')`), /expired/)

// ── Crew (003) ──
const role = async (user) => (await db.query(`select role, deleted_at from trip_member where trip_id = '${TRIP}' and user_id = '${user}'`)).rows[0]
await assert.rejects(as(TOM, `select make_organiser('${TRIP}', '${TOM}')`), /Only organisers/)
await as(LEA, `select make_organiser('${TRIP}', '${TOM}')`)
assert.equal((await role(TOM)).role, 'organiser')

await as(LEA, `select leave_trip('${TRIP}')`)
assert.equal((await as(LEA, `select * from objective`)).rows.length, 0, 'left: the trip is gone')
await assert.rejects(as(LEA, `select remove_climber('${TRIP}', '${TOM}')`), /Only organisers/)

await fn(LEA, `select join_trip('${fresh}')`) // back as a plain member
await assert.rejects(as(LEA, `update trip set deleted_at = 1 where id = '${TRIP}'`), /Only organisers can delete/)
await assert.rejects(as(TOM, `select remove_climber('${TRIP}', '${TOM}')`), /Leave trip/)

await as(TOM, `select leave_trip('${TRIP}')`) // the last organiser goes: Léa, the longest-standing climber, takes over
assert.equal((await role(LEA)).role, 'organiser')

await as(LEA, `select delete_account()`) // Léa was the last climber: the trip goes with her account
assert.notEqual((await db.query(`select deleted_at from trip where id = '${TRIP}'`)).rows[0].deleted_at, null)
assert.notEqual((await db.query(`select deleted_at from log_entry where id = '${LOG}'`)).rows[0].deleted_at, null)
assert.equal((await db.query(`select * from auth.users where id = '${LEA}'`)).rows.length, 0)
assert.equal((await db.query(`select * from profile where id = '${LEA}'`)).rows.length, 0)

console.log('supabase check: all good')
