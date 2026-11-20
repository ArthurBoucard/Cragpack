-- Cragpack · Phase 2 · sync conflicts (tech spec §3). Run after 003_crew.sql. Safe to re-run.
--
-- The one real conflict: two climbers move the same objective to different days while offline. The first move
-- to reach the server wins; the phone of the second sees Sync conflict (19), which names who moved it and when.
-- The server records who: the phone can't be trusted to say.
-- ponytail: "first to reach the server" is checked on the phone (each sync pulls before it pushes); a move landing in
-- the second between someone's pull and push is overwritten without a sheet. A server-side check of the base day
-- would close it if it ever shows up.

alter table plan_item add column if not exists updated_by uuid;

create or replace function stamp_mover() returns trigger language plpgsql as $$
begin
    new.updated_by := coalesce(auth.uid(), new.updated_by);
    return new;
end $$;

drop trigger if exists stamp_mover on plan_item;
create trigger stamp_mover before insert or update on plan_item for each row execute function stamp_mover();
