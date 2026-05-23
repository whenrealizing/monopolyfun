import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { diagrams } from "../../docs/book/.vitepress/theme/diagram-data.mjs";

const ROOT = process.cwd();
const BOOK_DIR = path.join(ROOT, "docs/book");
const COMPONENT_RE = /<DocDiagram\s+id="([^"]+)"\s*\/>/g;
const STATIC_SVG_RE = /\/diagrams\/([^"\s)]+)\.svg/g;

async function listMarkdownFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (entry.name.startsWith(".vitepress") || entry.name === "public" || entry.name === "diagrams") continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...await listMarkdownFiles(fullPath));
    if (entry.isFile() && entry.name.endsWith(".md")) files.push(fullPath);
  }
  return files;
}

function validateDiagramShape(id, diagram) {
  if (!diagram?.type) return [`${id}: missing type`];
  if (diagram.type === "flow" && !Array.isArray(diagram.labels)) return [`${id}: flow labels must be an array`];
  if (diagram.type === "hub" && (!diagram.center || !Array.isArray(diagram.items))) return [`${id}: hub requires center and items`];
  if (diagram.type === "sequence" && (!Array.isArray(diagram.actors) || !Array.isArray(diagram.steps))) return [`${id}: sequence requires actors and steps`];
  if (diagram.type === "architecture" && !Array.isArray(diagram.layers)) return [`${id}: architecture layers must be an array`];
  return [];
}

async function main() {
  const files = await listMarkdownFiles(BOOK_DIR);
  const errors = [];
  const used = new Set();

  // 中文注释：构建前校验所有 Markdown 图表引用，确保文档站使用组件图表且引用 id 可解析。
  for (const file of files) {
    const source = await readFile(file, "utf8");
    const relative = path.relative(ROOT, file);
    for (const match of source.matchAll(STATIC_SVG_RE)) {
      const id = match[1];
      errors.push(`${relative}: static SVG diagram ref "${id}" must use DocDiagram`);
    }

    for (const match of source.matchAll(COMPONENT_RE)) {
      const id = match[1];
      used.add(id);
      if (!diagrams[id]) errors.push(`${relative}: unknown DocDiagram id "${id}"`);
    }
  }

  for (const [id, diagram] of Object.entries(diagrams)) {
    errors.push(...validateDiagramShape(id, diagram));
  }

  if (errors.length) {
    console.error(errors.map((error) => `[docs-diagrams] ${error}`).join("\n"));
    process.exit(1);
  }

  console.log(`[docs-diagrams] checked ${used.size} component diagrams across ${files.length} markdown files`);
}

await main();
