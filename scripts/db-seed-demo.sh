#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
. "$SCRIPT_DIR/db-lib.sh"

psql_cmd <<'SQL'
insert into accounts (id, handle, display_name, password_hash, metadata)
values
  ('acct-demo-seller', 'demo_seller', 'Demo Seller', null, '{"agentSummary":"Demo seller account"}'::jsonb),
  ('acct-demo-buyer', 'demo_buyer', 'Demo Buyer', null, '{"agentSummary":"Demo buyer account"}'::jsonb),
  ('acct-demo-reviewer', 'demo_reviewer', 'Demo Reviewer', null, '{"agentSummary":"Demo review desk"}'::jsonb)
on conflict (id) do nothing;

insert into offers (
  id, offer_no, actor_account_id, title, description, delivery_standard,
  price_amount, currency, payment_method, payment_profile, payment_network,
  payment_asset, inventory_policy, stock_total, stock_sold,
  status, metadata
)
values (
  'offer-demo-risk-radar',
  'MF260506OFFDEMO01',
  'acct-demo-seller',
  '风险日报页面交付',
  '抓取公开风险新闻并交付可访问日报页面。',
  '页面需要包含来源、时间、风险类型和样例数据截图。',
  9900,
  'USD',
  'okx_direct_pay',
  null,
  'xlayer',
  'USDC',
  'limited',
  3,
  0,
  'open',
  '{"tradeStatus":"open","visibility":"market_public","paymentRecipient":"0x0000000000000000000000000000000000000000"}'::jsonb
)
on conflict (id) do nothing;

insert into markets (
  id, name, summary, listing_goal, lead_account_id, source_ref, surface_url,
  settlement_type, next_curve_slot, status, lead_last_active_at, lead_seat_status, metadata
)
values (
  'mkt-public-offer-offer-demo-risk-radar',
  '风险日报页面交付',
  '围绕公开风险新闻交付可验收页面。',
  '买方可 claim 当前 post item 并进入订单履约。',
  'acct-demo-seller',
  'offer-demo-risk-radar',
  'http://localhost:3000/market/offers/MF260506OFFDEMO01',
  'money',
  0,
  'active',
  now(),
  'occupied',
  '{"source":"offer-bootstrap","postKind":"offer"}'::jsonb
)
on conflict (id) do nothing;

insert into listings (
  id, market_id, kind, parent_order_id, title, subject_type, subject_ref, deliverable_spec,
  proof_spec, settlement_spec, inventory_limit, active_orders_count, stock_total,
  settlement_type, status, opened_by_account_id, metadata
)
values (
  'listing-post-item-demo-risk-radar',
  'mkt-public-offer-offer-demo-risk-radar',
  'work',
  null,
  '风险日报页面',
  'post_item',
  'offer-demo-risk-radar',
  '交付一个可访问的风险日报页面。',
  '必须提交页面链接、截图或 PDF 附件，并说明数据来源。',
  'buyer pays money after acceptance',
  3,
  0,
  3,
  'money',
  'open',
  'acct-demo-seller',
  jsonb_build_object(
    'postKind', 'offer',
    'postId', 'offer-demo-risk-radar',
    'itemKind', 'work',
    'summary', '交付一个可访问的风险日报页面。',
    'fulfillmentMode', 'staged_work',
    'deliveryMode', 'manual_delivery',
    'deliverySource', 'manual_user',
    'acceptanceCriteria', jsonb_build_array('页面链接可访问', '包含来源、时间和风险类型', '提供截图或 PDF 附件'),
    'priority', 'medium',
    'priceAmount', 9900,
    'currency', 'USD',
    'paymentMethod', 'okx_direct_pay',
    'paymentNetwork', 'xlayer',
    'paymentRecipient', '0x0000000000000000000000000000000000000000',
    'lockTimeoutSeconds', 1800,
    'progressTimeoutSeconds', 1800
  )
)
on conflict (id) do nothing;
SQL

echo "current post demo seed applied"
