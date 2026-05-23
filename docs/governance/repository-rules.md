# Repository Rules

This document defines the operating rules for the `master` branch, pull requests, and release hygiene.

## Default Branch

- `master` is the only long-lived branch.
- `master` should always be releasable.
- Direct pushes to `master` are reserved for repository bootstrap, emergency recovery, and explicit maintainer maintenance.
- Normal work enters through pull requests.

## Branch Naming

Use short-lived branches:

- `codex/<scope>` for Codex-generated implementation work.
- `fix/<scope>` for defect fixes.
- `docs/<scope>` for documentation-only changes.
- `feature/<scope>` for product or protocol additions.

Delete merged branches after merge.

## Pull Request Requirements

Every pull request should include:

- A concise problem statement.
- A summary of changed behavior or changed repository policy.
- Verification commands and results.
- Screenshots or rendered artifacts for visible UI and documentation changes.
- Migration, rollback, or compatibility notes when persistence or public contracts change.

## Required Checks

The intended protected-branch checks are:

- `build-and-test`
- `security`

The local equivalent is:

```bash
pnpm check
pnpm api:test:unit
pnpm build
```

Run integration tests for database, migration, order, payment, Project, workbench, and generated-client changes:

```bash
pnpm api:test:integration
```

Run release security checks before public deployment:

```bash
pnpm security:secrets
pnpm security:web
pnpm security:pr-policy
pnpm qa:security
```

## Review Rules

Owner review is required for changes touching:

- Authentication, authorization, session, CSRF, or account-risk boundaries.
- Payment callbacks, proof review, order state, settlement, and share release.
- Project state machines, candidate review, contribution ledger, and workbench actions.
- Flyway migrations, jOOQ-generated access, and generated web clients.
- CI, repository governance, security policy, and deployment settings.

## Merge Policy

- Use squash merge for normal pull requests.
- Keep merge commits disabled for ordinary work.
- Keep rebase merge disabled for ordinary work.
- Delete head branches after merge.
- Keep pull requests focused enough that the squash commit message remains meaningful.

## GitHub Settings

Configured repository settings:

- Issues: enabled.
- Discussions: enabled.
- Squash merge: enabled.
- Merge commits: disabled.
- Rebase merge: disabled.
- Delete branch on merge: enabled.
- Allow update branch: enabled.
- Wiki: disabled.

## Branch Protection Target

When GitHub branch protection is available for this repository, protect `master` with:

- Require a pull request before merging.
- Require approvals: `1`.
- Require review from Code Owners.
- Dismiss stale approvals when new commits are pushed.
- Require status checks to pass before merging.
- Require branches to be up to date before merging.
- Required checks: `build-and-test`, `security`.
- Require conversation resolution before merging.
- Require linear history.
- Restrict deletions.
- Block force pushes.
- Apply the same restrictions to administrators during normal operation.

The current repository is private. GitHub returned `HTTP 403` for branch protection because this account or repository plan does not expose private-repository branch protection. The policy above should be enabled when the repository is made public or branch protection becomes available.

