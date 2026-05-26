create table if not exists public.distribution_entitlements (
    id text primary key,
    batch_id text not null references public.distribution_batches(id) on delete cascade,
    account_id text not null references public.accounts(id),
    snapshot_shares integer not null,
    amount_minor integer not null,
    status text not null,
    created_at timestamptz default now() not null,
    unique (batch_id, account_id),
    constraint distribution_entitlements_shares_check check (snapshot_shares > 0),
    constraint distribution_entitlements_amount_check check (amount_minor >= 0),
    constraint distribution_entitlements_status_check check (status = any (array['claimable'::text, 'void'::text]))
);

create index if not exists idx_distribution_entitlements_account
    on public.distribution_entitlements(account_id, created_at desc);
