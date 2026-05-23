import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { CircleDot, ListTree } from "lucide-react";

export type SupportContentNavItem = {
  id: string;
  label: string;
};

export function SupportContentPage({
  eyebrow,
  title,
  description,
  navItems = [],
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  navItems?: SupportContentNavItem[];
  children: ReactNode;
}) {
  const t = useTranslations("SupportContent");
  return (
    <div className="bg-[var(--background)]">
      <article className="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
        <header className="py-2">
          {eyebrow ? (
            <div className="inline-flex items-center gap-2 rounded-[6px] bg-[var(--primary-soft)] px-3 py-1.5 text-[12px] font-semibold leading-4 text-[var(--secondary-foreground)]">
              <CircleDot className="h-4 w-4 text-[var(--primary-hover)]" />
              {eyebrow}
            </div>
          ) : null}
          <h1 className="mt-3 max-w-4xl text-[32px] font-bold leading-[1.12] tracking-[0] text-[var(--foreground)]">
            {title}
          </h1>
          <p className="mt-2 max-w-3xl text-[14px] leading-5 text-[var(--muted-foreground)]">
            {description}
          </p>
        </header>

        <div className="grid gap-6 py-6 lg:grid-cols-[230px_minmax(0,1fr)] lg:py-8">
          {navItems.length > 0 ? (
            <>
              <div className="rounded-[6px] bg-[var(--background)] p-3 lg:hidden">
                <div className="flex min-h-8 items-center gap-2 text-[13px] font-semibold leading-5 text-[var(--foreground)]">
                  <ListTree className="h-4 w-4 text-[var(--primary-hover)]" aria-hidden="true" />
                  {t("contents")}
                </div>
                <nav className="mt-3 flex flex-wrap gap-2" aria-label={t("contents")}>
                  {navItems.map((item) => (
                    <a
                      key={item.id}
                      href={`#${item.id}`}
                      className="inline-flex min-h-9 items-center rounded-[6px] bg-[var(--surface-1)] px-3 py-2 text-[13px] font-semibold leading-5 text-[var(--muted-foreground)] transition hover:bg-[var(--surface-hover)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--primary-border)]"
                    >
                      {item.label}
                    </a>
                  ))}
                </nav>
              </div>
              <nav className="hidden lg:sticky lg:top-24 lg:block lg:self-start" aria-label={t("contents")}>
                <div className="rounded-[6px] bg-[var(--background)] p-3">
                  <div className="px-2 text-[12px] font-semibold leading-4 text-[var(--muted-foreground)]">{t("contents")}</div>
                  <div className="mt-3 flex flex-col gap-2">
                    {navItems.map((item) => (
                      <a
                        key={item.id}
                        href={`#${item.id}`}
                        className="inline-flex min-h-10 items-center rounded-[6px] px-3 py-2 text-[14px] font-semibold leading-5 text-[var(--muted-foreground)] transition hover:bg-[var(--surface-hover)] hover:text-[var(--foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--primary-border)]"
                      >
                        {item.label}
                      </a>
                    ))}
                  </div>
                </div>
              </nav>
            </>
          ) : (
            <div className="hidden lg:block" />
          )}

          <div className="min-w-0 space-y-8">{children}</div>
        </div>
      </article>
    </div>
  );
}

export function SupportContentSection({
  id,
  title,
  description,
  children,
}: {
  id: string;
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-24 border-b border-[var(--border)] pb-8 last:border-b-0 last:pb-0">
      <div className="max-w-3xl">
        <h2 className="text-[20px] font-bold leading-7 tracking-[0] text-[var(--foreground)]">{title}</h2>
        {description ? (
          <p className="mt-2 text-[14px] leading-5 text-[var(--muted-foreground)]">{description}</p>
        ) : null}
      </div>
      <div className="mt-5">{children}</div>
    </section>
  );
}

export function SupportContentBulletList({ items }: { items: readonly string[] }) {
  return (
    <ul className="space-y-2 text-[14px] leading-5 text-[var(--muted-foreground)]">
      {items.map((item) => (
        <li key={item} className="flex gap-3">
          <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-[var(--success)]" aria-hidden="true" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}
