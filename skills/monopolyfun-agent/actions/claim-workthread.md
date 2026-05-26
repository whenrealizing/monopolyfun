# Claim WorkThread

Use when a developer says they want to take a WorkThread or start the bounty task.

Execution:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

API behavior:

- Resolve project context and the first open WorkThread when the user omits an id.
- POST `/api/v1/work-threads/{threadId}/claim`.
- Read `/api/v1/work-threads/{threadId}/packet`.

Required user-visible outcome: claimed WorkThread id and packet summary.
