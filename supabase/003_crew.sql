-- Cragpack · Phase 2 · crew management and account deletion. Run after 002_invites.sql. Safe to re-run.
--
-- Crew changes go through these functions so the rules live in one place: organisers make organisers and
-- remove climbers; anyone can leave; a trip with climbers always keeps an organiser (the longest-standing
-- climber takes over when the last one goes). Leaving and removal are soft (deleted_at), so every phone hears.

-- If a trip has climbers but no organiser left, the one who joined first becomes organiser.
create or replace function keep_an_organiser(trip uuid) returns void language plpgsql security definer set search_path = public as $$
declare stamp bigint := (extract(epoch from now()) * 1000)::bigint;
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
    if not is_organiser(trip) then
        raise exception 'Only organisers can do that' using errcode = '42501';
    end if;
    update trip_member set role = 'organiser', updated_at = (extract(epoch from now()) * 1000)::bigint
    where trip_id = trip and user_id = climber and deleted_at is null;
end $$;

create or replace function remove_climber(trip uuid, climber uuid) returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_organiser(trip) then
        raise exception 'Only organisers can do that' using errcode = '42501';
    end if;
    if climber = auth.uid() then
        raise exception 'Use Leave trip to leave' using errcode = '42501';
    end if;
    update trip_member set deleted_at = (extract(epoch from now()) * 1000)::bigint, updated_at = (extract(epoch from now()) * 1000)::bigint
    where trip_id = trip and user_id = climber and deleted_at is null;
end $$;

-- The trip leaves your phone; your sends stay in your logbook (D2).
create or replace function leave_trip(trip uuid) returns void language plpgsql security definer set search_path = public as $$
declare stamp bigint := (extract(epoch from now()) * 1000)::bigint;
begin
    update trip_member set deleted_at = stamp, updated_at = stamp where trip_id = trip and user_id = auth.uid() and deleted_at is null;
    perform keep_an_organiser(trip);
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
-- name go (for the crew too); trips you shared stay with the crew (a trip only you were in is deleted); then the account itself.
create or replace function delete_account() returns void language plpgsql security definer set search_path = public as $$
declare
    me uuid := auth.uid();
    stamp bigint := (extract(epoch from now()) * 1000)::bigint;
    t uuid;
begin
    if me is null then
        raise exception 'Sign in first' using errcode = '42501';
    end if;
    for t in select trip_id from trip_member where user_id = me and deleted_at is null loop
        update trip_member set deleted_at = stamp, updated_at = stamp where trip_id = t and user_id = me;
        if exists (select 1 from trip_member where trip_id = t and deleted_at is null) then
            perform keep_an_organiser(t);
        else
            update trip set deleted_at = stamp, updated_at = stamp where id = t and deleted_at is null;
        end if;
    end loop;
    -- Tombstones, so the crew's phones drop them too.
    -- ponytail: the rows stay on the server as tombstones; purge them with a scheduled job before release (GDPR).
    update log_entry set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    update interest set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    update gear_claim set deleted_at = stamp, updated_at = stamp where user_id = me and deleted_at is null;
    delete from profile where id = me;
    delete from auth.users where id = me;
end $$;

revoke execute on function keep_an_organiser(uuid), make_organiser(uuid, uuid), remove_climber(uuid, uuid), leave_trip(uuid), delete_account()
    from public, anon;
grant execute on function make_organiser(uuid, uuid), remove_climber(uuid, uuid), leave_trip(uuid), delete_account() to authenticated;
