alter table public.work_items drop constraint if exists work_items_status_check;
alter table public.work_items
    add constraint work_items_status_check
        check (status = any (array['ready'::text, 'claimed'::text, 'submitted'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'rejected'::text, 'closed'::text]));

alter table public.work_runs drop constraint if exists work_runs_status_check;
alter table public.work_runs
    add constraint work_runs_status_check
        check (status = any (array['claimed'::text, 'running'::text, 'submitted'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'rejected'::text, 'resolved'::text, 'closed'::text]));

alter table public.work_reviews drop constraint if exists work_reviews_status_check;
alter table public.work_reviews
    add constraint work_reviews_status_check
        check (status = any (array['pending'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'rejected'::text, 'resolved'::text]));

alter table public.shares_ledger drop constraint if exists shares_ledger_source_type_check;
alter table public.shares_ledger
    add constraint shares_ledger_source_type_check
        check (source_type = any (array['order'::text, 'proposal_pack'::text, 'work_thread'::text]));

create table public.work_threads (
    id text primary key,
    thread_no text not null unique,
    project_id text not null references public.projects(id) on delete cascade,
    created_by_account_id text not null references public.accounts(id),
    assignee_account_id text references public.accounts(id),
    reviewer_account_id text references public.accounts(id),
    issue_url text,
    repo_ref text,
    title text not null,
    goal text not null,
    deliverables jsonb default '[]'::jsonb not null,
    acceptance_criteria jsonb default '[]'::jsonb not null,
    task_value integer not null,
    bounty_amount_minor integer default 0 not null,
    bounty_token text default 'USDC'::text not null,
    status text not null,
    created_at timestamptz default now() not null,
    updated_at timestamptz default now() not null,
    submitted_at timestamptz,
    accepted_at timestamptz,
    settled_at timestamptz,
    constraint work_threads_task_value_check check (task_value between 0 and 10000),
    constraint work_threads_bounty_amount_check check (bounty_amount_minor >= 0),
    constraint work_threads_status_check check (status = any (array['open'::text, 'running'::text, 'submitted'::text, 'accepted'::text, 'settled'::text, 'rejected'::text]))
);

create index idx_work_threads_project_status on public.work_threads(project_id, status, updated_at desc);
create index idx_work_threads_assignee_status on public.work_threads(assignee_account_id, status, updated_at desc);

create table public.work_results (
    id text primary key,
    result_no text not null unique,
    work_thread_id text not null references public.work_threads(id) on delete cascade,
    actor_account_id text not null references public.accounts(id),
    result_markdown text not null,
    summary text not null,
    pr_url text not null,
    test_summary text not null,
    changed_files jsonb default '[]'::jsonb not null,
    evidence_refs jsonb default '[]'::jsonb not null,
    runtime text default 'openclaw'::text not null,
    status text not null,
    created_at timestamptz default now() not null,
    constraint work_results_status_check check (status = any (array['submitted'::text, 'accepted'::text, 'rejected'::text]))
);

create index idx_work_results_thread_created on public.work_results(work_thread_id, created_at desc);

create table public.work_thread_reviews (
    id text primary key,
    review_no text not null unique,
    work_thread_id text not null references public.work_threads(id) on delete cascade,
    result_id text references public.work_results(id) on delete set null,
    reviewer_account_id text not null references public.accounts(id),
    decision text not null,
    reason text not null,
    created_at timestamptz default now() not null,
    constraint work_thread_reviews_decision_check check (decision = any (array['accept'::text, 'resubmit'::text, 'reject'::text]))
);

create index idx_work_thread_reviews_thread_created on public.work_thread_reviews(work_thread_id, created_at desc);

create table public.contribution_ledger (
    id text primary key,
    project_id text not null references public.projects(id) on delete cascade,
    work_thread_id text not null references public.work_threads(id) on delete cascade,
    result_id text references public.work_results(id) on delete set null,
    account_id text not null references public.accounts(id),
    task_value integer not null,
    shares integer not null,
    bounty_amount_minor integer default 0 not null,
    bounty_token text default 'USDC'::text not null,
    status text not null,
    created_at timestamptz default now() not null,
    constraint contribution_ledger_task_value_check check (task_value between 0 and 10000),
    constraint contribution_ledger_shares_check check (shares > 0),
    constraint contribution_ledger_bounty_check check (bounty_amount_minor >= 0),
    constraint contribution_ledger_status_check check (status = any (array['settled'::text, 'void'::text])),
    unique (work_thread_id, account_id)
);

create index idx_contribution_ledger_project_account on public.contribution_ledger(project_id, account_id, created_at desc);

create table public.project_revenue_addresses (
    id text primary key,
    project_id text not null references public.projects(id) on delete cascade,
    chain_id text not null,
    contract_address text not null,
    token_address text not null,
    status text not null,
    created_at timestamptz default now() not null,
    updated_at timestamptz default now() not null,
    unique (project_id, chain_id, contract_address),
    constraint project_revenue_addresses_status_check check (status = any (array['active'::text, 'paused'::text]))
);

create table public.distribution_batches (
    id text primary key,
    project_id text not null references public.projects(id) on delete cascade,
    period text not null,
    total_revenue_minor integer not null,
    total_snapshot_shares integer not null,
    merkle_root text not null,
    status text not null,
    created_at timestamptz default now() not null,
    updated_at timestamptz default now() not null,
    unique (project_id, period),
    constraint distribution_batches_revenue_check check (total_revenue_minor >= 0),
    constraint distribution_batches_shares_check check (total_snapshot_shares >= 0),
    constraint distribution_batches_status_check check (status = any (array['draft'::text, 'published'::text, 'closed'::text]))
);

create table public.distribution_claims (
    id text primary key,
    batch_id text not null references public.distribution_batches(id) on delete cascade,
    account_id text not null references public.accounts(id),
    wallet_address text not null,
    amount_minor integer not null,
    proof jsonb default '[]'::jsonb not null,
    tx_hash text,
    status text not null,
    created_at timestamptz default now() not null,
    updated_at timestamptz default now() not null,
    unique (batch_id, account_id),
    constraint distribution_claims_amount_check check (amount_minor >= 0),
    constraint distribution_claims_status_check check (status = any (array['claimable'::text, 'submitted'::text, 'claimed'::text, 'void'::text]))
);
