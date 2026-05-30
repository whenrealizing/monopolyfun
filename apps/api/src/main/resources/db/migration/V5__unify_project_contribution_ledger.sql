alter type public.ledger_reason add value if not exists 'work_thread';
alter type public.ledger_reason add value if not exists 'validation_submitter';
alter type public.ledger_reason add value if not exists 'validation_validator';

alter table public.shares_ledger drop constraint if exists shares_ledger_source_type_check;
alter table public.shares_ledger
    add constraint shares_ledger_source_type_check
        check (source_type = any (array['order'::text, 'proposal_pack'::text, 'work_thread'::text, 'validation_reward'::text]));

alter table public.contribution_ledger
    alter column work_thread_id drop not null,
    add column if not exists source_type text,
    add column if not exists source_id text,
    add column if not exists contribution_role text,
    add column if not exists contribution_weight numeric(18, 6),
    add column if not exists metadata jsonb;

update public.contribution_ledger
set source_type = coalesce(source_type, 'work_thread'),
    source_id = coalesce(source_id, work_thread_id),
    contribution_role = coalesce(contribution_role, 'assignee'),
    contribution_weight = coalesce(contribution_weight, task_value::numeric),
    metadata = coalesce(metadata, '{}'::jsonb)
where source_type is null
   or source_id is null
   or contribution_role is null
   or contribution_weight is null
   or metadata is null;

alter table public.contribution_ledger
    alter column source_type set not null,
    alter column source_id set not null,
    alter column contribution_role set not null,
    alter column contribution_weight set default 0,
    alter column contribution_weight set not null,
    alter column metadata set default '{}'::jsonb,
    alter column metadata set not null;

alter table public.contribution_ledger drop constraint if exists contribution_ledger_source_type_check;
alter table public.contribution_ledger
    add constraint contribution_ledger_source_type_check
        check (source_type = any (array['order'::text, 'work_thread'::text, 'validation_reward'::text]));

create unique index if not exists contribution_ledger_source_account_role_idx
    on public.contribution_ledger (project_id, source_type, source_id, account_id, contribution_role);
