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
  grant usage on schema auth to authenticated;
`)
for (const file of readdirSync(dir).filter(f => f.endsWith('.sql')).sort()) {
  await db.exec(readFileSync(new URL(file, dir), 'utf8'))
}
await db.exec(`grant usage on schema public to authenticated;
  grant select, insert, update on all tables in schema public to authenticated;
  grant execute on all functions in schema public to authenticated;`)

const LEA = '00000000-0000-4000-8000-00000000000a'
const TOM = '00000000-0000-4000-8000-00000000000b'
const TRIP = '00000000-0000-4000-8000-0000000000f1'
const OBJ = '00000000-0000-4000-8000-0000000000b1'
await db.exec(`insert into auth.users values ('${LEA}'), ('${TOM}')`)

/** Runs sql as the given signed-in user, like PostgREST does. */
async function as(user, sql, params = []) {
  await db.exec(`set role authenticated; select set_config('request.jwt.claim.sub', '${user}', false);`)
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
await as(LEA, `insert into objective (id, trip_id, name, style, grade_scale, grade_value, created_by, created_at, updated_at)
  values ('${OBJ}', '${TRIP}', 'La Joker', 'boulder', 'Font', '7A', '${LEA}', 1, 1)`)
await as(LEA, `insert into interest (objective_id, user_id, updated_at) values ('${OBJ}', '${LEA}', 1)`)
assert.equal((await as(LEA, `select trip_id from interest`)).rows[0].trip_id, TRIP, 'interest takes its trip from the objective')

// Upsert of a field (what the phone's push does) works for a member.
await as(LEA, `insert into trip (id, name, crag, start_date, end_date, owner_id, created_at, updated_at)
  values ('${TRIP}', 'Font Easter!', 'Fontainebleau', '2026-04-17', '2026-04-21', '${LEA}', 1, 2)
  on conflict (id) do update set name = excluded.name, updated_at = excluded.updated_at`)

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

console.log('supabase check: all good')
