#!/usr/bin/env node

import {
  DEFAULT_BASE_URL,
  apiJson,
  buildFailurePayload,
  formatHelp,
  parseArgs,
  printJson,
  readOption,
  resolveRuntimeAuth,
} from "./runtime-session.mjs";

const DEFAULT_WEB_ORIGIN = "http://localhost:3000";
const DEFAULT_LIMIT = 5;
const MAX_FEED_LIMIT = 48;

const QUERY_EXPANSIONS = new Map([
  ["绘图", ["绘图", "AI绘图", "画画", "插画", "海报", "头像", "出图", "视觉设计", "prompt"]],
  ["画画", ["画画", "绘图", "AI绘图", "插画", "头像", "海报"]],
  ["海报", ["海报", "宣传图", "Banner", "视觉设计", "AI绘图", "绘图"]],
  ["头像", ["头像", "插画", "绘图", "AI绘图", "角色"]],
  ["设计", ["设计", "视觉设计", "UI", "海报", "Banner", "Logo"]],
]);

const { flags } = parseArgs(process.argv.slice(2));

if (flags.has("help")) {
  process.stdout.write(formatHelp([
    "usage: node scripts/market-discover.mjs --q 绘图 --side buy --limit 5",
    "",
    "options:",
    "  --q          natural-language search term",
    "  --side       buy or sell; buy searches offers, sell searches requests",
    "  --mode       find-first, publish-first, or direct-publish",
    "  --kind       offer, request, project, or all; default offer",
    "  --status     market status, default open",
    "  --limit      max ranked candidates, default 5",
    "  --base-url   api base url, default http://host.docker.internal:8080",
    "  --handle     runtime handle for authenticated resolve API",
    "  --password   runtime password for authenticated resolve API",
    "  --web-origin public web origin for candidate URLs, default http://localhost:3000",
  ]));
  process.exit(0);
}

try {
  const query = readOption(flags, "q", {
    defaultValue: "",
  });
  const kind = readOption(flags, "kind", {
    defaultValue: "offer",
  });
  const side = readOption(flags, "side", {
    defaultValue: kind === "request" ? "sell" : "buy",
  });
  const status = readOption(flags, "status", {
    defaultValue: "open",
  });
  const mode = readOption(flags, "mode", {
    defaultValue: "find_first",
  }).replace(/-/g, "_");
  const limit = numberOption(readOption(flags, "limit", {
    defaultValue: String(DEFAULT_LIMIT),
  }), DEFAULT_LIMIT);
  const baseUrl = readOption(flags, "base-url", {
    envKeys: ["MONOPOLYFUN_BASE_URL"],
    defaultValue: DEFAULT_BASE_URL,
  });
  const webOrigin = readOption(flags, "web-origin", {
    envKeys: ["MONOPOLYFUN_WEB_ORIGIN", "WEB_ORIGIN"],
    defaultValue: DEFAULT_WEB_ORIGIN,
  });

  const resolved = await tryResolveMarketIntent({ flags, baseUrl, side, mode, query, limit });
  if (resolved) {
    printJson({
      status: "ok",
      source: "resolveMarketIntent",
      query,
      side,
      mode,
      decision: resolved.decision,
      searchKind: resolved.searchKind,
      matches: resolved.matches ?? [],
      publishDraft: resolved.publishDraft ?? null,
      nextActions: resolved.nextActions ?? [],
      metadata: resolved.metadata ?? {},
    });
    process.exit(0);
  }

  const terms = expandedTerms(query);
  const feedLimit = Math.min(MAX_FEED_LIMIT, Math.max(24, limit * 4));
  const feeds = [];
  for (const term of terms) {
    feeds.push({
      term,
      feed: await readMarketFeed({ baseUrl, kind, status, q: term, limit: feedLimit }),
    });
  }
  if (feeds.every((entry) => feedCount(entry.feed) === 0)) {
    feeds.push({
      term: "",
      feed: await readMarketFeed({ baseUrl, kind, status, q: "", limit: feedLimit }),
      fallback: "recent_open_market",
    });
  }

  const posts = dedupePosts(feeds.flatMap((entry) => postsFromFeed(entry.feed)));
  const candidates = [];
  for (const post of posts) {
    const items = await readPostItems(baseUrl, post.postNo);
    for (const item of items) {
      const ranked = rankCandidate({ post, item, query, terms, webOrigin });
      if (query.trim() === "" || ranked.matchedFields.length > 0) {
        candidates.push(ranked);
      }
    }
  }

  candidates.sort((left, right) => right.score - left.score || compareText(left.title, right.title));
  printJson({
    status: "ok",
    query,
    kind,
    terms,
    searched: feeds.map((entry) => ({
      term: entry.term,
      fallback: entry.fallback ?? null,
      counts: entry.feed?.counts ?? {},
    })),
    candidates: candidates.slice(0, limit),
    nextAction: candidates.length === 0
      ? "No market candidate matched. Ask the user for a broader keyword or browse recent open market items."
      : "Ask the user to choose one candidate before claim/payment.",
  });
} catch (error) {
  printJson(buildFailurePayload(error, {
    status: "blocked",
    phase: "market_discovery",
  }));
  process.exit(1);
}

