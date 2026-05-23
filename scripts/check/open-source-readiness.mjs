#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const requiredFiles = [
  "LICENSE",
  "README.md",
  "README.zh-CN.md",
  "CONTRIBUTING.md",
  "CODE_OF_CONDUCT.md",
  "SECURITY.md",
  ".github/CODEOWNERS",
  ".github/pull_request_template.md",
  ".github/ISSUE_TEMPLATE/bug_report.md",
  ".github/ISSUE_TEMPLATE/feature_request.md",
  "docs/README.md",
  "docs/assets/marketplace-flow.svg",
  "docs/assets/project-workspace.svg",
  "docs/assets/agent-workbench.svg",
  "docs/deployment/self-hosting.md",
  "docs/governance/repository-rules.md",
  "docs/open-source-manifesto-zh.md",
  "docs/product/project-lifecycle.md",
  "docs/testing/transaction-test-chains.md",
];
const requiredReadmeSections = [
  ["README.md", "## Project Structure"],
  ["README.md", "## Ways to Use MonopolyFun"],
  ["README.md", "## Documentation"],
  ["README.md", "## Project Files"],
  ["README.md", "## Roadmap"],
  ["README.md", "## Community"],
  ["README.md", "## Contributors"],
  ["README.md", "## Public Launch Additions"],
  ["README.md", "## License"],
  ["README.zh-CN.md", "## 项目结构"],
  ["README.zh-CN.md", "## 使用方式"],
  ["README.zh-CN.md", "## 文档"],
  ["README.zh-CN.md", "## 项目文件"],
  ["README.zh-CN.md", "## 路线图"],
  ["README.zh-CN.md", "## 社区"],
  ["README.zh-CN.md", "## 贡献者"],
  ["README.zh-CN.md", "## 公开发布补充项"],
  ["README.zh-CN.md", "## 许可证"],
];

const errors = [];
const forbiddenPublicPaths = [
  "scripts/agent-system",
  "scripts/openclaw",
  "scripts/project-smoke",
  "scripts/project-memory",
  "scripts/ci/notify-feishu.mjs",
];
const forbiddenPackageScripts = [
  /^agent:/,
  /^openclaw:/,
  /^okx:/,
  "qa:agent:mock",
  "observability:health",
  "db:repair:listings",
];

for (const file of requiredFiles) {
  if (!existsSync(resolve(repoRoot, file))) {
    errors.push(`missing required open-source file: ${file}`);
  }
}

// 中文注释：英文 README 与中文 README 分别承载开源入口，检查关键章节避免治理信息漂移。
for (const [file, heading] of requiredReadmeSections) {
  const readmeText = readFileSync(resolve(repoRoot, file), "utf8");
  if (!readmeText.includes(heading)) {
    errors.push(`${file} missing open-source section: ${heading}`);
  }
}

// 中文注释：英文入口必须保持纯英文，避免 GitHub 首屏和英文手册夹带中文。
for (const file of ["README.md", "docs/book/en/README.md"]) {
  const text = readFileSync(resolve(repoRoot, file), "utf8");
  if (/[\u3400-\u9fff]/.test(text)) {
    errors.push(`${file} contains Chinese characters in an English-facing document`);
  }
}

// 中文注释：发布包的 scripts 目录只保留外部贡献者可复现入口，维护者私有 harness 留在私有流程。
for (const file of forbiddenPublicPaths) {
  if (existsSync(resolve(repoRoot, file))) {
    errors.push(`internal script path must stay out of public scripts: ${file}`);
  }
}

const packageJson = JSON.parse(readFileSync(resolve(repoRoot, "package.json"), "utf8"));
for (const name of Object.keys(packageJson.scripts ?? {})) {
  if (forbiddenPackageScripts.some((rule) => (rule instanceof RegExp ? rule.test(name) : rule === name))) {
    errors.push(`internal npm script must stay private: ${name}`);
  }
}

const tracked = gitLsFiles();

// 中文注释：开源仓库只保留可复现脚本和结论文档，原始 smoke transcript 归为本地生成物。
for (const file of tracked) {
  if ((file === ".env" || /^\.env\./.test(file)) && file !== ".env.example") {
    errors.push(`tracked environment file is not allowed: ${file}`);
  }
  if (file.startsWith("qa-artifacts/")) {
    errors.push(`tracked raw QA artifact is not allowed: ${file}`);
  }
}

// 中文注释：双语 README 是开源入口，内部链接断裂会直接阻塞外部贡献者上手。
for (const file of ["README.md", "README.zh-CN.md"]) {
  for (const link of markdownLinks(file)) {
    if (isExternalLink(link) || link.startsWith("#")) {
      continue;
    }
    const target = link.split("#")[0];
    if (!target || !existsSync(resolve(repoRoot, target))) {
      errors.push(`${file} link target missing: ${link}`);
    }
  }
}

if (errors.length > 0) {
  process.stderr.write(`${errors.join("\n")}\n`);
  process.exit(1);
}

process.stdout.write("open-source readiness checks passed\n");

function gitLsFiles() {
  return execFileSync("git", ["ls-files"], { cwd: repoRoot, encoding: "utf8" })
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);
}

function markdownLinks(file) {
  const text = readFileSync(resolve(repoRoot, file), "utf8");
  return [...text.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)]
    .map((match) => match[1].trim())
    .filter(Boolean);
}

function isExternalLink(value) {
  return /^[a-z][a-z0-9+.-]*:/i.test(value);
}
