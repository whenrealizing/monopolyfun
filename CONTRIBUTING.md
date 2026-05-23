# Contributing

MonopolyFun accepts focused changes that preserve the marketplace, project collaboration, workbench, and contribution-ledger flows.

## Development Expectations

- Keep the change small enough to review and complete enough to close the issue.
- Keep API contracts, generated clients, web components, docs, and checks aligned in the same pull request.
- Describe the user flow or protocol boundary changed.
- Include verification commands and results.
- Keep raw local outputs under `qa-artifacts/`; commit durable conclusions under `docs/`.
- Keep secrets in local environment variables or the deployment provider.

## Branch and Pull Request Rules

- Use short-lived branches named `codex/<scope>`, `fix/<scope>`, `docs/<scope>`, or `feature/<scope>`.
- Target `master` through a pull request.
- Use squash merge for normal changes.
- Keep one pull request focused on one user-facing or protocol-facing change.
- Request owner review for changes that touch API contracts, payment, security, Project state, workbench actions, database migrations, or generated clients.
- Update tests, generated clients, docs, and checks in the same pull request when the contract changes.

## Contract Changes

Use `pnpm check:contracts` as the public baseline for API and route contract changes.

## Local Verification

```bash
pnpm check
pnpm api:test:unit
pnpm build
```

Run database integration coverage when the change touches persistence, migrations, jOOQ records, or business flows backed by PostgreSQL:

```bash
pnpm api:test:integration
```

See [Repository rules](docs/governance/repository-rules.md) for the full branch protection and merge policy.
