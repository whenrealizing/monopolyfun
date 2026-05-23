import { readdirSync } from "node:fs";
import { join } from "node:path";

const migrationDir = join(process.cwd(), "apps/api/src/main/resources/db/migration");
const migrationPattern = /^V(\d+)__.+\.sql$/;

const migrations = readdirSync(migrationDir)
  .filter((file) => file.endsWith(".sql"))
  .map((file) => {
    const match = migrationPattern.exec(file);
    return {
      file,
      version: match ? Number(match[1]) : null,
    };
  });

const invalidNames = migrations.filter((migration) => migration.version === null);
const byVersion = new Map();

for (const migration of migrations) {
  if (migration.version === null) {
    continue;
  }

  const existing = byVersion.get(migration.version) ?? [];
  existing.push(migration.file);
  byVersion.set(migration.version, existing);
}

const duplicateVersions = [...byVersion.entries()]
  .filter(([, files]) => files.length > 1)
  .sort(([left], [right]) => left - right);

if (invalidNames.length > 0 || duplicateVersions.length > 0) {
  // 中文注释：启动前阻断 Flyway 无法解析的迁移集合，避免错误延迟到 Spring 容器初始化阶段。
  for (const migration of invalidNames) {
    console.error(`Invalid Flyway migration filename: ${migration.file}`);
  }

  for (const [version, files] of duplicateVersions) {
    console.error(`Duplicate Flyway migration version V${version}:`);
    for (const file of files.sort()) {
      console.error(`- ${file}`);
    }
  }

  process.exit(1);
}

console.log(`flyway migration checks passed (${migrations.length} migrations)`);
