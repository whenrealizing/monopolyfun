# Revenue Distribution Testchain

This smoke runs the WorkThread revenue path against a local Anvil chain.

Production defaults are BSC `eip155:56` + native BNB. The user-facing OpenClaw flow asks for project/company/goal/task context only; chain, token, router, RPC, and computed revenue amount are initialized by system configuration and pricing logic.

Required production env:

```bash
MONOPOLYFUN_REVENUE_CHAIN_ID=eip155:56
MONOPOLYFUN_REVENUE_CHAIN_NAME=BSC
MONOPOLYFUN_REVENUE_ASSET=BNB
MONOPOLYFUN_REVENUE_TOKEN_TYPE=native
MONOPOLYFUN_REVENUE_TOKEN_ADDRESS=0x0000000000000000000000000000000000000000
MONOPOLYFUN_REVENUE_ROUTER_ADDRESS=<bsc revenue router>
MONOPOLYFUN_REVENUE_RPC_EIP155_56=<bsc rpc url>
```

Local smoke overrides the backend revenue address to Anvil `eip155:31337`, because CI and laptop runs need deterministic receipt verification without spending mainnet BNB.

It verifies:

- backend WorkThread settlement creates shares;
- backend distribution creates a claimable amount;
- local `RevenueDistributor` receives mock USDC revenue;
- member wallet claims mock USDC on-chain;
- backend claim stores the real transaction hash;
- backend `claimed` status advances only after receipt, `Claimed` event, and `Transfer` event verification.

Run:

```bash
pnpm testchain:revenue
```

The script starts temporary Postgres, API, and Anvil processes, then writes evidence under `docs/evidence/testchain/`.

Fast path:

```bash
pnpm testchain:revenue
node tools/openclaw/full-e2e-from-zero.mjs
```

Full OpenClaw LLM watchdog path:

```bash
OPENCLAW_E2E_FORCE_OPENCLAW=1 OPENCLAW_E2E_LLM_TIMEOUT_MS=3000 node tools/openclaw/full-e2e-from-zero.mjs
```
