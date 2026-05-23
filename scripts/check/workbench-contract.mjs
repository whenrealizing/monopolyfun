import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = process.cwd();
const panelPath = join(root, "apps/web/components/workbench/workbench-panel.tsx");
const apiPath = join(root, "apps/web/lib/api/index.ts");

const panel = readFileSync(panelPath, "utf8");
const api = readFileSync(apiPath, "utf8");

const checks = [
  {
    ok: api.includes("export function readWorkbenchProjection"),
    message: "apps/web/lib/api/index.ts must export the single Workbench projection parser.",
  },
  {
    ok: api.includes("export function readWorkbenchItem") && api.includes("export function readWorkbenchItems"),
    message: "apps/web/lib/api/index.ts must own Workbench item parsing.",
  },
  {
    ok: panel.includes("listWorkbenchItems") && api.includes("return readWorkbenchItems(await Api.listWorkbenchItems())"),
    message: "WorkbenchPanel must use the shared Workbench parser.",
  },
  {
    ok: !panel.includes("function readWorkbenchItem(") && !panel.includes("function readWorkbenchItems("),
    message: "WorkbenchPanel must not keep a local Workbench parser.",
  },
  {
    ok: panel.includes("selected.actions.map"),
    message: "WorkbenchPanel must render buttons from selected.actions.",
  },
  {
    ok: panel.includes('action.id === "open"') && panel.includes('action.id === "dismiss"'),
    message: "WorkbenchPanel must handle open and dismiss actions from backend actions.",
  },
  {
    ok: panel.includes("router.push(item.targetHref)") && !panel.includes("buildHrefFromPointer"),
    message: "WorkbenchPanel open action must use business targetHref directly.",
  },
  {
    ok: panel.includes("dismissWorkbenchItem(itemId)") && !panel.includes("executeAgentTurn"),
    message: "WorkbenchPanel dismiss action must use the Workbench business API.",
  },
];

const failures = checks.filter((check) => !check.ok);
if (failures.length > 0) {
  for (const failure of failures) {
    console.error(failure.message);
  }
  process.exit(1);
}

console.log("workbench contract checks passed");
