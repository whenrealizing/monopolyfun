-- Baseline schema generated after V4 on 2026-05-23.
-- 中文注释：压缩历史迁移后，新环境通过单一基线一次性创建当前业务 schema。
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.9 (Debian 17.9-1.pgdg12+1)
-- Dumped by pg_dump version 17.9 (Debian 17.9-1.pgdg12+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: execution_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.execution_mode AS ENUM (
    'human',
    'agent',
    'mixed'
);


--
-- Name: ledger_reason; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.ledger_reason AS ENUM (
    'work_order',
    'review_order',
    'proposal_pack_author',
    'proposal_pack_final_review',
    'proposal_pack_validator'
);


--
-- Name: listing_kind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.listing_kind AS ENUM (
    'work',
    'review'
);


--
-- Name: listing_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.listing_status AS ENUM (
    'open',
    'closed',
    'paused',
    'archived',
    'draft'
);


--
-- Name: market_member_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.market_member_role AS ENUM (
    'member'
);


--
-- Name: market_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.market_status AS ENUM (
    'active',
    'stalled'
);


--
-- Name: order_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.order_status AS ENUM (
    'claimed',
    'delivered',
    'accepted_open',
    'disputed',
    'final_accepted',
    'final_closed'
);


--
-- Name: payment_intent_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.payment_intent_status AS ENUM (
    'pending',
    'authorized',
    'captured',
    'refunded',
    'cancelled',
    'disputed',
    'failed'
);


--
-- Name: project_role_code; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.project_role_code AS ENUM (
    'system_ceo',
    'system_cto',
    'system_cfo'
);


--
-- Name: proof_asset_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.proof_asset_status AS ENUM (
    'pending',
    'uploaded',
    'verified',
    'quarantined',
    'failed',
    'cancelled'
);


--
-- Name: proof_kind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.proof_kind AS ENUM (
    'work_proof',
    'review_proof'
);


--
-- Name: review_decision; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.review_decision AS ENUM (
    'accept_original',
    'close_original'
);


--
-- Name: settlement_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.settlement_type AS ENUM (
    'money',
    'shares',
    'points',
    'mixed'
);


--
-- Name: share_issuer_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.share_issuer_type AS ENUM (
    'system',
    'project'
);


--
-- Name: share_release_request_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.share_release_request_status AS ENUM (
    'pending',
    'approved',
    'skipped'
);


--
-- Name: project_roles_membership_guard(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.project_roles_membership_guard() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NEW.role_code = 'member'::project_role_code THEN
    IF EXISTS (
      SELECT 1
      FROM project_roles concrete_role
      WHERE concrete_role.project_id = NEW.project_id
        AND concrete_role.account_id = NEW.account_id
        AND concrete_role.role_code <> 'member'::project_role_code
        AND (TG_OP = 'INSERT' OR concrete_role.id <> NEW.id)
    ) THEN
      RAISE EXCEPTION 'project member role is redundant for account with concrete role';
    END IF;
  ELSE
    -- 中文注释：写入真实职位时移除同账号 member，保证 project_roles 没有派生状态冗余。
    DELETE FROM project_roles
    WHERE project_id = NEW.project_id
      AND account_id = NEW.account_id
      AND role_code = 'member'::project_role_code
      AND (TG_OP = 'INSERT' OR id <> NEW.id);
  END IF;

  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.accounts (
    id text NOT NULL,
    handle text NOT NULL,
    display_name text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    password_hash text,
    status text DEFAULT 'active'::text NOT NULL,
    risk_level text DEFAULT 'normal'::text NOT NULL,
    frozen_until timestamp with time zone,
    risk_reason text,
    risk_updated_at timestamp with time zone,
    CONSTRAINT accounts_risk_level_check CHECK ((risk_level = ANY (ARRAY['normal'::text, 'watch'::text, 'high'::text]))),
    CONSTRAINT accounts_status_check CHECK ((status = ANY (ARRAY['active'::text, 'frozen'::text, 'banned'::text])))
);


--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events (
    id text NOT NULL,
    type text NOT NULL,
    subject_type text NOT NULL,
    subject_id text,
    actor_account_id text NOT NULL,
    trace_id text NOT NULL,
    outcome text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: business_id_sequences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.business_id_sequences (
    id_type text NOT NULL,
    biz_date date NOT NULL,
    next_value bigint NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT business_id_sequences_next_value_check CHECK ((next_value > 0))
);


--
-- Name: delivery_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.delivery_attempts (
    id text NOT NULL,
    order_id text NOT NULL,
    payment_intent_id text,
    provider text NOT NULL,
    provider_order_id text,
    status text NOT NULL,
    idempotency_key text NOT NULL,
    request_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    receipt_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT delivery_attempts_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'succeeded'::text, 'failed'::text])))
);


--
-- Name: digital_inventory_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.digital_inventory_items (
    id text NOT NULL,
    listing_id text NOT NULL,
    encrypted_payload text NOT NULL,
    payload_preview text NOT NULL,
    payload_hash text NOT NULL,
    status text NOT NULL,
    reserved_order_id text,
    delivered_order_id text,
    created_by_account_id text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT digital_inventory_items_status_check CHECK ((status = ANY (ARRAY['available'::text, 'reserved'::text, 'delivered'::text, 'voided'::text])))
);


