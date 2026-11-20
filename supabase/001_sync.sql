-- Cragpack · Phase 2 · sync tables, access rules and the pull function.
-- Run once in the Supabase dashboard (SQL editor). Safe to re-run: it drops and recreates the functions and policies.
--
-- Tables mirror the phone's SQLite schema (shared/src/commonMain/sqldelight): same names and columns,
-- times in epoch milliseconds set by the phone. Two server-only columns on every table:
--   trip_id            which trip a row belongs to (filled from the parent row where the phone doesn't store it)
--   server_updated_at  when the row last reached the server: the pull cursor (tech spec §3)
-- Media files and their rows sync in Phase 3.

-- ── Tables ─────────────────────────────────────────────────────────────────────────

create table if not exists profile (
    id uuid primary key references auth.users on delete cascade,
    name text not null default '',
    updated_at bigint not null,
    server_updated_at timestamptz not null default now()
);

create table if not exists trip (
    id uuid primary key,
    trip_id uuid generated always as (id) stored,
    name text not null,
    crag text not null,
    start_date text not null,
    end_date text not null,
    cover_media_id uuid,
    owner_id uuid not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists trip_member (
    trip_id uuid not null references trip,
    user_id uuid not null,
    role text not null,
    color_index bigint not null,
    joined_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now(),
    primary key (trip_id, user_id)
);

create table if not exists sector (
    id uuid primary key,
    trip_id uuid not null references trip,
    name text not null,
    approach_note text,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists day (
    id uuid primary key,
    trip_id uuid not null references trip,
    date text not null,
    sector_id uuid,
    is_rest bigint not null default 0,
    notes text not null default '',
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists objective (
    id uuid primary key,
    trip_id uuid not null references trip,
    name text not null,
    style text not null,
    grade_scale text not null,
    grade_value text not null,
    sector_id uuid,
    lat double precision,
    lng double precision,
    created_by uuid not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists interest (
    objective_id uuid not null references objective,
    user_id uuid not null,
    trip_id uuid,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now(),
    primary key (objective_id, user_id)
);

create table if not exists plan_item (
    objective_id uuid primary key references objective,
    trip_id uuid,
    day_id uuid not null,
    order_key text not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists beta_item (
    id uuid primary key,
    objective_id uuid not null references objective,
    trip_id uuid,
    type text not null,
    media_id uuid,
    url text,
    text text,
    duration_sec bigint,
    created_by uuid not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists gear_item (
    id uuid primary key,
    trip_id uuid not null references trip,
    name text not null,
    quantity_needed bigint not null,
    day_id uuid,
    created_by uuid not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

create table if not exists gear_claim (
    gear_item_id uuid not null references gear_item,
    user_id uuid not null,
    trip_id uuid,
    quantity bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now(),
    primary key (gear_item_id, user_id)
);

-- trip_id is null for a climb logged outside a trip; those rows belong to their climber only (D2).
create table if not exists log_entry (
    id uuid primary key,
    user_id uuid not null,
    style text not null,
    attempts bigint,
    date text not null,
    trip_id uuid references trip,
    objective_id uuid,
    snapshot_name text not null,
    snapshot_grade_scale text not null,
    snapshot_grade_value text not null,
    snapshot_crag text not null,
    created_at bigint not null,
    updated_at bigint not null,
    deleted_at bigint,
    server_updated_at timestamptz not null default now()
);

-- Every table the phone syncs, parents first (the order the phone pushes in: SyncTables.kt).
create or replace function synced_tables() returns text[] language sql immutable as $$
    select array['profile', 'trip', 'trip_member', 'sector', 'day', 'objective', 'interest', 'plan_item', 'beta_item', 'gear_item', 'gear_claim', 'log_entry']
$$;

-- ── Server time and trip_id on every write ─────────────────────────────────────────

create or replace function touch() returns trigger language plpgsql as $$
begin
    new.server_updated_at := now();
    return new;
end $$;

-- Rows hanging off an objective or a gear item get their trip from the parent, so access rules can check it.
create or replace function fill_trip_from_objective() returns trigger language plpgsql security definer set search_path = public as $$
begin
    new.trip_id := (select o.trip_id from objective o where o.id = new.objective_id);
    return new;
end $$;

create or replace function fill_trip_from_gear() returns trigger language plpgsql security definer set search_path = public as $$
begin
    new.trip_id := (select g.trip_id from gear_item g where g.id = new.gear_item_id);
    return new;
end $$;

-- A log entry is append-only (D2, tech spec §3): after it's written only Undo (deleted_at) changes it.
create or replace function log_entry_append_only() returns trigger language plpgsql as $$
begin
    if (to_jsonb(new) - array['deleted_at', 'updated_at', 'server_updated_at']) <> (to_jsonb(old) - array['deleted_at', 'updated_at', 'server_updated_at']) then
        raise exception 'A logged climb can only be undone, not changed' using errcode = '42501';
    end if;
    return new;
end $$;

-- Columns that never change once a row exists (trigger arguments): who made it, whose and which trip it is.
create or replace function keep_columns() returns trigger language plpgsql as $$
declare c text;
begin
    foreach c in array tg_argv loop
        if to_jsonb(new) -> c is distinct from to_jsonb(old) -> c then
            raise exception '% can''t change', c using errcode = '42501';
        end if;
    end loop;
    return new;
end $$;

-- Only organisers change roles, and a removed climber comes back only through an invite link (join_trip,
-- which runs as the function's owner, not as the climber).
create or replace function trip_member_guard() returns trigger language plpgsql as $$
begin
    if current_user = 'authenticated' and not is_organiser(old.trip_id) then
        if new.role is distinct from old.role then
            raise exception 'Only organisers change roles' using errcode = '42501';
        end if;
        if old.deleted_at is not null and new.deleted_at is null then
            raise exception 'Join again with an invite link' using errcode = '42501';
        end if;
    end if;
    return new;
end $$;

do $$
declare t text;
begin
    foreach t in array synced_tables() loop
        execute format('drop trigger if exists touch on %I', t);
        execute format('create trigger touch before insert or update on %I for each row execute function touch()', t);
        execute format('alter table %I enable row level security', t);
        if t <> 'profile' then
            execute format('create index if not exists %I on %I (trip_id, server_updated_at)', t || '_pull', t);
        end if;
    end loop;
    -- On update too: moving a star or plan item to another objective moves it to that objective's trip.
    foreach t in array array['interest', 'plan_item', 'beta_item'] loop
        execute format('drop trigger if exists fill_trip on %I', t);
        execute format('create trigger fill_trip before insert or update on %I for each row execute function fill_trip_from_objective()', t);
    end loop;
    drop trigger if exists fill_trip on gear_claim;
    create trigger fill_trip before insert or update on gear_claim for each row execute function fill_trip_from_gear();

    drop trigger if exists append_only on log_entry;
    create trigger append_only before update on log_entry for each row execute function log_entry_append_only();
    drop trigger if exists keep_columns on trip;
    create trigger keep_columns before update on trip for each row execute function keep_columns('owner_id');
    drop trigger if exists keep_columns on trip_member;
    create trigger keep_columns before update on trip_member for each row execute function keep_columns('trip_id', 'user_id');
    drop trigger if exists guard on trip_member;
    create trigger guard before update on trip_member for each row execute function trip_member_guard();
    foreach t in array array['objective', 'beta_item', 'gear_item'] loop
        execute format('drop trigger if exists keep_columns on %I', t);
        execute format('create trigger keep_columns before update on %I for each row execute function keep_columns(''created_by'')', t);
    end loop;
end $$;

create index if not exists log_entry_mine on log_entry (user_id, server_updated_at);

-- ── Access rules: a member sees and edits only their trips (tech spec §3) ──────────

-- security definer: these read trip_member / trip without going through their own policies.
create or replace function is_member(t uuid) returns boolean language sql stable security definer set search_path = public as $$
    select exists (select 1 from trip_member where trip_id = t and user_id = auth.uid() and deleted_at is null)
$$;

create or replace function is_organiser(t uuid) returns boolean language sql stable security definer set search_path = public as $$
    select exists (select 1 from trip_member where trip_id = t and user_id = auth.uid() and role = 'organiser' and deleted_at is null)
$$;

create or replace function owns_trip(t uuid) returns boolean language sql stable security definer set search_path = public as $$
    select exists (select 1 from trip where id = t and owner_id = auth.uid())
$$;

do $$
declare t text; p record;
begin
    -- Start clean so re-running the file never leaves an old, looser policy behind.
    for p in select policyname, tablename from pg_policies where schemaname = 'public' and tablename = any(synced_tables()) loop
        execute format('drop policy %I on %I', p.policyname, p.tablename);
    end loop;
    -- Shared trip content: any member reads and writes. No deletes: rows are soft-deleted (deleted_at).
    foreach t in array array['sector', 'day', 'objective', 'plan_item', 'beta_item', 'gear_item'] loop
        execute format('create policy member_read on %I for select using (is_member(trip_id))', t);
        -- Rows that name their maker must name the caller.
        execute format('create policy member_add on %I for insert with check (is_member(trip_id)%s)', t,
            case when t in ('objective', 'beta_item', 'gear_item') then ' and created_by = auth.uid()' else '' end);
        execute format('create policy member_edit on %I for update using (is_member(trip_id)) with check (is_member(trip_id))', t);
    end loop;
    -- Per-climber rows: everyone in the trip sees them, only their climber writes them.
    foreach t in array array['interest', 'gear_claim'] loop
        execute format('create policy member_read on %I for select using (is_member(trip_id))', t);
        execute format('create policy own_add on %I for insert with check (user_id = auth.uid() and is_member(trip_id))', t);
        execute format('create policy own_edit on %I for update using (user_id = auth.uid()) with check (user_id = auth.uid() and is_member(trip_id))', t);
    end loop;
end $$;

create policy member_read on trip for select using (is_member(id) or owner_id = auth.uid());
create policy owner_add on trip for insert with check (owner_id = auth.uid());
create policy member_edit on trip for update using (is_member(id)) with check (is_member(id));

-- The creator adds themselves; everyone else joins through an invite (Phase 2, invite step).
create policy member_read on trip_member for select using (is_member(trip_id) or user_id = auth.uid());
create policy creator_add on trip_member for insert with check (user_id = auth.uid() and owns_trip(trip_id));
create policy self_or_organiser_edit on trip_member for update
    using (user_id = auth.uid() or is_organiser(trip_id)) with check (user_id = auth.uid() or is_organiser(trip_id));

-- Log entries are append-only per climber (D2): the crew reads them, only their climber writes them.
create policy read on log_entry for select using (user_id = auth.uid() or (trip_id is not null and is_member(trip_id)));
create policy own_add on log_entry for insert with check (user_id = auth.uid() and (trip_id is null or is_member(trip_id)));
create policy own_edit on log_entry for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- A name is visible to yourself and to the people you share a trip with.
create policy read on profile for select using (
    id = auth.uid() or exists (
        select 1 from trip_member a join trip_member b on a.trip_id = b.trip_id
        where a.user_id = auth.uid() and b.user_id = profile.id and a.deleted_at is null
    )
);
create policy own_add on profile for insert with check (id = auth.uid());
create policy own_edit on profile for update using (id = auth.uid()) with check (id = auth.uid());

-- ── Pull: what changed on my trips since each trip's cursor ──────────────────────────
-- cursors: {"<trip id>": "<timestamptz>", "me": "<timestamptz>"}. A trip missing from it (just joined) comes in full.
-- Returns {"<table>": [rows…], "profile": [...], "trips": [my trip ids], "cursor": "<timestamptz>"}.
-- cursor is 30 s before now: a write still committing while this runs is fetched again next time (rows are upserted, so twice is fine).
-- ponytail: a write transaction longer than 30 s would be missed; pushes are single short statements, so none is.
-- Runs as the caller, so the access rules above still apply.
create or replace function sync_pull(cursors jsonb) returns jsonb language plpgsql stable set search_path = public as $$
declare
    trips uuid[] := array(select trip_id from trip_member where user_id = auth.uid() and deleted_at is null);
    result jsonb := jsonb_build_object('trips', to_jsonb(trips), 'cursor', now() - interval '30 seconds');
    t text;
    rows jsonb;
begin
    foreach t in array (synced_tables())[2:] loop -- every table but profile
        execute format(
            'select coalesce(jsonb_agg(to_jsonb(x) - ''server_updated_at''), ''[]'') from %I x
             where x.trip_id = any($1) and x.server_updated_at > coalesce(($2 ->> x.trip_id::text)::timestamptz, ''-infinity'')', t)
            into rows using trips, cursors;
        result := result || jsonb_build_object(t, rows);
    end loop;
    -- My own climbs outside a trip, and on trips I left: my personal logbook (D2).
    result := jsonb_set(result, '{log_entry}', (result -> 'log_entry') || coalesce((
        select jsonb_agg(to_jsonb(x) - 'server_updated_at') from log_entry x
        where x.user_id = auth.uid() and (x.trip_id is null or not x.trip_id = any(trips))
          and x.server_updated_at > coalesce((cursors ->> 'me')::timestamptz, '-infinity')
    ), '[]'));
    -- ponytail: every crew name on every pull; a cursor here when crews get big.
    result := result || jsonb_build_object('profile', coalesce((
        select jsonb_agg(jsonb_build_object('id', p.id, 'name', p.name, 'updated_at', p.updated_at)) from profile p
        where p.id in (select user_id from trip_member where trip_id = any(trips))
    ), '[]'));
    return result;
end $$;
