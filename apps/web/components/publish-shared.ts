export type PublishType = "offer" | "request" | "project";

export function readPublishType(value: string | null): PublishType {
  // 服务端页面和客户端工作区共用同一套类型归一化，避免跨端调用 client 模块。
  return value === "request" || value === "project" || value === "trade" ? (value === "trade" ? "offer" : value) : "offer";
}
