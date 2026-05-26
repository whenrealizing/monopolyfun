#!/usr/bin/env node

import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const baseUrl = process.env.MONOPOLYFUN_BASE_URL ?? "http://host.docker.internal:8080";
const { operationId, flags } = parseArgs(process.argv.slice(2));
const skillDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const defaultSnapshot = resolve(skillDir, "references/openapi-snapshot.json");

if (!operationId || flags.has("help")) {
  console.error("usage: node scripts/openapi-operation.mjs submitWorkReceipt [--minimal-body] [--refresh]");
  process.exit(1);
}

const { text, source } = await loadSpecText();
const result = extractOpenApiOperation(text, operationId);
const bodySchema = requestBodySchema(result.operation);
const schemas = rootSchemas(text);
const minimalBody = bodySchema ? buildMinimalBody(bodySchema, schemas) : undefined;
console.log(JSON.stringify({
  ...result,
  referencedSchemas: referencedSchemas(result.operation, schemas),
  ...(flags.has("minimal-body") ? { minimalBody } : {}),
  source,
}, null, 2));
process.exit(0);

console.error(`operationId not found: ${operationId}`);
process.exit(1);

async function loadSpecText() {
  const configuredSnapshot = process.env.MONOPOLYFUN_OPENAPI_FILE;
  if (configuredSnapshot) {
    return readSnapshotText(configuredSnapshot);
  }

  if (process.env.MONOPOLYFUN_OPENAPI_REFRESH === "1" || flags.has("refresh")) {
    try {
      const response = await fetch(`${baseUrl}/v3/api-docs`);
      const text = await response.text();
      if (response.ok && looksLikeOpenApiJson(text)) {
        return {
          source: `${baseUrl}/v3/api-docs`,
          text,
        };
      }
      throw new Error(`unexpected OpenAPI response: ${response.status} ${text.slice(0, 120)}`);
    } catch (error) {
      // 中文注释：运行时刷新失败时回退到随 skill 固化的快照，避免 HTML 错页污染 operation 解析。
      console.error(`runtime OpenAPI refresh failed, using ${defaultSnapshot}: ${error.message}`);
    }
  }

  return readSnapshotText(defaultSnapshot);
}

async function readSnapshotText(path) {
  return {
    source: path,
    text: await readFile(path, "utf8"),
  };
}

function extractOpenApiOperation(rawDoc, targetOperationId) {
  const exact = extractByOperationId(rawDoc, targetOperationId);
  if (exact) {
    return exact;
  }
  const matched = extractByAgentIntent(rawDoc, targetOperationId);
  if (matched) {
    return matched;
  }
  throw new Error(`operationId or x-agent intent not found: ${targetOperationId}`);
}

function extractByOperationId(rawDoc, targetOperationId) {
  let doc;
  try {
    doc = JSON.parse(rawDoc);
  } catch {
    return extractByOperationIdWindow(rawDoc, targetOperationId);
  }
  for (const [path, pathItem] of Object.entries(doc.paths ?? {})) {
    for (const method of ["get", "post", "put", "patch", "delete"]) {
      const operation = pathItem?.[method];
      if (operation?.operationId === targetOperationId) {
        // 中文注释：readback 里也会出现 operationId 字符串，先按 operation 对象精确匹配可避免误命中。
        return {
          path,
          method: method.toUpperCase(),
          operation,
          matchedBy: "operationId",
        };
      }
    }
  }
  return null;
}

function extractByOperationIdWindow(rawDoc, targetOperationId) {
  const operationIndex = rawDoc.search(new RegExp(`"operationId"\\s*:\\s*"${escapeRegExp(targetOperationId)}"`));
  if (operationIndex < 0) {
    return null;
  }
  const methodMatch = lastMatchBefore(rawDoc, /"(get|post|put|patch|delete)"\s*:\s*\{/g, operationIndex);
  const pathMatch = lastMatchBefore(rawDoc, /"(\/[^"]+)"\s*:\s*\{/g, methodMatch?.index ?? operationIndex);
  if (!methodMatch || !pathMatch) {
    return null;
  }
  const operationStart = methodMatch.index + methodMatch[0].lastIndexOf("{");
  const operationEnd = findMatchingBrace(rawDoc, operationStart);
  if (operationEnd < operationIndex) {
    return null;
  }
  // 中文注释：只解析命中的 operation 小窗口，避免让 agent 读取整份 OpenAPI。
  const operation = JSON.parse(rawDoc.slice(operationStart, operationEnd + 1));
  return {
    path: pathMatch[1],
    method: methodMatch[1].toUpperCase(),
    operation,
    matchedBy: "operationId",
  };
}

