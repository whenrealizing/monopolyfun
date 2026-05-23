import type {AppLocale} from "@/i18n/locale-config";

type Messages = Record<string, unknown>;

const MESSAGE_GROUPS = [
  "shell",
  "market",
  "publish",
  "orders",
  "identity",
  "operations",
  "support",
  "backoffice",
] as const;

export async function loadMessages(locale: AppLocale) {
  const entries = await Promise.all(
    MESSAGE_GROUPS.map(async (group) => {
      const messages = await import(`./${locale}/${group}.json`);
      return messages.default as Messages;
    }),
  );

  return Object.assign({}, ...entries) as Messages;
}