async function tryResolveMarketIntent(input) {
  try {
    const runtime = await resolveRuntimeAuth({
      baseUrl: input.baseUrl,
      handle: readOption(input.flags, "handle", { envKeys: ["MONOPOLYFUN_HANDLE"] }),
      password: readOption(input.flags, "password", { envKeys: ["MONOPOLYFUN_PASSWORD"] }),
      loginFile: readOption(input.flags, "login-file", { envKeys: ["MONOPOLYFUN_LOGIN_FILE"] }),
      cookieHeader: readOption(input.flags, "cookie", { envKeys: ["MONOPOLYFUN_COOKIE"] }),
      csrfToken: readOption(input.flags, "csrf-token", { envKeys: ["MONOPOLYFUN_CSRF_TOKEN"] }),
      sessionCacheFile: readOption(input.flags, "session-cache-file", { envKeys: ["MONOPOLYFUN_SESSION_CACHE_FILE"] }),
    });
    return await apiJson(runtime.session, runtime.baseUrl, "POST", "/api/v1/market/intents/resolve", {
      side: input.side,
      mode: input.mode,
      text: input.query,
      limit: input.limit,
    });
  } catch (error) {
    if (error.status === 401 || error.status === 403 || error.status === 404) {
      return null;
    }
    return null;
  }
}

function expandedTerms(query) {
  const normalized = String(query ?? "").trim();
  if (!normalized) {
    return [""];
  }
  const terms = new Set([normalized]);
  for (const [needle, values] of QUERY_EXPANSIONS.entries()) {
    if (normalized.includes(needle)) {
      values.forEach((value) => terms.add(value));
    }
  }
  return [...terms];
}

async function readMarketFeed(input) {
  const params = new URLSearchParams({
    kind: input.kind,
    status: input.status,
    sort: "recent",
    limit: String(input.limit),
  });
  if (input.q) {
    params.set("q", input.q);
  }
  return readJson(`${input.baseUrl}/api/v1/public/market-feed?${params.toString()}`);
}

async function readPostItems(baseUrl, postNo) {
  if (!postNo) {
    return [];
  }
  try {
    return await readJson(`${baseUrl}/api/v1/posts/${encodeURIComponent(postNo)}/items`);
  } catch {
    return [];
  }
}

async function readJson(url) {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : {};
  if (!response.ok) {
    const error = new Error(`GET ${url} failed`);
    error.status = response.status;
    error.body = body;
    throw error;
  }
  return body;
}

function postsFromFeed(feed) {
  return [
    ...postList(feed?.offers, "offer"),
    ...postList(feed?.requests, "request"),
    ...postList(feed?.projects, "project"),
  ];
}

function postList(items, kind) {
  return Array.isArray(items)
    ? items.map((item) => normalizePost(item, kind)).filter((item) => item.postNo)
    : [];
}

function normalizePost(item, kind) {
  const postNo = item.offerNo ?? item.requestNo ?? item.projectNo ?? item.no ?? "";
  return {
    kind,
    postNo,
    title: item.title ?? "",
    description: item.description ?? item.summary ?? "",
    actorHandle: item.actorHandle ?? item.ownerHandle ?? "",
    status: item.status ?? "",
  };
}

function dedupePosts(posts) {
  const byKey = new Map();
  for (const post of posts) {
    byKey.set(`${post.kind}:${post.postNo}`, post);
  }
  return [...byKey.values()];
}

function rankCandidate(input) {
  const item = input.item;
  const post = input.post;
  const fieldTexts = {
    itemTitle: item.title,
    itemSummary: item.summary,
    itemDeliverable: item.deliverableSpec,
    itemAcceptance: item.acceptanceSpec,
    itemAgentInstruction: item.agentInstruction,
    postTitle: post.title,
    postDescription: post.description,
  };
  const matchedFields = [];
  let score = 0;
  for (const [field, text] of Object.entries(fieldTexts)) {
    const hits = matchingTerms(text, input.terms);
    if (hits.length === 0) {
      continue;
    }
    matchedFields.push({ field, terms: hits });
    score += hits.length * fieldWeight(field);
  }
  if (String(item.status ?? "").toLowerCase() === "open") {
    score += 1;
  }
  const path = post.kind === "request"
    ? "requests"
    : post.kind === "project"
      ? "projects"
      : "offers";
  return {
    kind: post.kind,
    postNo: post.postNo,
    itemId: item.id,
    title: item.title,
    summary: item.summary,
    priceAmount: item.priceAmount ?? item.budgetAmount ?? null,
    currency: item.currency ?? null,
    sellerHandle: post.actorHandle,
    status: item.status,
    publicUrl: `${trimRight(input.webOrigin)}/market/${path}/${encodeURIComponent(post.postNo)}`,
    score,
    matchedFields,
    matchReason: matchedFields.length === 0
      ? "recent_open_market_item"
      : matchedFields.map((entry) => `${entry.field}:${entry.terms.join("/")}`).join(", "),
    item: {
      deliverableSpec: item.deliverableSpec,
      acceptanceCriteria: item.acceptanceCriteria ?? [],
      agentInstruction: item.agentInstruction,
      paymentMethod: item.paymentMethod,
    },
    post: {
      title: post.title,
      description: post.description,
    },
  };
}

function matchingTerms(text, terms) {
  const haystack = String(text ?? "").toLowerCase();
  if (!haystack) {
    return [];
  }
  return terms
    .filter((term) => term)
    .filter((term) => haystack.includes(String(term).toLowerCase()));
}

function fieldWeight(field) {
  if (field === "itemTitle") {
    return 8;
  }
  if (field.startsWith("item")) {
    return 4;
  }
  if (field === "postTitle") {
    return 3;
  }
  return 2;
}

function feedCount(feed) {
  return Number(feed?.counts?.offer ?? 0)
    + Number(feed?.counts?.request ?? 0)
    + Number(feed?.counts?.project ?? 0);
}

function numberOption(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : fallback;
}

function compareText(left, right) {
  return String(left ?? "").localeCompare(String(right ?? ""));
}

function trimRight(value) {
  return String(value ?? "").replace(/\/+$/, "");
}
