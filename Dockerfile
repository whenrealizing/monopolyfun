FROM node:22-bookworm AS web-build

WORKDIR /workspace

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json

RUN corepack enable && pnpm install --frozen-lockfile

COPY apps/web ./apps/web

ENV NEXT_PUBLIC_API_BASE_URL=
ENV API_BASE_URL=http://127.0.0.1:18080

RUN pnpm --filter @monopolyfun/web build

FROM maven:3.9.11-eclipse-temurin-21 AS api-build

WORKDIR /workspace

COPY apps/api/pom.xml apps/api/pom.xml
COPY apps/api/src ./apps/api/src

RUN mvn -f apps/api/pom.xml package -DskipTests

FROM node:22-bookworm AS node-runtime

FROM eclipse-temurin:21-jre

USER root
WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends ca-certificates bash curl \
  && rm -rf /var/lib/apt/lists/*

COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=api-build /workspace/apps/api/target/monopolyfun-api-0.1.0-SNAPSHOT.jar /app/api/app.jar
COPY --from=web-build /workspace/apps/web/.next/standalone /app/web/
COPY --from=web-build /workspace/apps/web/.next/static /app/web/apps/web/.next/static
# 中文注释：Next standalone 镜像不会自动携带 public 目录，品牌图和 favicon 需要显式放入运行时。
COPY --from=web-build /workspace/apps/web/public /app/web/apps/web/public
COPY scripts/railway-all-in-one-entrypoint.sh /app/entrypoint.sh

RUN chmod +x /app/entrypoint.sh

EXPOSE 3000

ENTRYPOINT ["/app/entrypoint.sh"]
