import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";
import { dirname, resolve } from "node:path";
import { networkInterfaces } from "node:os";
import { fileURLToPath } from "node:url";

const appRoot = dirname(fileURLToPath(import.meta.url));
const workspaceRoot = resolve(appRoot, "../..");
const withNextIntl = createNextIntlPlugin("./i18n/request.ts");
const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "";
const serverApiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:8080";
const isDevelopment = process.env.NODE_ENV === "development";
const allowedDevOrigins = buildAllowedDevOrigins(process.env.NEXT_ALLOWED_DEV_ORIGINS);
const scriptSrc = [
  "'self'",
  "'unsafe-inline'",
  // 中文注释：Railway/Cloudflare 注入的 Web Analytics 脚本需要显式放行，否则线上控制台会持续报 CSP 拦截。
  "https://static.cloudflareinsights.com",
  // 中文注释：React/Next 开发调试需要动态执行能力，生产环境继续使用更收敛的脚本策略。
  ...(isDevelopment ? ["'unsafe-eval'"] : []),
].join(" ");
const connectSrc = [
  "'self'",
  apiBaseUrl,
  // 中文注释：Cloudflare Web Analytics 上报端点跟随统计脚本放行，避免脚本加载后被 connect-src 再次拦截。
  "https://cloudflareinsights.com",
  "https://static.cloudflareinsights.com",
].filter(Boolean).join(" ");
const csp = [
  "default-src 'self'",
  `connect-src ${connectSrc}`,
  "img-src 'self' data: https:",
  `script-src ${scriptSrc}`,
  "style-src 'self' 'unsafe-inline'",
  "font-src 'self' data:",
  "frame-ancestors 'self'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

const nextConfig: NextConfig = {
  output: "standalone",
  allowedDevOrigins,
  turbopack: {
    root: workspaceRoot,
  },
  async rewrites() {
    // 中文注释：单域名部署时浏览器请求同源 /api，由 Next 转发到容器内 Spring API。
    const routes = [
      {
        source: "/api/:path*",
        destination: `${serverApiBaseUrl.replace(/\/+$/, "")}/api/:path*`,
      },
    ];
    if (isDevelopment) {
      // 中文注释：Actuator 通配代理只服务本地排障，生产域名避免暴露 metrics/loggers 等管理面。
      routes.push({
        source: "/actuator/:path*",
        destination: `${serverApiBaseUrl.replace(/\/+$/, "")}/actuator/:path*`,
      });
    }
    return routes;
  },
  async headers() {
    // 中文注释：安全响应头集中在 Next 层，避免各页面重复维护浏览器防护策略。
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: csp },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);

function buildAllowedDevOrigins(rawOrigins: string | undefined) {
  const configuredOrigins = (rawOrigins ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const localNetworkOrigins = Object.values(networkInterfaces())
    .flatMap((items) => items ?? [])
    .filter((item) => item.family === "IPv4" && !item.internal)
    .map((item) => item.address);

  // 中文注释：Next dev 会拦截跨 Host 的内部资源，局域网调试必须显式放行当前机器地址。
  return Array.from(new Set(["127.0.0.1", ...localNetworkOrigins, ...configuredOrigins]));
}
