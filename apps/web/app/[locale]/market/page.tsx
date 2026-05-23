import {redirect} from "next/navigation";

export default async function MarketPage({
                                             params,
                                             searchParams,
                                         }: {
    params: Promise<{ locale: string }>;
    searchParams?: Promise<Record<string, string | string[] | undefined>>;
}) {
    const [{locale}, query] = await Promise.all([params, searchParams ?? Promise.resolve({})]);
    const target = buildHomeHref(locale, query);
    redirect(target);
}

function buildHomeHref(locale: string, query: Record<string, string | string[] | undefined>) {
    const params = new URLSearchParams();
    const kind = firstValue(query.kind);
    const sort = firstValue(query.sort);
    const search = firstValue(query.q);

    if (kind === "offer" || kind === "request" || kind === "project") params.set("kind", kind);
    if (sort === "oldest" || sort === "title") params.set("sort", sort);
    if (search) params.set("q", search);

    const prefix = locale === "en" ? "/en" : "";
    const serialized = params.toString();
    return serialized ? `${prefix}/?${serialized}` : `${prefix}/`;
}

function firstValue(value: string | string[] | undefined) {
    return Array.isArray(value) ? value[0] : value;
}
