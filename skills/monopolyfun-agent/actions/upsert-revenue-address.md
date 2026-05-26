# Upsert Revenue Address

Use when the owner binds a testchain or production revenue distributor contract for a project.

Execution:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

API behavior:

- Resolve project context.
- POST `/api/v1/projects/{projectId}/revenue-address`.
- Require `chainId`, `contractAddress`, and `tokenAddress`.

Required user-visible outcome: bound chain id, distributor address, token address, and project number.
