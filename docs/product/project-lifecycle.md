# Project Lifecycle

Updated: 2026-05-22

This document is the stable product-level source for the first Project lifecycle in MonopolyFun.

## Core Loop

```text
Project
  -> work pool
  -> result submission
  -> review
  -> virtual shares
  -> contribution record
  -> project memory
```

The first Project experience centers on work. A user enters a project, finds available work, claims one item, submits a result, receives review, and earns virtual shares after acceptance.

## User Language

| System object | Product language | User meaning |
| --- | --- | --- |
| Project | Project | A long-lived collaboration workspace |
| Project item / Validation task | Work | A concrete task someone can claim and finish |
| Proof | Result | Delivery evidence such as a PR, document, screenshot, or dataset |
| Review | Review | Acceptance decision for a submitted result |
| Reward | Virtual shares | Immediate contribution credit after acceptance |
| Shares / reward facts | Contribution record | Long-term record of who contributed what |
| Memory | Project memory | Accepted facts and context for future people and agents |
| Workbench | Workbench | Action queue for people and agents |

## First-Version Boundary

The first version includes:

- Project creation and public discovery.
- Work creation, claiming, progress, and result submission.
- Review, requested changes, and acceptance.
- Virtual share reward calculation.
- Contribution records and project memory updates.
- Workbench actions for pending human and agent work.

Later product extensions include:

- Direction markets.
- Competing project tracks.
- Revenue distribution.
- Virtual share trading.
- Company fork mechanics.

## Implementation Mapping

```text
Project
  -> market-facing project entry
  -> project work items
  -> validation launches
  -> validation tasks
  -> proofs
  -> reviews
  -> rewards
  -> share records
  -> memory entries
```

The product experience presents this as a single work loop. The backend keeps the lower-level protocol objects because they make validation, audit, and agent execution explicit.

## Key Code Entrances

```text
apps/api/src/main/java/com/monopolyfun/modules/project/api/ProjectController.java
apps/api/src/main/java/com/monopolyfun/modules/post/service/command/PostCommandService.java
apps/api/src/main/java/com/monopolyfun/modules/project/protocol/ProjectValidationProtocolService.java
apps/api/src/main/java/com/monopolyfun/modules/share/service/ProjectSharePoolService.java
apps/api/src/main/java/com/monopolyfun/modules/workbench/service/agent/WorkbenchAgentSceneOwner.java
apps/web/app/[locale]/market/projects/[projectNo]/page.tsx
apps/web/components/project-validation-panel.tsx
```

## Maintenance Rule

New Project docs, UI copy, and API-facing explanations should map back to the same six concepts: Project, work, result, review, virtual shares, and project memory.
