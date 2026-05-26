#!/usr/bin/env node
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { privateKeyToAccount } from "viem/accounts";

const XLAYER_CHAIN_ID = 196;
const TRANSFER_WITH_AUTHORIZATION_TYPES = {
  TransferWithAuthorization: [
    { name: "from", type: "address" },
    { name: "to", type: "address" },
    { name: "value", type: "uint256" },
    { name: "validAfter", type: "uint256" },
    { name: "validBefore", type: "uint256" },
    { name: "nonce", type: "bytes32" },
  ],
};

async function main() {
  const input = readInput();
  const requirements = input.requirements ?? input.paymentRequirements ?? input.accepted ?? input;
  const signed = await signX402PaymentPayload({
    requirements,
    privateKey: input.privateKey,
    nowSeconds: input.nowSeconds,
    nonce: input.nonce,
  });
  process.stdout.write(`${JSON.stringify(signed, null, 2)}\n`);
}

async function signX402PaymentPayload(input) {
  const privateKey = normalizePrivateKey(input.privateKey ?? requireEnv("PRIVATE_KEY"));
  const account = privateKeyToAccount(privateKey);
  const nowSeconds = input.nowSeconds ?? Math.floor(Date.now() / 1000);
  const maxTimeout = readRequirementNumber(input.requirements, "maxTimeoutSeconds", 300);
  const authorization = {
    from: account.address,
    to: readRequirementAddress(input.requirements, "payTo"),
    value: readRequirementString(input.requirements, "amount"),
    validAfter: String(Math.max(0, nowSeconds - 5)),
    validBefore: String(nowSeconds + maxTimeout),
    nonce: input.nonce ?? randomBytes32(),
  };
  const extra = readRequirementExtra(input.requirements);
  const typedData = {
    domain: {
      name: extra.name,
      version: extra.version,
      chainId: XLAYER_CHAIN_ID,
      verifyingContract: readRequirementAddress(input.requirements, "asset"),
    },
    types: TRANSFER_WITH_AUTHORIZATION_TYPES,
    primaryType: "TransferWithAuthorization",
    message: {
      ...authorization,
      value: BigInt(authorization.value),
      validAfter: BigInt(authorization.validAfter),
      validBefore: BigInt(authorization.validBefore),
    },
  };
  // 中文注释：skill 脚本让 agent 在没有浏览器钱包时仍能用测试私钥生成标准 x402 paymentPayload。
  const signature = await account.signTypedData(typedData);
  return {
    payer: account.address,
    paymentPayload: {
      x402Version: 2,
      accepted: input.requirements,
      payload: { authorization, signature },
    },
    typedData: {
      ...typedData,
      message: authorization,
    },
  };
}

function readInput() {
  const args = process.argv.slice(2);
  const fileFlagIndex = args.findIndex((arg) => arg === "--requirements-file" || arg === "--input-file");
  if (fileFlagIndex >= 0) {
    const file = args[fileFlagIndex + 1];
    if (!file) {
      throw new Error("--requirements-file 需要文件路径");
    }
    return JSON.parse(readFileSync(resolve(process.cwd(), file), "utf8"));
  }
  const inline = args.find((arg) => arg.trim().startsWith("{"));
  if (inline) {
    return JSON.parse(inline);
  }
  throw new Error("用法：node scripts/x402-private-key.mjs '{\"requirements\":{...}}'");
}

function requireEnv(name) {
  const value = process.env[name] ?? readDotEnv()[name];
  if (!value || !value.trim()) {
    throw new Error(`缺少 ${name}`);
  }
  return value.trim();
}

let dotEnvCache;
function readDotEnv() {
  if (dotEnvCache) {
    return dotEnvCache;
  }
  const envPath = findRepoEnv();
  dotEnvCache = envPath ? parseEnvFile(readFileSync(envPath, "utf8")) : {};
  return dotEnvCache;
}

function findRepoEnv() {
  let cursor = dirname(fileURLToPath(import.meta.url));
  while (true) {
    const candidate = resolve(cursor, ".env");
    if (existsSync(candidate)) {
      return candidate;
    }
    const parent = dirname(cursor);
    if (parent === cursor) {
      return null;
    }
    cursor = parent;
  }
}

function parseEnvFile(content) {
  const result = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const separator = trimmed.indexOf("=");
    if (separator < 1) {
      continue;
    }
    const key = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim().replace(/^['"]|['"]$/g, "");
    result[key] = value;
  }
  return result;
}

function normalizePrivateKey(value) {
  const normalized = value.startsWith("0x") ? value : `0x${value}`;
  if (!/^0x[0-9a-fA-F]{64}$/.test(normalized)) {
    throw new Error("PRIVATE_KEY 格式无效");
  }
  return normalized;
}

function randomBytes32() {
  return `0x${randomBytes(32).toString("hex")}`;
}

function readRequirementString(requirements, key) {
  const value = requirements[key];
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`OKX paymentRequirements 缺少 ${key}`);
  }
  return value.trim();
}

function readRequirementAddress(requirements, key) {
  const value = readRequirementString(requirements, key);
  if (!/^0x[0-9a-fA-F]{40}$/.test(value)) {
    throw new Error(`OKX paymentRequirements ${key} 地址格式无效`);
  }
  return value;
}

function readRequirementNumber(requirements, key, fallback) {
  const value = requirements[key];
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return fallback;
}

function readRequirementExtra(requirements) {
  const extra = requirements.extra;
  if (!extra || typeof extra !== "object" || Array.isArray(extra)) {
    throw new Error("OKX paymentRequirements 缺少 extra");
  }
  const name = extra.name;
  const version = extra.version;
  if (typeof name !== "string" || typeof version !== "string") {
    throw new Error("OKX paymentRequirements 缺少 extra.name/version");
  }
  return { name, version };
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exit(1);
});
