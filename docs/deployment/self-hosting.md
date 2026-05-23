# Self-hosting

This guide describes the reproducible MonopolyFun stack for local evaluation and shared deployments.

## Stack

- `web`: Next.js application on port `3000`.
- `api`: Spring Boot API on port `8080`.
- `postgres`: PostgreSQL with pgvector on host port `55432`.
- `pnpm check`: public contract, migration, route, and open-source readiness gate.

## Local Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Open the web app at `http://localhost:3000` and the API health endpoint at `http://localhost:8080/actuator/health`.

## Local Development Mode

Use this path when changing code and running the services from source.

```bash
pnpm install
cp .env.example .env
pnpm dev
```

Run a single surface when the other surface is already available:

```bash
pnpm api:dev
pnpm web:dev
```

## Required Configuration

The default `.env.example` values target a disposable local environment.

Set these values for any shared deployment:

- `APP_PRODUCTION=true`
- `COOKIE_SECURE=true`
- `WEB_ORIGIN`
- `NEXT_PUBLIC_API_BASE_URL`
- `API_BASE_URL`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `PAYMENT_CALLBACK_SECRET`
- `DIGITAL_INVENTORY_ENCRYPTION_SECRET`
- Upload provider credentials when proof uploads use object storage.
- OAuth callback URLs and credentials when GitHub login or identity verification is enabled.

## Public Ports

| Service | Default host port | Purpose |
| --- | ---: | --- |
| Web | `3000` | Browser UI |
| API | `8080` | REST API and health endpoint |
| PostgreSQL | `55432` | Local database access |

Override ports with `WEB_HOST_PORT`, `API_HOST_PORT`, and `POSTGRES_HOST_PORT`.

## Verification

Run these commands before publishing or handing off a deployment:

```bash
pnpm check
pnpm api:test:unit
pnpm build
pnpm check:open-source-readiness
```

For database integration coverage:

```bash
pnpm api:test:integration
```

For security release checks:

```bash
pnpm security:secrets
pnpm security:web
pnpm security:pr-policy
pnpm qa:security
```

## Release Notes

Capture durable deployment conclusions under `docs/` and keep raw local command output under `qa-artifacts/`.

