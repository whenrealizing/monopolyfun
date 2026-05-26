# Review WorkThread

Use when the owner accepts or rejects a WorkThread result.

Execution:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

API behavior:

- Resolve project context and the submitted WorkThread.
- POST `/api/v1/work-threads/{threadId}/review`.
- Read `/api/v1/projects/{projectId}/workroom`.

Required user-visible outcome: accepted or rejected review state and contribution status.
