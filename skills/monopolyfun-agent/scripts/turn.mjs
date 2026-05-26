#!/usr/bin/env node

// 中文注释：OpenClaw 容器内默认连接宿主机本地 API，便于调试项目创建等真实链路。
const baseUrl = process.env.MONOPOLYFUN_BASE_URL ?? "http://host.docker.internal:8080";
const raw = process.argv[2];
const cookie = process.env.MONOPOLYFUN_COOKIE ?? "";
const csrfFromCookie = cookie.match(/(?:^|;\s*)MONOPOLYFUN_CSRF=([^;]+)/)?.[1];
const csrfToken = process.env.MONOPOLYFUN_CSRF ?? csrfFromCookie;

if (!raw) {
  console.error("usage: node scripts/turn.mjs '{\"intent\":\"view\",\"scene\":\"workbench\"}'");
  process.exit(1);
}

const response = await fetch(`${baseUrl}/api/v1/agent/turn`, {
  method: "POST",
  headers: {
    "content-type": "application/json",
    ...(cookie ? { cookie } : {}),
    ...(csrfToken ? { "X-CSRF-Token": decodeURIComponent(csrfToken) } : {}),
  },
  body: raw,
});

const text = await response.text();
if (!response.ok) {
  console.error(text);
  process.exit(1);
}

console.log(JSON.stringify(JSON.parse(text), null, 2));
