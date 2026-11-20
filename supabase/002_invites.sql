-- Cragpack · Phase 2 · invite links (tech spec §5). Run after 001_sync.sql. Safe to re-run.
--
-- One live link per trip. Reset revokes it and makes a new one: old links show Invite expired (17).
-- Links don't expire on their own; only organisers reset them (decided 2026-11-20).
-- The table has no policies, so the phone can't read it directly: everything goes through these functions.

create table if not exists invite (
    token text primary key,
    trip_id uuid not null references trip,
    created_by uuid not null,
    created_at timestamptz not null default now(),
    revoked_at timestamptz
);
create unique index if not exists invite_live on invite (trip_id) where revoked_at is null;
alter table invite enable row level security;

-- 16 hex characters (64 random bits): short enough to read, too many to guess.
create or replace function new_invite_token() returns text language sql volatile as $$
    select left(encode(sha256(convert_to(gen_random_uuid()::text, 'utf8')), 'hex'), 16)
$$;

-- The trip's live link token, made the first time someone asks. Any member can invite.
create or replace function invite_token(trip uuid) returns text language plpgsql security definer set search_path = public as $$
declare t text;
begin
    if not is_member(trip) then
        raise exception 'Only the trip''s crew can invite' using errcode = '42501';
    end if;
    insert into invite (token, trip_id, created_by) values (new_invite_token(), trip, auth.uid())
        on conflict (trip_id) where revoked_at is null do nothing;
    select token into t from invite where trip_id = trip and revoked_at is null;
    return t;
end $$;

-- Trip settings → Reset: the old link stops working.
create or replace function reset_invite(trip uuid) returns text language plpgsql security definer set search_path = public as $$
begin
    if not is_organiser(trip) then
        raise exception 'Only organisers can reset the link' using errcode = '42501';
    end if;
    update invite set revoked_at = now() where trip_id = trip and revoked_at is null;
    return invite_token(trip);
end $$;

-- What Join (10) shows before anyone signs in: callable without an account, by whoever holds the link.
-- {"expired": true} for a reset link or a deleted trip; null for a token that never existed.
create or replace function invite_preview(token text) returns jsonb language sql stable security definer set search_path = public as $$
    select case when i.revoked_at is not null or t.deleted_at is not null then jsonb_build_object('expired', true)
    else jsonb_build_object(
        'expired', false,
        'trip_id', t.id, 'name', t.name, 'crag', t.crag, 'start_date', t.start_date, 'end_date', t.end_date,
        -- "Léa invited you": the trip's longest-standing organiser (any climber can share the one link).
        'inviter', coalesce((
            select jsonb_build_object('name', coalesce(p.name, ''), 'color_index', m.color_index)
            from trip_member m left join profile p on p.id = m.user_id
            where m.trip_id = t.id and m.role = 'organiser' and m.deleted_at is null order by m.joined_at limit 1
        ), jsonb_build_object('name', '', 'color_index', 1)),
        'crew', coalesce((
            select jsonb_agg(jsonb_build_object('name', coalesce(p.name, ''), 'color_index', m.color_index) order by m.joined_at)
            from trip_member m left join profile p on p.id = m.user_id
            where m.trip_id = t.id and m.deleted_at is null
        ), '[]')
    ) end
    from invite i join trip t on t.id = i.trip_id
    where i.token = invite_preview.token
$$;

-- Join with a live link. Returns the trip id; the next pull brings the whole trip.
-- Joining again after leaving puts you back; colours follow join order, 1–5 then repeat.
create or replace function join_trip(token text) returns uuid language plpgsql security definer set search_path = public as $$
declare
    joining uuid;
    joined bigint := (extract(epoch from now()) * 1000)::bigint;
begin
    if auth.uid() is null then
        raise exception 'Sign in to join' using errcode = '42501';
    end if;
    select i.trip_id into joining from invite i join trip t on t.id = i.trip_id
        where i.token = join_trip.token and i.revoked_at is null and t.deleted_at is null;
    if joining is null then
        raise exception 'This link has expired' using errcode = 'P0001', hint = 'invite_expired';
    end if;
    perform pg_advisory_xact_lock(hashtext(joining::text)); -- one join per trip at a time: colours stay in order
    -- A removed climber can come back while the link is live; an organiser who wants them out resets it.
    insert into trip_member (trip_id, user_id, role, color_index, joined_at, updated_at)
        values (joining, auth.uid(), 'member', (select count(*) from trip_member m where m.trip_id = joining) % 5 + 1, joined, joined)
        -- Coming back after leaving or being removed: as a member, whatever the role was before.
        on conflict (trip_id, user_id) do update set
            role = case when trip_member.deleted_at is null then trip_member.role else 'member' end,
            deleted_at = null,
            updated_at = excluded.updated_at;
    return joining;
end $$;

-- Functions are open to everyone by default: only the preview is for people without an account.
revoke all on table invite from anon, authenticated;
revoke execute on function invite_token(uuid), reset_invite(uuid), join_trip(text), new_invite_token() from public, anon;
grant execute on function invite_token(uuid), reset_invite(uuid), join_trip(text) to authenticated;
grant execute on function invite_preview(text) to anon, authenticated;