--
-- Name: external_event_dedup; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.external_event_dedup (
    delivery_id text NOT NULL,
    event text NOT NULL,
    repo_url text NOT NULL,
    head_branch text NOT NULL,
    session_id text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: identity_badges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_badges (
    id text NOT NULL,
    account_id text NOT NULL,
    kind text NOT NULL,
    code text NOT NULL,
    label text NOT NULL,
    icon text,
    source_certifier_id text,
    source_fact_id text,
    weight integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: identity_facts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_facts (
    id text NOT NULL,
    account_id text NOT NULL,
    challenge_id text,
    certifier_id text NOT NULL,
    provider text NOT NULL,
    fact_type text NOT NULL,
    verification_method text NOT NULL,
    status text NOT NULL,
    platform_user_id text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    verified_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: identity_verification_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.identity_verification_challenges (
    id text NOT NULL,
    account_id text NOT NULL,
    certifier_id text NOT NULL,
    provider text NOT NULL,
    status text NOT NULL,
    verification_method text NOT NULL,
    challenge_token text NOT NULL,
    context jsonb DEFAULT '{}'::jsonb NOT NULL,
    instructions jsonb DEFAULT '{}'::jsonb NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    failed_at timestamp with time zone,
    failure_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.listings (
    id text NOT NULL,
    market_id text NOT NULL,
    kind public.listing_kind NOT NULL,
    parent_order_id text,
    title text NOT NULL,
    subject_type text NOT NULL,
    subject_ref text,
    deliverable_spec text NOT NULL,
    proof_spec text NOT NULL,
    settlement_spec text NOT NULL,
    inventory_limit integer NOT NULL,
    active_orders_count integer DEFAULT 0 NOT NULL,
    stock_total integer DEFAULT 0 NOT NULL,
    settlement_type public.settlement_type NOT NULL,
    status public.listing_status DEFAULT 'open'::public.listing_status NOT NULL,
    opened_by_account_id text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT listings_active_orders_count_check CHECK ((active_orders_count >= 0)),
    CONSTRAINT listings_inventory_capacity CHECK ((active_orders_count <= inventory_limit)),
    CONSTRAINT listings_inventory_limit_check CHECK ((inventory_limit > 0)),
    CONSTRAINT listings_stock_total_check CHECK ((stock_total >= 0))
);


--
-- Name: market_items_read_model; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.market_items_read_model (
    id text NOT NULL,
    kind text NOT NULL,
    source_id text NOT NULL,
    public_no text NOT NULL,
    actor_account_id text NOT NULL,
    title text NOT NULL,
    summary text NOT NULL,
    status text NOT NULL,
    visibility text DEFAULT 'market_public'::text NOT NULL,
    sort_at timestamp with time zone NOT NULL,
    search_text text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    search_text_normalized text GENERATED ALWAYS AS (lower(COALESCE(search_text, ''::text))) STORED,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, COALESCE(search_text, ''::text))) STORED
);


--
-- Name: market_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.market_members (
    id text NOT NULL,
    market_id text NOT NULL,
    account_id text NOT NULL,
    role public.market_member_role DEFAULT 'member'::public.market_member_role NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: markets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.markets (
    id text NOT NULL,
    name text NOT NULL,
    summary text NOT NULL,
    listing_goal text NOT NULL,
    lead_account_id text NOT NULL,
    source_ref text,
    surface_url text,
    settlement_type public.settlement_type NOT NULL,
    next_curve_slot integer DEFAULT 0 NOT NULL,
    status public.market_status DEFAULT 'active'::public.market_status NOT NULL,
    lead_last_active_at timestamp with time zone,
    lead_seat_status text DEFAULT 'occupied'::text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT markets_next_curve_slot_check CHECK ((next_curve_slot >= 0))
);


--
-- Name: oauth_identities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth_identities (
    id text NOT NULL,
    provider text NOT NULL,
    external_user_id text NOT NULL,
    account_id text NOT NULL,
    external_login text,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: oauth_states; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oauth_states (
    id text NOT NULL,
    provider text NOT NULL,
    state_token text NOT NULL,
    return_to text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offers (
    id text NOT NULL,
    actor_account_id text NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    delivery_standard text NOT NULL,
    price_amount numeric(18,2),
    currency text DEFAULT 'USD'::text NOT NULL,
    payment_method text,
    payment_profile text,
    payment_network text,
    payment_asset text,
    inventory_policy text DEFAULT 'single'::text NOT NULL,
    stock_total integer,
    stock_sold integer DEFAULT 0 NOT NULL,
    status text DEFAULT 'open'::text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    offer_no text NOT NULL,
    CONSTRAINT offers_inventory_policy_check CHECK ((inventory_policy = ANY (ARRAY['single'::text, 'limited'::text, 'unlimited'::text]))),
    CONSTRAINT offers_price_amount_positive CHECK (((price_amount IS NULL) OR (price_amount >= 0.01))),
    CONSTRAINT offers_status_check CHECK ((status = ANY (ARRAY['open'::text, 'closed'::text]))),
    CONSTRAINT offers_stock_sold_check CHECK ((stock_sold >= 0)),
    CONSTRAINT offers_stock_total_check CHECK (((stock_total IS NULL) OR (stock_total > 0)))
);


--
-- Name: order_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_events (
    id text NOT NULL,
    order_id text NOT NULL,
    event_type text NOT NULL,
    actor_account_id text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: order_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_participants (
    order_id text NOT NULL,
    account_id text NOT NULL,
    role_code text NOT NULL,
    source text DEFAULT 'order_snapshot'::text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: order_payment_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_payment_state (
    order_id text NOT NULL,
    latest_payment_intent_id text,
    status text,
    amount_minor integer,
    currency text,
    provider text,
    provider_payment_ref text,
    authorized_at timestamp with time zone,
    captured_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: order_progress_updates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_progress_updates (
    id text NOT NULL,
    order_id text NOT NULL,
    listing_id text NOT NULL,
    step_index integer NOT NULL,
    step_title text NOT NULL,
    summary text NOT NULL,
    links jsonb DEFAULT '[]'::jsonb NOT NULL,
    artifacts jsonb DEFAULT '[]'::jsonb NOT NULL,
    progress_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    submitted_by_account_id text NOT NULL,
    execution_mode public.execution_mode NOT NULL,
    agent_session_id text,
    agent_runtime text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT order_progress_updates_step_index_check CHECK ((step_index > 0))
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id text NOT NULL,
    market_id text NOT NULL,
    listing_id text NOT NULL,
    kind public.listing_kind NOT NULL,
    parent_order_id text,
    display_phase text NOT NULL,
    claimed_by_account_id text NOT NULL,
    submitted_by_account_id text,
    accepted_by_account_id text,
    proof_id text,
    settlement_type public.settlement_type NOT NULL,
    settlement_amount numeric(18,2),
    closed_reason text,
    dispute_reason text,
    review_listing_id text,
    delivery_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    settlement_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    reviewer_account_id text,
    review_due_at timestamp with time zone,
    backoffice_override_decision public.review_decision,
    backoffice_override_reason text,
    post_kind text,
    post_id text,
    review_post_id text,
    order_no text NOT NULL,
    challenge_nonce text DEFAULT 'legacy-nonce'::text NOT NULL,
    settlement_frozen boolean DEFAULT false NOT NULL,
    acceptance_criteria_snapshot jsonb DEFAULT '[]'::jsonb NOT NULL,
    proof_spec_snapshot text DEFAULT ''::text NOT NULL,
    settlement_spec_snapshot text DEFAULT ''::text NOT NULL,
    risk_level text DEFAULT 'normal'::text NOT NULL,
    operator_override_decision public.review_decision,
    operator_override_reason text,
    review_status text DEFAULT 'none'::text NOT NULL,
    dispute_window_status text DEFAULT 'closed'::text NOT NULL,
    dispute_window_expires_at timestamp with time zone,
    finalized_at timestamp with time zone,
    manual_review_required boolean DEFAULT false NOT NULL,
    status public.order_status DEFAULT 'claimed'::public.order_status NOT NULL,
    dispute_opened_by_account_id text,
    dispute_opened_from_status text,
    dispute_opened_from_window_status text,
    dispute_opened_from_window_expires_at timestamp with time zone,
    dispute_opened_at timestamp with time zone,
    dispute_cancelled_by_account_id text,
    dispute_cancelled_at timestamp with time zone,
    dispute_cancel_reason text,
    CONSTRAINT orders_settlement_amount_positive CHECK (((settlement_amount IS NULL) OR (settlement_amount > (0)::numeric)))
);


--
-- Name: organization_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_events (
    id text NOT NULL,
    project_id text,
    actor_account_id text,
    event_type text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    id text NOT NULL,
    account_id text NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payment_intents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_intents (
    id text NOT NULL,
    order_id text NOT NULL,
    account_id text NOT NULL,
    provider text NOT NULL,
    provider_payment_ref text,
    status public.payment_intent_status NOT NULL,
    amount_minor integer NOT NULL,
    currency text DEFAULT 'USD'::text NOT NULL,
    callback_token text NOT NULL,
    authorized_at timestamp with time zone,
    captured_at timestamp with time zone,
    refunded_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    disputed_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    payment_no text NOT NULL,
    CONSTRAINT payment_intents_amount_minor_check CHECK ((amount_minor > 0))
);


--
-- Name: payment_provider_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_provider_events (
    id text NOT NULL,
    provider text NOT NULL,
    provider_event_id text NOT NULL,
    payment_intent_id text NOT NULL,
    provider_payment_ref text,
    tx_hash text,
    status text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: post_items_read_model; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.post_items_read_model (
    item_id text NOT NULL,
    post_kind text NOT NULL,
    post_id text NOT NULL,
    market_id text NOT NULL,
    listing_id text NOT NULL,
    item_kind text,
    fulfillment_mode text,
    delivery_mode text,
    listing_status text NOT NULL,
    latest_order_id text,
    latest_order_status text,
    latest_order_display_phase text,
    latest_payment_intent_id text,
    latest_payment_status text,
    sort_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    search_text text DEFAULT ''::text NOT NULL,
    search_text_normalized text GENERATED ALWAYS AS (lower(COALESCE(search_text, ''::text))) STORED,
    search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, COALESCE(search_text, ''::text))) STORED
);


--
-- Name: project_external_refs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_external_refs (
    id text NOT NULL,
    ref_type text NOT NULL,
    project_id text NOT NULL,
    validation_task_id text,
    repo_url text,
    pr_number integer,
    pr_url text,
    head_sha text,
    base_branch text,
    branch_name text,
    state text,
    check_name text,
    status text,
    conclusion text,
    details_url text,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    merged_at timestamp with time zone,
    last_synced_at timestamp with time zone,
    raw_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT project_external_refs_ref_type_check CHECK ((ref_type = ANY (ARRAY['pull_request'::text, 'ci_check'::text])))
);


--
-- Name: project_initiative_recommendations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_initiative_recommendations (
    id text NOT NULL,
    recommendation_no text NOT NULL,
    account_id text NOT NULL,
    project_id text NOT NULL,
    project_no text NOT NULL,
    recommendation_type text NOT NULL,
    target_key text NOT NULL,
    target_role_code text,
    title text NOT NULL,
    reason text NOT NULL,
    suggested_action text NOT NULL,
    input jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'open'::text NOT NULL,
    work_item_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_memory_repo_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_memory_repo_entries (
    id text NOT NULL,
    project_id text NOT NULL,
    root_id text,
    memory_id text NOT NULL,
    kind text NOT NULL,
    content text NOT NULL,
    source_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    confidence numeric(4,3) DEFAULT 1.0 NOT NULL,
    visibility text NOT NULL,
    risk_level text DEFAULT 'normal'::text NOT NULL,
    retrieval_tags jsonb DEFAULT '[]'::jsonb NOT NULL,
    supersedes jsonb DEFAULT '[]'::jsonb NOT NULL,
    origin_event_type text,
    origin_event_id text,
    maintenance_reason text,
    valid_from timestamp with time zone,
    expires_at timestamp with time zone,
    last_used_at timestamp with time zone,
    status text NOT NULL,
    created_by_account_id text,
    approved_by_account_id text,
    approved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_memory_repo_roots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_memory_repo_roots (
    id text NOT NULL,
    project_id text NOT NULL,
    repo_binding_id text,
    provider text NOT NULL,
    repo_owner text NOT NULL,
    repo_name text NOT NULL,
    branch text NOT NULL,
    commit_sha text NOT NULL,
    root_hash text NOT NULL,
    latest_path text NOT NULL,
    sync_status text NOT NULL,
    error_code text,
    error_message text,
    raw_root jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    synced_at timestamp with time zone
);


--
-- Name: project_memory_repo_sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_memory_repo_sources (
    id text NOT NULL,
    project_id text NOT NULL,
    root_id text,
    source_id text NOT NULL,
    kind text NOT NULL,
    path text NOT NULL,
    sha256 text NOT NULL,
    visibility text NOT NULL,
    provider text,
    external_url text,
    external_file_id text,
    external_revision_id text,
    external_size bigint,
    sync_status text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_memory_sync_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_memory_sync_events (
    id text NOT NULL,
    project_id text NOT NULL,
    root_id text,
    event_type text NOT NULL,
    status text NOT NULL,
    message text,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_repo_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_repo_bindings (
    id text NOT NULL,
    project_id text NOT NULL,
    provider text NOT NULL,
    repo_url text NOT NULL,
    repo_owner text NOT NULL,
    repo_name text NOT NULL,
    default_branch text,
    installation_id text,
    created_by_account_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_roles (
    id text NOT NULL,
    project_id text NOT NULL,
    role_code public.project_role_code NOT NULL,
    account_id text NOT NULL,
    assigned_by_account_id text,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_share_pools; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_share_pools (
    project_id text NOT NULL,
    market_id text NOT NULL,
    share_total integer NOT NULL,
    share_minted integer NOT NULL,
    share_reserved integer NOT NULL,
    task_budget integer NOT NULL,
    task_minted integer NOT NULL,
    task_reserved integer NOT NULL,
    reserve_budget integer NOT NULL,
    next_curve_slot integer NOT NULL,
    initial_base_reward integer NOT NULL,
    decay numeric(12,8) NOT NULL,
    min_base_reward integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT project_share_pools_decay_check CHECK ((decay > (0)::numeric)),
    CONSTRAINT project_share_pools_initial_base_reward_check CHECK ((initial_base_reward > 0)),
    CONSTRAINT project_share_pools_min_base_reward_check CHECK ((min_base_reward > 0)),
    CONSTRAINT project_share_pools_next_curve_slot_check CHECK ((next_curve_slot >= 0)),
    CONSTRAINT project_share_pools_reserve_budget_check CHECK ((reserve_budget >= 0)),
    CONSTRAINT project_share_pools_share_minted_check CHECK ((share_minted >= 0)),
    CONSTRAINT project_share_pools_share_reserved_check CHECK ((share_reserved >= 0)),
    CONSTRAINT project_share_pools_share_total_check CHECK ((share_total > 0)),
    CONSTRAINT project_share_pools_task_budget_check CHECK ((task_budget >= 0)),
    CONSTRAINT project_share_pools_task_minted_check CHECK ((task_minted >= 0)),
    CONSTRAINT project_share_pools_task_reserved_check CHECK ((task_reserved >= 0))
);


--
-- Name: project_timeline_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_timeline_events (
    id text NOT NULL,
    project_id text NOT NULL,
    event_type text NOT NULL,
    source_type text NOT NULL,
    source_id text NOT NULL,
    order_id text,
    actor_account_id text,
    occurred_at timestamp with time zone NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: project_validations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_validations (
    id text NOT NULL,
    project_id text NOT NULL,
    title text NOT NULL,
    hypothesis text NOT NULL,
    status text NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    parent_launch_id text,
    source_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_by_account_id text NOT NULL,
    published_by_account_id text,
    settled_by_account_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    settled_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT project_validations_status_check CHECK ((status = ANY (ARRAY['draft'::text, 'live'::text, 'reviewing'::text, 'settled'::text])))
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id text NOT NULL,
    owner_account_id text NOT NULL,
    title text NOT NULL,
    summary text NOT NULL,
    one_sentence text NOT NULL,
    inventory_policy text DEFAULT 'single'::text NOT NULL,
    stock_total integer,
    stock_sold integer DEFAULT 0 NOT NULL,
    status text DEFAULT 'active'::text NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    project_no text NOT NULL,
    project_level text DEFAULT 'child'::text NOT NULL,
    parent_project_id text,
    CONSTRAINT projects_inventory_policy_check CHECK ((inventory_policy = ANY (ARRAY['single'::text, 'limited'::text, 'unlimited'::text]))),
    CONSTRAINT projects_project_level_check CHECK ((project_level = ANY (ARRAY['root'::text, 'child'::text]))),
    CONSTRAINT projects_root_child_shape_check CHECK ((((project_level = 'root'::text) AND (parent_project_id IS NULL)) OR ((project_level = 'child'::text) AND (parent_project_id IS NOT NULL)))),
    CONSTRAINT projects_status_check CHECK ((status = ANY (ARRAY['active'::text, 'claimable'::text, 'archived'::text]))),
    CONSTRAINT projects_stock_sold_check CHECK ((stock_sold >= 0)),
    CONSTRAINT projects_stock_total_check CHECK (((stock_total IS NULL) OR (stock_total > 0)))
);


--
-- Name: proof_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proof_assets (
    id text NOT NULL,
    order_id text NOT NULL,
    artifact_ref text NOT NULL,
    object_key text NOT NULL,
    filename text NOT NULL,
    content_type text NOT NULL,
    content_length_bytes bigint NOT NULL,
    checksum_sha256 text NOT NULL,
    storage_provider text NOT NULL,
    bucket text NOT NULL,
    status public.proof_asset_status DEFAULT 'pending'::public.proof_asset_status NOT NULL,
    public_url text,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    uploaded_by_account_id text NOT NULL,
    purpose text DEFAULT 'proof'::text NOT NULL,
    visibility text DEFAULT 'participants'::text NOT NULL,
    CONSTRAINT chk_proof_assets_purpose CHECK ((purpose = ANY (ARRAY['proof'::text, 'delivery'::text, 'dispute'::text, 'review'::text, 'progress'::text]))),
    CONSTRAINT chk_proof_assets_visibility CHECK ((visibility = ANY (ARRAY['participants'::text, 'reviewer_only'::text, 'private'::text]))),
    CONSTRAINT proof_assets_content_length_bytes_check CHECK ((content_length_bytes > 0))
);


--
-- Name: proofs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proofs (
    id text NOT NULL,
    order_id text NOT NULL,
    kind public.proof_kind NOT NULL,
    parent_order_id text,
    submitted_by_account_id text NOT NULL,
    summary text NOT NULL,
    links jsonb DEFAULT '[]'::jsonb NOT NULL,
    artifacts jsonb DEFAULT '[]'::jsonb NOT NULL,
    proof_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    execution_mode public.execution_mode NOT NULL,
    agent_session_id text,
    agent_runtime text,
    decision public.review_decision,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    evidence_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    content_hashes jsonb DEFAULT '[]'::jsonb NOT NULL,
    criteria_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    visibility text DEFAULT 'public'::text NOT NULL,
    execution_trace_ref text
);


--
-- Name: repo_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.repo_jobs (
    id text NOT NULL,
    job_type text NOT NULL,
    project_no text,
    order_no text,
    provider text NOT NULL,
    repo_url text NOT NULL,
    clone_url text NOT NULL,
    repo_owner text,
    repo_name text,
    default_branch text,
    visibility text,
    base_branch text,
    head_branch text,
    pr_url text,
    head_commit text,
    ci_status text,
    status text NOT NULL,
    runtime text,
    created_by_account_id text,
    issued_to_account_id text,
    token_secret_ref text,
    expires_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT repo_jobs_job_type_check CHECK ((job_type = ANY (ARRAY['provision'::text, 'delivery'::text])))
);


--
-- Name: requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.requests (
    id text NOT NULL,
    actor_account_id text NOT NULL,
    title text NOT NULL,
    description text NOT NULL,
    delivery_standard text NOT NULL,
    budget_amount numeric(18,2),
    currency text DEFAULT 'USD'::text NOT NULL,
    payment_method text,
    payment_profile text,
    payment_network text,
    payment_asset text,
    inventory_policy text DEFAULT 'single'::text NOT NULL,
    stock_total integer,
    stock_filled integer DEFAULT 0 NOT NULL,
    status text DEFAULT 'open'::text NOT NULL,
    deadline_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    request_no text NOT NULL,
    CONSTRAINT requests_budget_amount_positive CHECK (((budget_amount IS NULL) OR (budget_amount >= 0.01))),
    CONSTRAINT requests_inventory_policy_check CHECK ((inventory_policy = ANY (ARRAY['single'::text, 'limited'::text, 'unlimited'::text]))),
    CONSTRAINT requests_status_check CHECK ((status = ANY (ARRAY['open'::text, 'closed'::text]))),
    CONSTRAINT requests_stock_filled_check CHECK ((stock_filled >= 0)),
    CONSTRAINT requests_stock_total_check CHECK (((stock_total IS NULL) OR (stock_total > 0)))
);


--
-- Name: risk_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.risk_events (
    id text NOT NULL,
    kind text NOT NULL,
    subject_type text NOT NULL,
    subject_id text,
    actor_ref text NOT NULL,
    severity text NOT NULL,
    reason text NOT NULL,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: share_release_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.share_release_requests (
    id text NOT NULL,
    issuer_type public.share_issuer_type NOT NULL,
    issuer_id text NOT NULL,
    market_id text NOT NULL,
    project_id text,
    order_id text NOT NULL,
    proof_id text NOT NULL,
    account_id text NOT NULL,
    amount integer NOT NULL,
    curve_slot integer NOT NULL,
    status public.share_release_request_status NOT NULL,
    required_role_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    approved_role_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    skipped_role_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    requested_by_account_id text,
    resolved_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT share_release_requests_amount_check CHECK ((amount > 0)),
    CONSTRAINT share_release_requests_curve_slot_check CHECK ((curve_slot >= 0))
);


--
-- Name: share_settlement_holds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.share_settlement_holds (
    id text NOT NULL,
    order_id text NOT NULL,
    proof_id text,
    share_release_request_id text,
    market_id text NOT NULL,
    project_id text,
    item_id text,
    account_id text NOT NULL,
    amount integer NOT NULL,
    curve_slot integer NOT NULL,
    reason public.ledger_reason NOT NULL,
    status text NOT NULL,
    lock_reason text NOT NULL,
    release_reason text,
    released_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT share_settlement_holds_amount_check CHECK ((amount > 0)),
    CONSTRAINT share_settlement_holds_curve_slot_check CHECK ((curve_slot >= 0)),
    CONSTRAINT share_settlement_holds_status_check CHECK ((status = ANY (ARRAY['locked'::text, 'released'::text, 'cancelled'::text])))
);


--
-- Name: shares_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shares_ledger (
    id text NOT NULL,
    market_id text,
    order_id text,
    proof_id text,
    account_id text NOT NULL,
    amount integer NOT NULL,
    curve_slot integer NOT NULL,
    reason public.ledger_reason NOT NULL,
    settlement_type_snapshot public.settlement_type NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    project_id text,
    item_id text,
    issuer_type public.share_issuer_type DEFAULT 'project'::public.share_issuer_type NOT NULL,
    issuer_id text NOT NULL,
    share_release_request_id text,
    source_type text DEFAULT 'order'::text NOT NULL,
    source_id text NOT NULL,
    CONSTRAINT shares_ledger_amount_check CHECK ((amount > 0)),
    CONSTRAINT shares_ledger_curve_slot_check CHECK ((curve_slot >= 0)),
    CONSTRAINT shares_ledger_source_type_check CHECK ((source_type = ANY (ARRAY['order'::text, 'proposal_pack'::text])))
);


--
-- Name: spring_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session (
    primary_id character(36) NOT NULL,
    session_id character(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name character varying(100)
);


--
-- Name: spring_session_attributes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.spring_session_attributes (
    session_primary_id character(36) NOT NULL,
    attribute_name character varying(200) NOT NULL,
    attribute_bytes bytea NOT NULL
);


--
-- Name: work_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_events (
    id text NOT NULL,
    subject_type text NOT NULL,
    subject_id text NOT NULL,
    actor_account_id text NOT NULL,
    event_type text NOT NULL,
    action_id text NOT NULL,
    input_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    output_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    receipt_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_items (
    id text NOT NULL,
    item_no text NOT NULL,
    source_type text NOT NULL,
    source_id text NOT NULL,
    account_id text NOT NULL,
    title text NOT NULL,
    goal text NOT NULL,
    acceptance_criteria jsonb DEFAULT '[]'::jsonb NOT NULL,
    input_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    output_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
    required_role text,
    required_capability text,
    urgency text DEFAULT 'attention'::text NOT NULL,
    status text NOT NULL,
    ready_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    claim_expires_at timestamp with time zone,
    CONSTRAINT work_items_status_check CHECK ((status = ANY (ARRAY['ready'::text, 'claimed'::text, 'submitted'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'closed'::text])))
);


--
-- Name: work_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_receipts (
    id text NOT NULL,
    receipt_no text NOT NULL,
    work_run_id text NOT NULL,
    summary text NOT NULL,
    output jsonb DEFAULT '{}'::jsonb NOT NULL,
    evidence_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    trace_refs jsonb DEFAULT '[]'::jsonb NOT NULL,
    content_hashes jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: work_reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_reviews (
    id text NOT NULL,
    review_no text NOT NULL,
    work_run_id text NOT NULL,
    reviewer_account_id text NOT NULL,
    status text NOT NULL,
    decision text,
    decision_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    resolved_at timestamp with time zone,
    CONSTRAINT work_reviews_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'resolved'::text])))
);


--
-- Name: work_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_runs (
    id text NOT NULL,
    run_no text NOT NULL,
    work_item_id text NOT NULL,
    actor_account_id text NOT NULL,
    status text NOT NULL,
    execution_mode text DEFAULT 'manual'::text NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    submitted_at timestamp with time zone,
    accepted_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT work_runs_status_check CHECK ((status = ANY (ARRAY['claimed'::text, 'running'::text, 'submitted'::text, 'accepted'::text, 'revision_requested'::text, 'disputed'::text, 'resolved'::text, 'closed'::text])))
);


--
-- Name: work_trust_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.work_trust_events (
    id text NOT NULL,
    event_no text NOT NULL,
    event_type text NOT NULL,
    work_run_id text NOT NULL,
    review_id text,
    order_id text,
    payment_intent_id text,
    actor_account_id text,
    status text NOT NULL,
    reason text,
    amount_minor integer,
    currency text,
    input_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    output_snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT work_trust_events_event_type_check CHECK ((event_type = ANY (ARRAY['payment_authorization'::text, 'settlement'::text, 'after_sale'::text, 'arbitration'::text])))
);


--
-- Name: workbench_dismissals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workbench_dismissals (
    account_id text NOT NULL,
    item_key text NOT NULL,
    reason text NOT NULL,
    subject_type text NOT NULL,
    subject_id text NOT NULL,
    dismissed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: accounts accounts_handle_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_handle_key UNIQUE (handle);


--
-- Name: accounts accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: business_id_sequences business_id_sequences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.business_id_sequences
    ADD CONSTRAINT business_id_sequences_pkey PRIMARY KEY (id_type, biz_date);


--
-- Name: delivery_attempts delivery_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_attempts
    ADD CONSTRAINT delivery_attempts_pkey PRIMARY KEY (id);


--
-- Name: delivery_attempts delivery_attempts_provider_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_attempts
    ADD CONSTRAINT delivery_attempts_provider_idempotency_key_key UNIQUE (provider, idempotency_key);


--
-- Name: digital_inventory_items digital_inventory_items_listing_id_payload_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_listing_id_payload_hash_key UNIQUE (listing_id, payload_hash);


--
-- Name: digital_inventory_items digital_inventory_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_pkey PRIMARY KEY (id);


--
-- Name: external_event_dedup external_event_dedup_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.external_event_dedup
    ADD CONSTRAINT external_event_dedup_pkey PRIMARY KEY (delivery_id);


--
-- Name: identity_badges identity_badges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_badges
    ADD CONSTRAINT identity_badges_pkey PRIMARY KEY (id);


--
-- Name: identity_facts identity_facts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_facts
    ADD CONSTRAINT identity_facts_pkey PRIMARY KEY (id);


--
-- Name: identity_verification_challenges identity_verification_challenges_challenge_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_verification_challenges
    ADD CONSTRAINT identity_verification_challenges_challenge_token_key UNIQUE (challenge_token);


--
-- Name: identity_verification_challenges identity_verification_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_verification_challenges
    ADD CONSTRAINT identity_verification_challenges_pkey PRIMARY KEY (id);


--
-- Name: listings listings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_pkey PRIMARY KEY (id);


--
-- Name: market_items_read_model market_items_read_model_kind_source_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_items_read_model
    ADD CONSTRAINT market_items_read_model_kind_source_id_key UNIQUE (kind, source_id);


--
-- Name: market_items_read_model market_items_read_model_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_items_read_model
    ADD CONSTRAINT market_items_read_model_pkey PRIMARY KEY (id);


--
-- Name: market_members market_members_market_id_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_members
    ADD CONSTRAINT market_members_market_id_account_id_key UNIQUE (market_id, account_id);


--
-- Name: market_members market_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_members
    ADD CONSTRAINT market_members_pkey PRIMARY KEY (id);


--
-- Name: markets markets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.markets
    ADD CONSTRAINT markets_pkey PRIMARY KEY (id);


--
-- Name: oauth_identities oauth_identities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_identities
    ADD CONSTRAINT oauth_identities_pkey PRIMARY KEY (id);


--
-- Name: oauth_identities oauth_identities_provider_external_user_id_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_identities
    ADD CONSTRAINT oauth_identities_provider_external_user_id_unique UNIQUE (provider, external_user_id);


--
-- Name: oauth_states oauth_states_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_states
    ADD CONSTRAINT oauth_states_pkey PRIMARY KEY (id);


--
-- Name: oauth_states oauth_states_state_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_states
    ADD CONSTRAINT oauth_states_state_token_key UNIQUE (state_token);


--
-- Name: offers offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_pkey PRIMARY KEY (id);


--
-- Name: order_events order_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_pkey PRIMARY KEY (id);


--
-- Name: order_participants order_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_participants
    ADD CONSTRAINT order_participants_pkey PRIMARY KEY (order_id, account_id, role_code);


--
-- Name: order_payment_state order_payment_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_payment_state
    ADD CONSTRAINT order_payment_state_pkey PRIMARY KEY (order_id);


--
-- Name: order_progress_updates order_progress_updates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_progress_updates
    ADD CONSTRAINT order_progress_updates_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: organization_events organization_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_events
    ADD CONSTRAINT organization_events_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);


--
-- Name: payment_intents payment_intents_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_intents
    ADD CONSTRAINT payment_intents_order_id_key UNIQUE (order_id);


--
-- Name: payment_intents payment_intents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_intents
    ADD CONSTRAINT payment_intents_pkey PRIMARY KEY (id);


--
-- Name: payment_provider_events payment_provider_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_provider_events
    ADD CONSTRAINT payment_provider_events_pkey PRIMARY KEY (id);


--
-- Name: payment_provider_events payment_provider_events_provider_provider_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_provider_events
    ADD CONSTRAINT payment_provider_events_provider_provider_event_id_key UNIQUE (provider, provider_event_id);


--
-- Name: post_items_read_model post_items_read_model_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_pkey PRIMARY KEY (item_id);


--
-- Name: project_external_refs project_external_refs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_external_refs
    ADD CONSTRAINT project_external_refs_pkey PRIMARY KEY (id);


--
-- Name: project_initiative_recommendations project_initiative_recommenda_project_id_recommendation_typ_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_initiative_recommendations
    ADD CONSTRAINT project_initiative_recommenda_project_id_recommendation_typ_key UNIQUE (project_id, recommendation_type, target_key);


--
-- Name: project_initiative_recommendations project_initiative_recommendations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_initiative_recommendations
    ADD CONSTRAINT project_initiative_recommendations_pkey PRIMARY KEY (id);


--
-- Name: project_initiative_recommendations project_initiative_recommendations_recommendation_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_initiative_recommendations
    ADD CONSTRAINT project_initiative_recommendations_recommendation_no_key UNIQUE (recommendation_no);


--
-- Name: project_memory_repo_entries project_memory_repo_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_entries
    ADD CONSTRAINT project_memory_repo_entries_pkey PRIMARY KEY (id);


--
-- Name: project_memory_repo_roots project_memory_repo_roots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_roots
    ADD CONSTRAINT project_memory_repo_roots_pkey PRIMARY KEY (id);


--
-- Name: project_memory_repo_sources project_memory_repo_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_sources
    ADD CONSTRAINT project_memory_repo_sources_pkey PRIMARY KEY (id);


--
-- Name: project_memory_sync_events project_memory_sync_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_sync_events
    ADD CONSTRAINT project_memory_sync_events_pkey PRIMARY KEY (id);


--
-- Name: project_repo_bindings project_repo_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_repo_bindings
    ADD CONSTRAINT project_repo_bindings_pkey PRIMARY KEY (id);


--
-- Name: project_repo_bindings project_repo_bindings_project_id_provider_repo_owner_repo_n_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_repo_bindings
    ADD CONSTRAINT project_repo_bindings_project_id_provider_repo_owner_repo_n_key UNIQUE (project_id, provider, repo_owner, repo_name);


--
-- Name: project_roles project_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_roles
    ADD CONSTRAINT project_roles_pkey PRIMARY KEY (id);


--
-- Name: project_roles project_roles_project_id_role_code_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_roles
    ADD CONSTRAINT project_roles_project_id_role_code_account_id_key UNIQUE (project_id, role_code, account_id);


--
-- Name: project_share_pools project_share_pools_market_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_share_pools
    ADD CONSTRAINT project_share_pools_market_id_key UNIQUE (market_id);


--
-- Name: project_share_pools project_share_pools_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_share_pools
    ADD CONSTRAINT project_share_pools_pkey PRIMARY KEY (project_id);


--
-- Name: project_timeline_events project_timeline_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_timeline_events
    ADD CONSTRAINT project_timeline_events_pkey PRIMARY KEY (id);


--
-- Name: project_timeline_events project_timeline_events_project_id_source_type_source_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_timeline_events
    ADD CONSTRAINT project_timeline_events_project_id_source_type_source_id_key UNIQUE (project_id, source_type, source_id);


--
-- Name: project_validations project_validations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_validations
    ADD CONSTRAINT project_validations_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: proof_assets proof_assets_artifact_ref_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proof_assets
    ADD CONSTRAINT proof_assets_artifact_ref_key UNIQUE (artifact_ref);


--
-- Name: proof_assets proof_assets_object_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proof_assets
    ADD CONSTRAINT proof_assets_object_key_key UNIQUE (object_key);


--
-- Name: proof_assets proof_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proof_assets
    ADD CONSTRAINT proof_assets_pkey PRIMARY KEY (id);


--
-- Name: proofs proofs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proofs
    ADD CONSTRAINT proofs_pkey PRIMARY KEY (id);


--
-- Name: repo_jobs repo_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repo_jobs
    ADD CONSTRAINT repo_jobs_pkey PRIMARY KEY (id);


--
-- Name: requests requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_pkey PRIMARY KEY (id);


--
-- Name: risk_events risk_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.risk_events
    ADD CONSTRAINT risk_events_pkey PRIMARY KEY (id);


--
-- Name: share_release_requests share_release_requests_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_order_id_key UNIQUE (order_id);


--
-- Name: share_release_requests share_release_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_pkey PRIMARY KEY (id);


--
-- Name: share_settlement_holds share_settlement_holds_order_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_order_id_key UNIQUE (order_id);


--
-- Name: share_settlement_holds share_settlement_holds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_pkey PRIMARY KEY (id);


--
-- Name: shares_ledger shares_ledger_order_id_account_id_reason_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_order_id_account_id_reason_key UNIQUE (order_id, account_id, reason);


--
-- Name: shares_ledger shares_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_pkey PRIMARY KEY (id);


--
-- Name: spring_session_attributes spring_session_attributes_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name);


--
-- Name: spring_session spring_session_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session
    ADD CONSTRAINT spring_session_pk PRIMARY KEY (primary_id);


--
-- Name: work_events work_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_events
    ADD CONSTRAINT work_events_pkey PRIMARY KEY (id);


--
-- Name: work_items work_items_account_id_item_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_items
    ADD CONSTRAINT work_items_account_id_item_no_key UNIQUE (account_id, item_no);


--
-- Name: work_items work_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_items
    ADD CONSTRAINT work_items_pkey PRIMARY KEY (id);


--
-- Name: work_receipts work_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_receipts
    ADD CONSTRAINT work_receipts_pkey PRIMARY KEY (id);


--
-- Name: work_receipts work_receipts_receipt_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_receipts
    ADD CONSTRAINT work_receipts_receipt_no_key UNIQUE (receipt_no);


--
-- Name: work_reviews work_reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_reviews
    ADD CONSTRAINT work_reviews_pkey PRIMARY KEY (id);


--
-- Name: work_reviews work_reviews_review_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_reviews
    ADD CONSTRAINT work_reviews_review_no_key UNIQUE (review_no);


--
-- Name: work_runs work_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_runs
    ADD CONSTRAINT work_runs_pkey PRIMARY KEY (id);


--
-- Name: work_runs work_runs_run_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_runs
    ADD CONSTRAINT work_runs_run_no_key UNIQUE (run_no);


--
-- Name: work_runs work_runs_work_item_id_actor_account_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_runs
    ADD CONSTRAINT work_runs_work_item_id_actor_account_id_key UNIQUE (work_item_id, actor_account_id);


--
-- Name: work_trust_events work_trust_events_event_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_event_no_key UNIQUE (event_no);


--
-- Name: work_trust_events work_trust_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_pkey PRIMARY KEY (id);


--
-- Name: workbench_dismissals workbench_dismissals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workbench_dismissals
    ADD CONSTRAINT workbench_dismissals_pkey PRIMARY KEY (account_id, item_key);


--
-- Name: idx_accounts_status_risk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_accounts_status_risk ON public.accounts USING btree (status, risk_level, risk_updated_at DESC);


--
-- Name: idx_audit_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_created_at ON public.audit_events USING btree (created_at DESC);


--
-- Name: idx_audit_events_type_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_events_type_created_at ON public.audit_events USING btree (type, created_at DESC);


--
-- Name: idx_delivery_attempts_order_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_attempts_order_created ON public.delivery_attempts USING btree (order_id, created_at DESC);


--
-- Name: idx_delivery_attempts_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_delivery_attempts_status_created ON public.delivery_attempts USING btree (status, created_at DESC);


--
-- Name: idx_digital_inventory_listing_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_digital_inventory_listing_status ON public.digital_inventory_items USING btree (listing_id, status, created_at);


--
-- Name: idx_digital_inventory_reserved_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_digital_inventory_reserved_order ON public.digital_inventory_items USING btree (reserved_order_id) WHERE (reserved_order_id IS NOT NULL);


--
-- Name: idx_external_event_dedup_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_external_event_dedup_session_created ON public.external_event_dedup USING btree (session_id, created_at DESC);


--
-- Name: idx_identity_badges_account_kind_weight; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_badges_account_kind_weight ON public.identity_badges USING btree (account_id, kind, weight DESC);


--
-- Name: idx_identity_facts_account_verified; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_facts_account_verified ON public.identity_facts USING btree (account_id, status, verified_at DESC);


--
-- Name: idx_identity_facts_certifier_platform_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_facts_certifier_platform_user ON public.identity_facts USING btree (certifier_id, platform_user_id);


--
-- Name: idx_identity_verification_challenges_account_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_verification_challenges_account_created ON public.identity_verification_challenges USING btree (account_id, created_at DESC);


--
-- Name: idx_identity_verification_challenges_status_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_identity_verification_challenges_status_expires ON public.identity_verification_challenges USING btree (status, expires_at);


--
-- Name: idx_listings_market_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_market_status ON public.listings USING btree (market_id, status, kind);


--
-- Name: idx_listings_parent_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_parent_order_id ON public.listings USING btree (parent_order_id);


--
-- Name: idx_listings_post_item_search_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_listings_post_item_search_trgm ON public.listings USING gin (lower(((((COALESCE(title, ''::text) || ' '::text) || COALESCE(deliverable_spec, ''::text)) || ' '::text) || COALESCE(proof_spec, ''::text))) public.gin_trgm_ops) WHERE (subject_type = 'post_item'::text);


--
-- Name: idx_market_items_read_model_kind_status_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_items_read_model_kind_status_sort ON public.market_items_read_model USING btree (kind, status, sort_at DESC, id DESC) WHERE (visibility = 'market_public'::text);


--
-- Name: idx_market_items_read_model_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_items_read_model_search ON public.market_items_read_model USING gin (to_tsvector('simple'::regconfig, search_text));


--
-- Name: idx_market_items_read_model_search_normalized_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_items_read_model_search_normalized_trgm ON public.market_items_read_model USING gin (search_text_normalized public.gin_trgm_ops);


--
-- Name: idx_market_items_read_model_search_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_items_read_model_search_trgm ON public.market_items_read_model USING gin (lower(search_text) public.gin_trgm_ops) WHERE (visibility = 'market_public'::text);


--
-- Name: idx_market_items_read_model_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_items_read_model_search_vector ON public.market_items_read_model USING gin (search_vector);


--
-- Name: idx_market_members_market_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_market_members_market_id ON public.market_members USING btree (market_id);


--
-- Name: idx_markets_lead_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_markets_lead_account_id ON public.markets USING btree (lead_account_id);


--
-- Name: idx_oauth_states_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_oauth_states_expires_at ON public.oauth_states USING btree (expires_at);


--
-- Name: idx_offers_actor_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_actor_created_at ON public.offers USING btree (actor_account_id, created_at DESC);


--
-- Name: idx_offers_public_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_public_search ON public.offers USING gin (to_tsvector('simple'::regconfig, ((((COALESCE(offer_no, ''::text) || ' '::text) || COALESCE(title, ''::text)) || ' '::text) || COALESCE(description, ''::text))));


--
-- Name: idx_offers_public_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_public_status_created ON public.offers USING btree (status, created_at DESC) WHERE (COALESCE((metadata ->> 'visibility'::text), 'market_public'::text) = 'market_public'::text);


--
-- Name: idx_offers_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_offers_status_created_at ON public.offers USING btree (status, created_at DESC);


--
-- Name: idx_order_events_order_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_events_order_created ON public.order_events USING btree (order_id, created_at);


--
-- Name: idx_order_participants_account_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_participants_account_order ON public.order_participants USING btree (account_id, order_id);


--
-- Name: idx_order_payment_state_status_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_payment_state_status_updated ON public.order_payment_state USING btree (status, updated_at DESC);


--
-- Name: idx_order_progress_updates_listing_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_progress_updates_listing_created ON public.order_progress_updates USING btree (listing_id, created_at);


--
-- Name: idx_order_progress_updates_order_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_progress_updates_order_created ON public.order_progress_updates USING btree (order_id, created_at);


--
-- Name: idx_orders_acceptor_account_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_acceptor_account_updated ON public.orders USING btree (((metadata ->> 'acceptorAccountId'::text)), updated_at DESC);


--
-- Name: idx_orders_buyer_account_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_buyer_account_updated ON public.orders USING btree (((metadata ->> 'buyerAccountId'::text)), updated_at DESC);


--
-- Name: idx_orders_dispute_opened_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_dispute_opened_by ON public.orders USING btree (dispute_opened_by_account_id, updated_at DESC) WHERE (status = 'disputed'::public.order_status);


--
-- Name: idx_orders_dispute_window; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_dispute_window ON public.orders USING btree (dispute_window_status, dispute_window_expires_at);


--
-- Name: idx_orders_fulfiller_account_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_fulfiller_account_updated ON public.orders USING btree (((metadata ->> 'fulfillerAccountId'::text)), updated_at DESC);


--
-- Name: idx_orders_listing_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_listing_id ON public.orders USING btree (listing_id);


--
-- Name: idx_orders_parent_order_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_parent_order_created ON public.orders USING btree (parent_order_id, created_at);


--
-- Name: idx_orders_parent_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_parent_order_id ON public.orders USING btree (parent_order_id);


--
-- Name: idx_orders_post_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_post_ref ON public.orders USING btree (post_kind, post_id);


--
-- Name: idx_orders_reviewer_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_reviewer_updated ON public.orders USING btree (reviewer_account_id, updated_at DESC);


--
-- Name: idx_orders_seller_account_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_orders_seller_account_updated ON public.orders USING btree (((metadata ->> 'sellerAccountId'::text)), updated_at DESC);


--
-- Name: idx_organization_events_project_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_organization_events_project_created ON public.organization_events USING btree (project_id, created_at DESC);


--
-- Name: idx_password_reset_tokens_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_tokens_account_id ON public.password_reset_tokens USING btree (account_id);


--
-- Name: idx_password_reset_tokens_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_tokens_expires_at ON public.password_reset_tokens USING btree (expires_at);


--
-- Name: idx_payment_intents_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_intents_created_at ON public.payment_intents USING btree (created_at DESC);


--
-- Name: idx_payment_intents_order_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_intents_order_created ON public.payment_intents USING btree (order_id, created_at DESC);


--
-- Name: idx_payment_intents_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_intents_status_created_at ON public.payment_intents USING btree (status, created_at DESC);


--
-- Name: idx_payment_provider_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_provider_events_created_at ON public.payment_provider_events USING btree (created_at DESC);


--
-- Name: idx_payment_provider_events_intent_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_provider_events_intent_created ON public.payment_provider_events USING btree (payment_intent_id, created_at DESC);


--
-- Name: idx_payment_provider_events_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_payment_provider_events_status_created_at ON public.payment_provider_events USING btree (status, created_at DESC);


--
-- Name: idx_post_items_read_model_post_sort; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_items_read_model_post_sort ON public.post_items_read_model USING btree (post_kind, post_id, sort_at DESC, item_id DESC);


--
-- Name: idx_post_items_read_model_search_normalized_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_items_read_model_search_normalized_trgm ON public.post_items_read_model USING gin (search_text_normalized public.gin_trgm_ops);


--
-- Name: idx_post_items_read_model_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_post_items_read_model_search_vector ON public.post_items_read_model USING gin (search_vector);


--
-- Name: idx_project_external_refs_project_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_external_refs_project_type_status ON public.project_external_refs USING btree (project_id, ref_type, status, conclusion, updated_at DESC);


--
-- Name: idx_project_initiative_recommendations_account_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_initiative_recommendations_account_status ON public.project_initiative_recommendations USING btree (account_id, status, updated_at DESC);


--
-- Name: idx_project_initiative_recommendations_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_initiative_recommendations_project_status ON public.project_initiative_recommendations USING btree (project_id, status, updated_at DESC);


--
-- Name: idx_project_memory_entry_context; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_memory_entry_context ON public.project_memory_repo_entries USING btree (project_id, status, kind, updated_at DESC);


--
-- Name: idx_project_memory_root_commit; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_memory_root_commit ON public.project_memory_repo_roots USING btree (project_id, COALESCE(repo_binding_id, ''::text), commit_sha, root_hash);


--
-- Name: idx_project_memory_root_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_memory_root_status ON public.project_memory_repo_roots USING btree (project_id, sync_status, created_at DESC);


--
-- Name: idx_project_memory_source_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_memory_source_project ON public.project_memory_repo_sources USING btree (project_id, sync_status, created_at DESC);


--
-- Name: idx_project_memory_sync_events_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_memory_sync_events_project ON public.project_memory_sync_events USING btree (project_id, event_type, status, created_at DESC);


--
-- Name: idx_project_roles_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_roles_account_id ON public.project_roles USING btree (account_id);


--
-- Name: idx_project_roles_account_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_roles_account_project ON public.project_roles USING btree (account_id, project_id);


--
-- Name: idx_project_roles_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_roles_project_id ON public.project_roles USING btree (project_id);


--
-- Name: idx_project_timeline_events_project_occurred; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_timeline_events_project_occurred ON public.project_timeline_events USING btree (project_id, occurred_at DESC, id DESC);


--
-- Name: idx_project_validations_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_validations_project ON public.project_validations USING btree (project_id, status, created_at DESC);


--
-- Name: idx_projects_owner_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_owner_created_at ON public.projects USING btree (owner_account_id, created_at DESC);


--
-- Name: idx_projects_parent_project_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_parent_project_id ON public.projects USING btree (parent_project_id);


--
-- Name: idx_projects_public_child_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_public_child_status_created ON public.projects USING btree (project_level, status, created_at DESC) WHERE (COALESCE((metadata ->> 'visibility'::text), 'market_public'::text) = 'market_public'::text);


--
-- Name: idx_projects_public_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_public_search ON public.projects USING gin (to_tsvector('simple'::regconfig, ((((((((((((COALESCE(project_no, ''::text) || ' '::text) || COALESCE(title, ''::text)) || ' '::text) || COALESCE(summary, ''::text)) || ' '::text) || COALESCE(one_sentence, ''::text)) || ' '::text) || COALESCE((metadata ->> 'description'::text), ''::text)) || ' '::text) || COALESCE((metadata ->> 'goal'::text), ''::text)) || ' '::text) || COALESCE((metadata ->> 'deliverables'::text), ''::text))));


--
-- Name: idx_projects_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_status_created_at ON public.projects USING btree (status, created_at DESC);


--
-- Name: idx_proof_assets_order_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proof_assets_order_status ON public.proof_assets USING btree (order_id, status);


--
-- Name: idx_proof_assets_order_visibility; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proof_assets_order_visibility ON public.proof_assets USING btree (order_id, visibility, status);


--
-- Name: idx_proof_assets_uploaded_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proof_assets_uploaded_by ON public.proof_assets USING btree (uploaded_by_account_id, created_at DESC);


--
-- Name: idx_proofs_order_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proofs_order_id ON public.proofs USING btree (order_id);


--
-- Name: idx_proofs_submitter_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_proofs_submitter_created ON public.proofs USING btree (submitted_by_account_id, created_at DESC);


--
-- Name: idx_repo_jobs_delivery_branch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_repo_jobs_delivery_branch ON public.repo_jobs USING btree (repo_url, head_branch, updated_at DESC) WHERE (job_type = 'delivery'::text);


--
-- Name: idx_repo_jobs_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_repo_jobs_order_no ON public.repo_jobs USING btree (order_no) WHERE (order_no IS NOT NULL);


--
-- Name: idx_repo_jobs_project_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_repo_jobs_project_no ON public.repo_jobs USING btree (project_no, job_type);


--
-- Name: idx_requests_actor_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_requests_actor_created_at ON public.requests USING btree (actor_account_id, created_at DESC);


--
-- Name: idx_requests_public_search; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_requests_public_search ON public.requests USING gin (to_tsvector('simple'::regconfig, ((((COALESCE(request_no, ''::text) || ' '::text) || COALESCE(title, ''::text)) || ' '::text) || COALESCE(description, ''::text))));


--
-- Name: idx_requests_public_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_requests_public_status_created ON public.requests USING btree (status, created_at DESC) WHERE (COALESCE((metadata ->> 'visibility'::text), 'market_public'::text) = 'market_public'::text);


--
-- Name: idx_requests_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_requests_status_created_at ON public.requests USING btree (status, created_at DESC);


--
-- Name: idx_risk_events_actor_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_events_actor_created ON public.risk_events USING btree (actor_ref, created_at DESC);


--
-- Name: idx_risk_events_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_events_created_at ON public.risk_events USING btree (created_at DESC);


--
-- Name: idx_risk_events_kind_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_events_kind_created_at ON public.risk_events USING btree (kind, created_at DESC);


--
-- Name: idx_risk_events_subject_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_risk_events_subject_created ON public.risk_events USING btree (subject_id, created_at DESC);


--
-- Name: idx_share_release_requests_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_share_release_requests_account ON public.share_release_requests USING btree (account_id, created_at DESC);


--
-- Name: idx_share_release_requests_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_share_release_requests_project_status ON public.share_release_requests USING btree (project_id, status);


--
-- Name: idx_share_release_requests_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_share_release_requests_status ON public.share_release_requests USING btree (status, created_at DESC);


--
-- Name: idx_share_settlement_holds_account_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_share_settlement_holds_account_status ON public.share_settlement_holds USING btree (account_id, status, created_at DESC);


--
-- Name: idx_share_settlement_holds_market_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_share_settlement_holds_market_status ON public.share_settlement_holds USING btree (market_id, status, created_at DESC);


--
-- Name: idx_shares_ledger_account_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_account_id ON public.shares_ledger USING btree (account_id);


--
-- Name: idx_shares_ledger_issuer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_issuer ON public.shares_ledger USING btree (issuer_type, issuer_id);


--
-- Name: idx_shares_ledger_market_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_market_id ON public.shares_ledger USING btree (market_id);


--
-- Name: idx_shares_ledger_project_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_project_item ON public.shares_ledger USING btree (project_id, item_id);


--
-- Name: idx_shares_ledger_release_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_release_request ON public.shares_ledger USING btree (share_release_request_id);


--
-- Name: idx_shares_ledger_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shares_ledger_source ON public.shares_ledger USING btree (source_type, source_id);


--
-- Name: idx_work_events_subject_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_events_subject_created ON public.work_events USING btree (subject_type, subject_id, created_at DESC);


--
-- Name: idx_work_items_account_status_ready; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_items_account_status_ready ON public.work_items USING btree (account_id, status, ready_at DESC, item_no DESC);


--
-- Name: idx_work_items_claim_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_items_claim_expires ON public.work_items USING btree (status, claim_expires_at) WHERE ((status = 'claimed'::text) AND (claim_expires_at IS NOT NULL));


--
-- Name: idx_work_receipts_run_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_receipts_run_created ON public.work_receipts USING btree (work_run_id, created_at DESC);


--
-- Name: idx_work_reviews_reviewer_status_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_reviews_reviewer_status_created ON public.work_reviews USING btree (reviewer_account_id, status, created_at DESC);


--
-- Name: idx_work_runs_actor_status_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_runs_actor_status_updated ON public.work_runs USING btree (actor_account_id, status, updated_at DESC);


--
-- Name: idx_work_trust_events_run_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_trust_events_run_created ON public.work_trust_events USING btree (work_run_id, created_at DESC);


--
-- Name: idx_work_trust_events_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_work_trust_events_type_status ON public.work_trust_events USING btree (event_type, status, updated_at DESC);


--
-- Name: idx_workbench_dismissals_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workbench_dismissals_account ON public.workbench_dismissals USING btree (account_id);


--
-- Name: idx_workbench_dismissals_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_workbench_dismissals_subject ON public.workbench_dismissals USING btree (subject_type, subject_id);


--
-- Name: oauth_identities_account_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX oauth_identities_account_idx ON public.oauth_identities USING btree (account_id);


--
-- Name: spring_session_ix1; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX spring_session_ix1 ON public.spring_session USING btree (session_id);


--
-- Name: spring_session_ix2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix2 ON public.spring_session USING btree (expiry_time);


--
-- Name: spring_session_ix3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX spring_session_ix3 ON public.spring_session USING btree (principal_name);


--
-- Name: ux_offers_offer_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_offers_offer_no ON public.offers USING btree (offer_no) WHERE (offer_no IS NOT NULL);


--
-- Name: ux_orders_order_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_orders_order_no ON public.orders USING btree (order_no);


--
-- Name: ux_payment_intents_payment_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_payment_intents_payment_no ON public.payment_intents USING btree (payment_no);


--
-- Name: ux_project_external_refs_pr; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_project_external_refs_pr ON public.project_external_refs USING btree (project_id, repo_url, pr_number, ref_type) WHERE (ref_type = 'pull_request'::text);


--
-- Name: ux_project_memory_entry; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_project_memory_entry ON public.project_memory_repo_entries USING btree (project_id, memory_id);


--
-- Name: ux_project_memory_source; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_project_memory_source ON public.project_memory_repo_sources USING btree (project_id, source_id, sha256);


--
-- Name: ux_project_roles_single_seat; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_project_roles_single_seat ON public.project_roles USING btree (project_id, role_code);


--
-- Name: ux_projects_project_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_projects_project_no ON public.projects USING btree (project_no);


--
-- Name: ux_projects_single_root; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_projects_single_root ON public.projects USING btree (project_level) WHERE (project_level = 'root'::text);


--
-- Name: ux_repo_jobs_provision_repo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_repo_jobs_provision_repo ON public.repo_jobs USING btree (job_type, provider, repo_owner, repo_name);


--
-- Name: ux_requests_request_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_requests_request_no ON public.requests USING btree (request_no) WHERE (request_no IS NOT NULL);


--
-- Name: ux_shares_ledger_source_account_reason; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_shares_ledger_source_account_reason ON public.shares_ledger USING btree (source_type, source_id, account_id, reason);


--
-- Name: ux_work_events_settlement_event_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_work_events_settlement_event_once ON public.work_events USING btree (subject_type, subject_id, event_type, action_id) WHERE (subject_type = 'settlement_event'::text);


--
-- Name: delivery_attempts delivery_attempts_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_attempts
    ADD CONSTRAINT delivery_attempts_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: delivery_attempts delivery_attempts_payment_intent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.delivery_attempts
    ADD CONSTRAINT delivery_attempts_payment_intent_id_fkey FOREIGN KEY (payment_intent_id) REFERENCES public.payment_intents(id) ON DELETE SET NULL;


--
-- Name: digital_inventory_items digital_inventory_items_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id);


--
-- Name: digital_inventory_items digital_inventory_items_delivered_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_delivered_order_id_fkey FOREIGN KEY (delivered_order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: digital_inventory_items digital_inventory_items_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id) ON DELETE CASCADE;


--
-- Name: digital_inventory_items digital_inventory_items_reserved_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.digital_inventory_items
    ADD CONSTRAINT digital_inventory_items_reserved_order_id_fkey FOREIGN KEY (reserved_order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: identity_badges identity_badges_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_badges
    ADD CONSTRAINT identity_badges_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: identity_badges identity_badges_source_fact_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_badges
    ADD CONSTRAINT identity_badges_source_fact_id_fkey FOREIGN KEY (source_fact_id) REFERENCES public.identity_facts(id) ON DELETE SET NULL;


--
-- Name: identity_facts identity_facts_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_facts
    ADD CONSTRAINT identity_facts_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: identity_facts identity_facts_challenge_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_facts
    ADD CONSTRAINT identity_facts_challenge_id_fkey FOREIGN KEY (challenge_id) REFERENCES public.identity_verification_challenges(id) ON DELETE SET NULL;


--
-- Name: identity_verification_challenges identity_verification_challenges_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.identity_verification_challenges
    ADD CONSTRAINT identity_verification_challenges_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: listings listings_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id);


--
-- Name: listings listings_opened_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.listings
    ADD CONSTRAINT listings_opened_by_account_id_fkey FOREIGN KEY (opened_by_account_id) REFERENCES public.accounts(id);


--
-- Name: market_items_read_model market_items_read_model_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_items_read_model
    ADD CONSTRAINT market_items_read_model_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: market_members market_members_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_members
    ADD CONSTRAINT market_members_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: market_members market_members_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.market_members
    ADD CONSTRAINT market_members_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id) ON DELETE CASCADE;


--
-- Name: markets markets_lead_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.markets
    ADD CONSTRAINT markets_lead_account_id_fkey FOREIGN KEY (lead_account_id) REFERENCES public.accounts(id);


--
-- Name: oauth_identities oauth_identities_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oauth_identities
    ADD CONSTRAINT oauth_identities_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: offers offers_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offers
    ADD CONSTRAINT offers_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id);


--
-- Name: order_events order_events_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id);


--
-- Name: order_events order_events_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_events
    ADD CONSTRAINT order_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_participants order_participants_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_participants
    ADD CONSTRAINT order_participants_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: order_participants order_participants_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_participants
    ADD CONSTRAINT order_participants_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_payment_state order_payment_state_latest_payment_intent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_payment_state
    ADD CONSTRAINT order_payment_state_latest_payment_intent_id_fkey FOREIGN KEY (latest_payment_intent_id) REFERENCES public.payment_intents(id) ON DELETE SET NULL;


--
-- Name: order_payment_state order_payment_state_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_payment_state
    ADD CONSTRAINT order_payment_state_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: order_progress_updates order_progress_updates_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_progress_updates
    ADD CONSTRAINT order_progress_updates_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id);


--
-- Name: order_progress_updates order_progress_updates_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_progress_updates
    ADD CONSTRAINT order_progress_updates_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_progress_updates order_progress_updates_submitted_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_progress_updates
    ADD CONSTRAINT order_progress_updates_submitted_by_account_id_fkey FOREIGN KEY (submitted_by_account_id) REFERENCES public.accounts(id);


--
-- Name: orders orders_accepted_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_accepted_by_account_id_fkey FOREIGN KEY (accepted_by_account_id) REFERENCES public.accounts(id);


--
-- Name: orders orders_claimed_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_claimed_by_account_id_fkey FOREIGN KEY (claimed_by_account_id) REFERENCES public.accounts(id);


--
-- Name: orders orders_dispute_cancelled_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_dispute_cancelled_by_account_id_fkey FOREIGN KEY (dispute_cancelled_by_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: orders orders_dispute_opened_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_dispute_opened_by_account_id_fkey FOREIGN KEY (dispute_opened_by_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: orders orders_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id);


--
-- Name: orders orders_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id);


--
-- Name: orders orders_parent_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_parent_order_id_fkey FOREIGN KEY (parent_order_id) REFERENCES public.orders(id);


--
-- Name: orders orders_proof_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_proof_id_fkey FOREIGN KEY (proof_id) REFERENCES public.proofs(id);


--
-- Name: orders orders_review_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_review_listing_id_fkey FOREIGN KEY (review_listing_id) REFERENCES public.listings(id);


--
-- Name: orders orders_reviewer_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_reviewer_account_id_fkey FOREIGN KEY (reviewer_account_id) REFERENCES public.accounts(id);


--
-- Name: orders orders_submitted_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_submitted_by_account_id_fkey FOREIGN KEY (submitted_by_account_id) REFERENCES public.accounts(id);


--
-- Name: organization_events organization_events_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_events
    ADD CONSTRAINT organization_events_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: organization_events organization_events_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_events
    ADD CONSTRAINT organization_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: password_reset_tokens password_reset_tokens_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: payment_intents payment_intents_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_intents
    ADD CONSTRAINT payment_intents_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: payment_intents payment_intents_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_intents
    ADD CONSTRAINT payment_intents_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: payment_provider_events payment_provider_events_payment_intent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_provider_events
    ADD CONSTRAINT payment_provider_events_payment_intent_id_fkey FOREIGN KEY (payment_intent_id) REFERENCES public.payment_intents(id) ON DELETE CASCADE;


--
-- Name: post_items_read_model post_items_read_model_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_item_id_fkey FOREIGN KEY (item_id) REFERENCES public.listings(id) ON DELETE CASCADE;


--
-- Name: post_items_read_model post_items_read_model_latest_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_latest_order_id_fkey FOREIGN KEY (latest_order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: post_items_read_model post_items_read_model_latest_payment_intent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_latest_payment_intent_id_fkey FOREIGN KEY (latest_payment_intent_id) REFERENCES public.payment_intents(id) ON DELETE SET NULL;


--
-- Name: post_items_read_model post_items_read_model_listing_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_listing_id_fkey FOREIGN KEY (listing_id) REFERENCES public.listings(id) ON DELETE CASCADE;


--
-- Name: post_items_read_model post_items_read_model_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.post_items_read_model
    ADD CONSTRAINT post_items_read_model_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id) ON DELETE CASCADE;


--
-- Name: project_external_refs project_external_refs_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_external_refs
    ADD CONSTRAINT project_external_refs_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_initiative_recommendations project_initiative_recommendations_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_initiative_recommendations
    ADD CONSTRAINT project_initiative_recommendations_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: project_initiative_recommendations project_initiative_recommendations_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_initiative_recommendations
    ADD CONSTRAINT project_initiative_recommendations_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_memory_repo_entries project_memory_repo_entries_approved_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_entries
    ADD CONSTRAINT project_memory_repo_entries_approved_by_account_id_fkey FOREIGN KEY (approved_by_account_id) REFERENCES public.accounts(id);


--
-- Name: project_memory_repo_entries project_memory_repo_entries_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_entries
    ADD CONSTRAINT project_memory_repo_entries_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id);


--
-- Name: project_memory_repo_entries project_memory_repo_entries_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_entries
    ADD CONSTRAINT project_memory_repo_entries_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_memory_repo_entries project_memory_repo_entries_root_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_entries
    ADD CONSTRAINT project_memory_repo_entries_root_id_fkey FOREIGN KEY (root_id) REFERENCES public.project_memory_repo_roots(id) ON DELETE SET NULL;


--
-- Name: project_memory_repo_roots project_memory_repo_roots_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_roots
    ADD CONSTRAINT project_memory_repo_roots_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_memory_repo_roots project_memory_repo_roots_repo_binding_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_roots
    ADD CONSTRAINT project_memory_repo_roots_repo_binding_id_fkey FOREIGN KEY (repo_binding_id) REFERENCES public.project_repo_bindings(id) ON DELETE SET NULL;


--
-- Name: project_memory_repo_sources project_memory_repo_sources_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_sources
    ADD CONSTRAINT project_memory_repo_sources_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_memory_repo_sources project_memory_repo_sources_root_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_repo_sources
    ADD CONSTRAINT project_memory_repo_sources_root_id_fkey FOREIGN KEY (root_id) REFERENCES public.project_memory_repo_roots(id) ON DELETE SET NULL;


--
-- Name: project_memory_sync_events project_memory_sync_events_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_sync_events
    ADD CONSTRAINT project_memory_sync_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_memory_sync_events project_memory_sync_events_root_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_memory_sync_events
    ADD CONSTRAINT project_memory_sync_events_root_id_fkey FOREIGN KEY (root_id) REFERENCES public.project_memory_repo_roots(id) ON DELETE SET NULL;


--
-- Name: project_repo_bindings project_repo_bindings_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_repo_bindings
    ADD CONSTRAINT project_repo_bindings_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id);


--
-- Name: project_repo_bindings project_repo_bindings_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_repo_bindings
    ADD CONSTRAINT project_repo_bindings_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_roles project_roles_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_roles
    ADD CONSTRAINT project_roles_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: project_roles project_roles_assigned_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_roles
    ADD CONSTRAINT project_roles_assigned_by_account_id_fkey FOREIGN KEY (assigned_by_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: project_roles project_roles_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_roles
    ADD CONSTRAINT project_roles_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_share_pools project_share_pools_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_share_pools
    ADD CONSTRAINT project_share_pools_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id) ON DELETE CASCADE;


--
-- Name: project_share_pools project_share_pools_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_share_pools
    ADD CONSTRAINT project_share_pools_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_timeline_events project_timeline_events_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_timeline_events
    ADD CONSTRAINT project_timeline_events_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: project_timeline_events project_timeline_events_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_timeline_events
    ADD CONSTRAINT project_timeline_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: project_timeline_events project_timeline_events_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_timeline_events
    ADD CONSTRAINT project_timeline_events_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_validations project_validations_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_validations
    ADD CONSTRAINT project_validations_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id);


--
-- Name: project_validations project_validations_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_validations
    ADD CONSTRAINT project_validations_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: project_validations project_validations_published_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_validations
    ADD CONSTRAINT project_validations_published_by_account_id_fkey FOREIGN KEY (published_by_account_id) REFERENCES public.accounts(id);


--
-- Name: project_validations project_validations_settled_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_validations
    ADD CONSTRAINT project_validations_settled_by_account_id_fkey FOREIGN KEY (settled_by_account_id) REFERENCES public.accounts(id);


--
-- Name: projects projects_owner_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_owner_account_id_fkey FOREIGN KEY (owner_account_id) REFERENCES public.accounts(id);


--
-- Name: projects projects_parent_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_parent_project_id_fkey FOREIGN KEY (parent_project_id) REFERENCES public.projects(id) ON DELETE RESTRICT;


--
-- Name: proof_assets proof_assets_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proof_assets
    ADD CONSTRAINT proof_assets_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: proofs proofs_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proofs
    ADD CONSTRAINT proofs_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: proofs proofs_parent_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proofs
    ADD CONSTRAINT proofs_parent_order_id_fkey FOREIGN KEY (parent_order_id) REFERENCES public.orders(id);


--
-- Name: proofs proofs_submitted_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proofs
    ADD CONSTRAINT proofs_submitted_by_account_id_fkey FOREIGN KEY (submitted_by_account_id) REFERENCES public.accounts(id);


--
-- Name: repo_jobs repo_jobs_created_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repo_jobs
    ADD CONSTRAINT repo_jobs_created_by_account_id_fkey FOREIGN KEY (created_by_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: repo_jobs repo_jobs_issued_to_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.repo_jobs
    ADD CONSTRAINT repo_jobs_issued_to_account_id_fkey FOREIGN KEY (issued_to_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: requests requests_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.requests
    ADD CONSTRAINT requests_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id);


--
-- Name: share_release_requests share_release_requests_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: share_release_requests share_release_requests_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id) ON DELETE CASCADE;


--
-- Name: share_release_requests share_release_requests_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: share_release_requests share_release_requests_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: share_release_requests share_release_requests_proof_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_proof_id_fkey FOREIGN KEY (proof_id) REFERENCES public.proofs(id) ON DELETE CASCADE;


--
-- Name: share_release_requests share_release_requests_requested_by_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_release_requests
    ADD CONSTRAINT share_release_requests_requested_by_account_id_fkey FOREIGN KEY (requested_by_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: share_settlement_holds share_settlement_holds_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: share_settlement_holds share_settlement_holds_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_item_id_fkey FOREIGN KEY (item_id) REFERENCES public.listings(id) ON DELETE SET NULL;


--
-- Name: share_settlement_holds share_settlement_holds_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id) ON DELETE CASCADE;


--
-- Name: share_settlement_holds share_settlement_holds_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;


--
-- Name: share_settlement_holds share_settlement_holds_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE SET NULL;


--
-- Name: share_settlement_holds share_settlement_holds_proof_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_proof_id_fkey FOREIGN KEY (proof_id) REFERENCES public.proofs(id) ON DELETE SET NULL;


--
-- Name: share_settlement_holds share_settlement_holds_share_release_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.share_settlement_holds
    ADD CONSTRAINT share_settlement_holds_share_release_request_id_fkey FOREIGN KEY (share_release_request_id) REFERENCES public.share_release_requests(id) ON DELETE SET NULL;


--
-- Name: shares_ledger shares_ledger_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- Name: shares_ledger shares_ledger_market_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_market_id_fkey FOREIGN KEY (market_id) REFERENCES public.markets(id);


--
-- Name: shares_ledger shares_ledger_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: shares_ledger shares_ledger_proof_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_proof_id_fkey FOREIGN KEY (proof_id) REFERENCES public.proofs(id);


--
-- Name: shares_ledger shares_ledger_share_release_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shares_ledger
    ADD CONSTRAINT shares_ledger_share_release_request_id_fkey FOREIGN KEY (share_release_request_id) REFERENCES public.share_release_requests(id) ON DELETE SET NULL;


--
-- Name: spring_session_attributes spring_session_attributes_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.spring_session_attributes
    ADD CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id) REFERENCES public.spring_session(primary_id) ON DELETE CASCADE;


--
-- Name: work_events work_events_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_events
    ADD CONSTRAINT work_events_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: work_events work_events_receipt_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_events
    ADD CONSTRAINT work_events_receipt_id_fkey FOREIGN KEY (receipt_id) REFERENCES public.work_receipts(id) ON DELETE SET NULL;


--
-- Name: work_items work_items_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_items
    ADD CONSTRAINT work_items_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: work_receipts work_receipts_work_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_receipts
    ADD CONSTRAINT work_receipts_work_run_id_fkey FOREIGN KEY (work_run_id) REFERENCES public.work_runs(id) ON DELETE CASCADE;


--
-- Name: work_reviews work_reviews_reviewer_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_reviews
    ADD CONSTRAINT work_reviews_reviewer_account_id_fkey FOREIGN KEY (reviewer_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: work_reviews work_reviews_work_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_reviews
    ADD CONSTRAINT work_reviews_work_run_id_fkey FOREIGN KEY (work_run_id) REFERENCES public.work_runs(id) ON DELETE CASCADE;


--
-- Name: work_runs work_runs_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_runs
    ADD CONSTRAINT work_runs_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE CASCADE;


--
-- Name: work_runs work_runs_work_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_runs
    ADD CONSTRAINT work_runs_work_item_id_fkey FOREIGN KEY (work_item_id) REFERENCES public.work_items(id) ON DELETE CASCADE;


--
-- Name: work_trust_events work_trust_events_actor_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_actor_account_id_fkey FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL;


--
-- Name: work_trust_events work_trust_events_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE SET NULL;


--
-- Name: work_trust_events work_trust_events_payment_intent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_payment_intent_id_fkey FOREIGN KEY (payment_intent_id) REFERENCES public.payment_intents(id) ON DELETE SET NULL;


--
-- Name: work_trust_events work_trust_events_review_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_review_id_fkey FOREIGN KEY (review_id) REFERENCES public.work_reviews(id) ON DELETE SET NULL;


--
-- Name: work_trust_events work_trust_events_work_run_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.work_trust_events
    ADD CONSTRAINT work_trust_events_work_run_id_fkey FOREIGN KEY (work_run_id) REFERENCES public.work_runs(id) ON DELETE CASCADE;


--
-- Name: workbench_dismissals workbench_dismissals_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workbench_dismissals
    ADD CONSTRAINT workbench_dismissals_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.accounts(id);


--
-- PostgreSQL database dump complete
--


