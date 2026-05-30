# Security

Report security issues through the private maintainer security channel for this repository. Include the affected route, API endpoint, reproduction steps, expected impact, and relevant logs with secrets redacted.

Security fixes target `master`.

## Production Configuration

Production configuration must provide rotated values for:

- `PAYMENT_CALLBACK_SECRET`
- `DIGITAL_INVENTORY_ENCRYPTION_SECRET`
- `DATABASE_PASSWORD`
- Upload credentials
- API base URLs
- Forgejo repository delivery credentials

Set `APP_PRODUCTION=true` and `COOKIE_SECURE=true` for shared or public deployments. Local fixture values in `.env.example` and `docker-compose.yml` are only for disposable development environments.

## Reportable Scope

- Authentication, authorization, session, CSRF, and account-risk boundaries.
- Payment callback, proof, order, Project, workbench, and share-release state transitions.
- Forgejo repository delivery, short-lived clone credentials, and pull-request publishing.
- Agent action routing, prompt-injection handling, and cross-account write protection.
- Secret exposure in source, docs, workflow files, generated clients, and committed artifacts.

## Local Security Checks

```bash
pnpm security:secrets
pnpm security:web
pnpm security:pr-policy
```

Run `pnpm qa:security` when preparing a release candidate.
