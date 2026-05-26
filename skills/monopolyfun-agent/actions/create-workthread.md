# Create WorkThread

Use when the owner asks to create a bounty-style project task for OpenClaw or a developer.

Execution:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

API behavior:

- Resolve `projectNo` or project name to internal `projectId`.
- POST `/api/v1/projects/{projectId}/work-threads`.
- Include current `actorAccountId`.
- Read back `/api/v1/projects/{projectId}/workroom`.

Required user-visible outcome: WorkThread id, project number, and next action for the developer.
