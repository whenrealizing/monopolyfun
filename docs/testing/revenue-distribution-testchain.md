# Revenue Distribution Testchain

This smoke runs the WorkThread revenue path against a local Anvil chain.

Current defaults use local Anvil `eip155:31337` + mock USDC as the canonical revenue track for demos. The user-facing OpenClaw flow asks for project/company/goal/task context only; chain, token, router, RPC, and computed revenue amount are initialized by system configuration and pricing logic.

Default revenue env:

```bash
MONOPOLYFUN_REVENUE_CHAIN_ID=eip155:31337
MONOPOLYFUN_REVENUE_CHAIN_NAME=Anvil
MONOPOLYFUN_REVENUE_ASSET=USDC
MONOPOLYFUN_REVENUE_TOKEN_TYPE=mock-erc20
MONOPOLYFUN_REVENUE_TOKEN_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
MONOPOLYFUN_REVENUE_ROUTER_ADDRESS=0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512
MONOPOLYFUN_REVENUE_RPC_EIP155_31337=http://anvil:8545
```

Docker Compose runs `testchain-deploy` after Anvil is healthy, so the configured token and router addresses exist before the API starts.

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
