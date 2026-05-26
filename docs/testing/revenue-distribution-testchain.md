# Revenue Distribution Testchain

This smoke runs the WorkThread revenue path against a local Anvil chain.

It verifies:

- backend WorkThread settlement creates shares;
- backend distribution creates a claimable amount;
- local `RevenueDistributor` receives mock USDC revenue;
- member wallet claims mock USDC on-chain;
- backend claim stores the real transaction hash.

Run:

```bash
pnpm testchain:revenue
```

The script starts temporary Postgres, API, and Anvil processes, then writes evidence under `docs/evidence/testchain/`.