function extractByAgentIntent(rawDoc, query) {
  let doc;
  try {
    doc = JSON.parse(rawDoc);
  } catch {
    return null;
  }
  const normalizedQuery = normalize(query);
  for (const [path, pathItem] of Object.entries(doc.paths ?? {})) {
    for (const method of ["get", "post", "put", "patch", "delete"]) {
      const operation = pathItem?.[method];
      if (!operation) {
        continue;
      }
      const agent = operation["x-agent"];
      if (!agent) {
        continue;
      }
      const haystack = [
        ...(Array.isArray(agent.intents) ? agent.intents : []),
        agent.description,
        operation.summary,
        operation.description,
      ].filter(Boolean).map(normalize).join(" ");
      if (haystack.includes(normalizedQuery) || normalizedQuery.includes(haystack)) {
        // 中文注释：用户只给自然语言意图时，直接返回带 x-agent 的 operation，减少手工查找步骤。
        return {
          path,
          method: method.toUpperCase(),
          operation,
          matchedBy: "x-agent.intent",
        };
      }
    }
  }
  return null;
}

function normalize(value) {
  return String(value ?? "").trim().toLowerCase();
}

function parseArgs(argv) {
  const flags = new Set();
  let operationId = null;
  for (const arg of argv) {
    if (arg.startsWith("--")) {
      flags.add(arg.slice(2));
    } else if (!operationId) {
      operationId = arg;
    }
  }
  return { operationId, flags };
}

function looksLikeOpenApiJson(text) {
  const trimmed = text.trimStart();
  return trimmed.startsWith("{") && trimmed.includes("\"openapi\"");
}

function rootSchemas(rawDoc) {
  try {
    return JSON.parse(rawDoc).components?.schemas ?? {};
  } catch {
    return {};
  }
}

function requestBodySchema(operation) {
  const content = operation?.requestBody?.content;
  return content?.["application/json"]?.schema ?? null;
}

function referencedSchemas(operation, schemas) {
  const names = new Set();
  collectRefs(operation, names);
  const result = {};
  for (const name of names) {
    collectRefs(schemas[name], names);
  }
  for (const name of names) {
    if (schemas[name]) {
      result[name] = schemas[name];
    }
  }
  return result;
}

function collectRefs(value, names) {
  if (!value || typeof value !== "object") {
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item) => collectRefs(item, names));
    return;
  }
  if (typeof value.$ref === "string") {
    const name = value.$ref.split("/").pop();
    if (name) {
      names.add(name);
    }
  }
  for (const item of Object.values(value)) {
    collectRefs(item, names);
  }
}

function buildMinimalBody(schema, schemas, seen = new Set()) {
  const resolved = resolveSchema(schema, schemas, seen);
  if (!resolved) {
    return {};
  }
  if (resolved.type === "array") {
    return [buildMinimalBody(resolved.items ?? {}, schemas, seen)];
  }
  if (resolved.enum?.length) {
    return resolved.enum[0];
  }
  if (resolved.type === "boolean") {
    return false;
  }
  if (resolved.type === "integer" || resolved.type === "number") {
    return 0;
  }
  if (resolved.type === "string" || resolved.format) {
    return exampleString(resolved);
  }
  const properties = resolved.properties ?? {};
  const required = Array.isArray(resolved.required) ? resolved.required : Object.keys(properties);
  const body = {};
  for (const key of required) {
    if (properties[key]) {
      body[key] = buildMinimalBody(properties[key], schemas, seen);
    }
  }
  return body;
}

function resolveSchema(schema, schemas, seen) {
  if (!schema || typeof schema !== "object") {
    return null;
  }
  if (schema.$ref) {
    const name = String(schema.$ref).split("/").pop();
    if (!name || seen.has(name)) {
      return {};
    }
    seen.add(name);
    return resolveSchema(schemas[name], schemas, seen);
  }
  if (Array.isArray(schema.allOf)) {
    return Object.assign({}, ...schema.allOf.map((part) => resolveSchema(part, schemas, seen)));
  }
  if (Array.isArray(schema.oneOf) && schema.oneOf[0]) {
    return resolveSchema(schema.oneOf[0], schemas, seen);
  }
  if (Array.isArray(schema.anyOf) && schema.anyOf[0]) {
    return resolveSchema(schema.anyOf[0], schemas, seen);
  }
  return schema;
}

function exampleString(schema) {
  if (schema.example) return schema.example;
  if (schema.format === "date-time") return "2026-01-01T00:00:00Z";
  if (schema.format === "date") return "2026-01-01";
  return "";
}

function lastMatchBefore(text, pattern, beforeIndex) {
  let match;
  let last = null;
  pattern.lastIndex = 0;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index >= beforeIndex) {
      break;
    }
    last = match;
  }
  return last;
}

function findMatchingBrace(text, startIndex) {
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = startIndex; index < text.length; index += 1) {
    const char = text[index];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === "\"") {
        inString = false;
      }
      continue;
    }
    if (char === "\"") {
      inString = true;
      continue;
    }
    if (char === "{") {
      depth += 1;
    } else if (char === "}") {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }
  throw new Error(`OpenAPI object brace not closed near index ${startIndex}`);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
