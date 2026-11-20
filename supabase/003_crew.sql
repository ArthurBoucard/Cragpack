-- Cragpack · Phase 2 · crew management and account deletion. Run after 002_invites.sql. Safe to re-run.
--
-- Crew changes go through these functions so the rules live in one place: organisers make organisers and
-- remove climbers; anyone can leave; a trip with climbers always keeps an organiser (the longest-standing
-- climber takes over when the last one goes). Leaving and removal are soft (deleted_at), so every phone hears.

create or replace function now_ms() returns bigint language sql stable as $$ select (extract(epoch from now()) * 1000)::bigint $$;

-- Crew changes to one trip happen one at a time (same lock as join_trip), so two organisers removing each other
-- or leaving together can't leave the trip without one.
create or replace function lock_crew(trip uuid) returns void language sql as $$ select pg_advisory_xact_lock(hashtext(trip::text)) $$;

-- If a trip has climbers but no organiser left, the one who joined first becomes organiser.
create or replace function keep_an_organiser(trip uuid) returns void language plpgsql security definer set search_path = public as $$
declare stamp bigint := now_ms();
begin
    if not exists (select 1 from trip_member where trip_id = trip and role = 'organiser' and deleted_at is null) then
        update trip_member set role = 'organiser', updated_at = stamp
        where trip_id = trip and user_id = (
            select user_id from trip_member where trip_id = trip and deleted_at is null order by joined_at limit 1
        );
    end if;
end $$;

create or replace function make_organiser(trip uuid, climber uuid) returns void language plpgsql security definer set search_path = public as $$
begin
    perform lock_crew(trip);
    if not is_organiser(trip) then
        raise exception 'Only organisers can do that' using errcode = '42501';
    end if;
    update trip_member set role = 'organiser', updated_at = now_ms()
    where trip_id = trip and user_id = climber and deleted_at is null;
end $$;

create or replace function remove_climber(trip uuid, climber uuid) returns void language plpgsql security definer set search_path = public as $$
begin
    perform lock_crew(trip);
    if not is_organiser(trip) then
        raise exception 'Only organisers can do that' using errcode = '42501';
    end if;
    if climber = auth.uid() then
        raise exception 'Use Leave trip to leave' using errcode = '42501';
    end if;
    update trip_member set deleted_at = now_ms(), updated_at = now_ms() where trip_id = trip and user_id = climber and deleted_at is null;
end $$;

-- The trip leaves your phone; your sends stay in your logbook (D2). The last one out deletes it: nobody could
-- look after an empty trip, and its link would let strangers in.
create or replace function leave_trip(trip uuid) returns void language plpgsql security definer set search_path = public as $$
begin
    perform lock_crew(trip);
    perform leave_as(trip, auth.uid());
end $$;

create or replace function leave_as(leaving uuid, climber uuid) returns void language plpgsql security definer set search_path = public as $$
declare stamp bigint := now_ms();
begin
    update trip_member set deleted_at = stamp, updated_at = stamp where trip_id = leaving and user_id = climber and deleted_at is null;
    if exists (select 1 from trip_member where trip_id = leaving and deleted_at is null) then
        perform keep_an_organiser(leaving);
    else
        update trip set deleted_at = stamp, updated_at = stamp where id = leaving and deleted_at is null;
        update invite set revoked_at = now() where trip_id = leaving and revoked_at is null;
    end if;
end $$;

-- Only organisers delete a trip (screen 30); every other edit is open to the whole crew.
create or replace function trip_delete_guard() returns trigger language plpgsql as $$
begin
    if current_user = 'authenticated' and old.deleted_at is null and new.deleted_at is not null and not is_organiser(old.id) then
        raise exception 'Only organisers can delete the trip' using errcode = '42501';
    end if;
    return new;
end $$;
drop trigger if exists delete_guard on trip;
create trigger delete_guard before update on trip for each row execute function trip_delete_guard();

-- Settings → Delete account (tech spec §2; Apple and Google require it). Your climbs, stars, gear claims and
-- name go (crew phones keep the name they already had); trips you shared stay with the crew (a trip only you were in is deleted); then the account itself.
create or replace function delete_account() returns void language plpgsql security definer set search_path = public as $$
declare
    me uuid := auth.uid();
    stamp bigint := now_ms();
    t uuid;
begin
    if me is null then
        raise exception 'Sign in first' using errcode = '42501';
    end if;
    -- In trip order, like any other caller would lock them: two accounts deleted at once can't deadlock.
    for t in select trip_id from trip_member where user_id = me and deleted_at is null order by trip_id loop
        perform lock_crew(t);
        perform leave_as(t, me);
    end loop;
    -- Tombstones, so the crew's phones drop them too.
    -- ponytail: the rows stay on the server as tombstones; purge them with a scheduled job before release (GDPR).
    update log_entry set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    update interest set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    update gear_claim set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    delete from profile where id = me;
    delete from auth.users where id = me;
end $$;

-- Supabase grants every new function to anon and authenticated: take back the internal ones from everybody.
revoke execute on function keep_an_organiser(uuid), leave_as(uuid, uuid), lock_crew(uuid), now_ms() from public, anon, authenticated;
revoke execute on function make_organiser(uuid, uuid), remove_climber(uuid, uuid), leave_trip(uuid), delete_account() from public, anon;
grant execute on function make_organiser(uuid, uuid), remove_climber(uuid, uuid), leave_trip(uuid), delete_account() to authenticated;
