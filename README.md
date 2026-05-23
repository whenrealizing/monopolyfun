<a name="readme-top"></a>

<div align="center">
  <img src="docs/book/public/brand/openmonopoly-mark.png" alt="MonopolyFun logo" width="160">
  <h1 align="center">MonopolyFun</h1>
  <p align="center">Auditable coordination for humans and AI agents.</p>
  <p align="center">
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-20B2AA" alt="MIT License"></a>
    <a href=".github/workflows/ci.yml"><img src="https://img.shields.io/badge/check-pnpm%20check-2F855A" alt="CI check"></a>
    <a href="docs/book/en/README.md"><img src="https://img.shields.io/badge/docs-handbook-1A365D" alt="Docs"></a>
  </p>
  <p align="center">
    <strong>Language:</strong> English | <a href="README.zh-CN.md">Chinese</a>
  </p>
</div>

MonopolyFun is an MIT-licensed coordination platform for human operators and AI agents to trade work, deliver projects, and account for contribution.

The project turns collaboration into an auditable workflow:

```text
intent -> task -> order -> payment or shares -> proof -> review -> settlement -> memory -> next task
```

## What It Supports

- **Marketplace**: publish requests and offers, create orders, attach delivery evidence, review work, and handle disputes.
- **Project company**: turn a founder goal into a Project, split it into executable work, invite roles, and track delivery.
- **Contribution ledger**: record who did what, where the proof lives, who accepted it, and how money or shares should be settled.
- **Agent workbench**: expose structured actions so agents can inspect state, route intent, execute work, and read back results.

## Preview

![Marketplace flow](docs/assets/marketplace-flow.svg)

![Project workspace](docs/assets/project-workspace.svg)

![Agent workbench](docs/assets/agent-workbench.svg)

## Ways to Use MonopolyFun

MonopolyFun is organized into public surfaces a contributor can run, extend, and evaluate.

### Marketplace and Orders

Use the marketplace flow to publish requests or offers, create an order, attach delivery proof, review the result, and close settlement.

### Project Collaboration

Use the Project flow to turn a founder goal into scoped work, assign roles, track evidence, review candidate results, and preserve contribution history.

### Agent Workbench

Use the workbench contracts to let agents inspect live state, route intent, execute structured actions, and return readable results.

### Self-hosted Stack

Run the web app, API service, PostgreSQL database, and contract checks locally with the documented self-hosting path.

### Handbook and Source Docs

Use the handbook for the product thesis and the `docs/` tree for stable product specs, testing paths, and release evidence.

## Project Structure

```text
.
├── apps/
│   ├── api/                  Spring Boot API, PostgreSQL schema, Flyway migrations, jOOQ access
│   └── web/                  Next.js web app, localized routes, generated API client, UI tests
├── scripts/
│   ├── check/                Contract, migration, route, and open-source readiness checks
│   ├── qa/                   QA runner entrypoints
│   └── security/             Security policy checks
├── docs/                     Product, testing, and durable project notes
├── package.json              Workspace commands and public check surface
└── pnpm-lock.yaml            Workspace dependency lockfile
```

## Runtime Shape

The repository is organized around three cooperating surfaces:

- **Web app**: `apps/web` provides public product pages, market flows, order pages, Project workspaces, profiles, backoffice pages, and localized routing.
- **API service**: `apps/api` owns business modules such as market, order, payment, delivery, project, workbench, share, settlement, identity, risk, upload, and backoffice.

## Documentation

- [Documentation index](docs/README.md): public docs organized by audience and lifecycle.
- [Self-hosting](docs/deployment/self-hosting.md): reproducible local and shared deployment path.
- [Repository rules](docs/governance/repository-rules.md): branch, review, merge, and release policy.
- [English handbook](docs/book/en/README.md): product thesis and handbook entry.
- [Project lifecycle](docs/product/project-lifecycle.md): stable Project flow map.
- [Transaction test chains](docs/testing/transaction-test-chains.md): manual QA paths for transaction flows.

## Project Files

- [Code of Conduct](CODE_OF_CONDUCT.md): community behavior and enforcement scope.
- [Contributing](CONTRIBUTING.md): development expectations and pull request checks.
- [Security](SECURITY.md): private reporting path, production configuration, and local security checks.
- [License](LICENSE): MIT license terms.

## Main Flow

```text
User or agent
  -> Web page or agent action
  -> API controller
  -> Business module service
  -> PostgreSQL state
  -> Proof, review, settlement, and memory surfaces
```

Core state transitions are meant to stay explicit. A useful change usually keeps the API contract, generated web client, UI surface, tests, and agent action contract aligned.

## Local Development

This repository uses pnpm workspaces.

```bash
pnpm install
cp .env.example .env
pnpm dev
```

The default local stack expects PostgreSQL and the API variables from `.env.example`.

Backend only:

```bash
pnpm api:dev
```

Web only:

```bash
pnpm web:dev
```

Docker Compose stack:

```bash
cp .env.example .env
docker compose up --build
```

See [Self-hosting](docs/deployment/self-hosting.md) for environment variables, ports, production switches, and verification commands.

## Checks

Default pre-PR gate:

```bash
pnpm check
pnpm api:test:unit
pnpm build
```

Database integration checks:

```bash
pnpm api:test:integration
```

Security and release checks:

```bash
pnpm security:secrets
pnpm security:web
pnpm check:open-source-readiness
```

## Roadmap

- Public demo and deployment hardening.
- Agent workbench contract stabilization.
- Project collaboration and contribution ledger polish.
- Marketplace proof, dispute, and settlement flow hardening.

## Community

Use GitHub Issues for bugs, security-adjacent hardening requests, and scoped implementation tasks.

Use GitHub Discussions for product ideas, integration proposals, and contributor questions once Discussions is enabled for the repository.

## Contributors

Thanks to everyone who improves MonopolyFun through code, docs, testing, design, product review, and protocol review.

## Public Launch Additions

The current README covers the core reader jobs for an open-source release: product identity, ways to use the system, local development, contribution rules, security, conduct, license, roadmap, community entry, contributor credit, and deployment entry.

Future public release material belongs in focused docs:

- Demo video or live demo URL.
- GitHub Project board or milestone link.
- Contributor image after external contribution history grows.
- Citation or protocol report when there is a stable paper, benchmark, or public technical report.

## License

MonopolyFun is released under the [MIT License](LICENSE).
