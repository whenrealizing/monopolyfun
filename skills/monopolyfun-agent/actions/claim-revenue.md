# Claim Revenue

Use when a contributor asks what they can claim, provides a wallet address, or sends a txHash after chain claim.

Execution:

```bash
MONOPOLYFUN_BASE_URL="$(cat /home/node/.openclaw/monopolyfun/base-url.txt 2>/dev/null || printf http://host.docker.internal:8080)" MONOPOLYFUN_HANDLE_FILE=/home/node/.openclaw/credentials/monopolyfun-handle.txt MONOPOLYFUN_LOGIN_FILE=/home/node/.openclaw/credentials/monopolyfun-login.txt MONOPOLYFUN_SESSION_CACHE_FILE=/home/node/.openclaw/monopolyfun/runtime-session.json node /home/node/.openclaw/skills/monopolyfun-agent/scripts/agent-turn.mjs --text '<raw user message>'
```

API behavior:

- Resolve project context and latest distribution period when omitted.
- POST `/api/v1/projects/{projectId}/distributions/{period}/claim`.
- With wallet only, return amount and proof.
- With wallet plus txHash, record submitted claim.
- With txHash only after an earlier wallet claim, record submitted claim by reusing the saved wallet from MonopolyFun state.
- User text containing a txHash only means “record this transaction”. After an authorized system verifier checks the receipt and transfer event, call the same API with `txConfirmed=true` to mark the claim as claimed.

Safety:

- The default skill action records backend claim state and leaves chain confirmation to the verifier.
- Chain signing needs an explicit approved wallet flow.
- Do not ask the user to repeat wallet address when txHash is the only missing post-claim field.

Required user-visible outcome: claimable amount and proof count, recorded txHash and submitted status, or confirmed claim status.
