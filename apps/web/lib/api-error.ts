export type ApiErrorPayload = {
    timestamp?: string;
    error?: string;
    code?: string;
    message?: string;
    context?: Record<string, string | number | boolean | null | undefined>;
    fields?: Record<string, string | string[]>;
    path?: string;
    traceId?: string;
    status?: number;
};

export class ApiRequestError extends Error {
    code?: string;
    status?: number;
    context?: Record<string, string | number | boolean | null | undefined>;
    fields: Record<string, string>;
    path?: string;
    traceId?: string;
    error?: string;
    timestamp?: string;

    constructor(payload: ApiErrorPayload, fallbackMessage: string) {
        super(typeof payload.message === "string" && payload.message.trim() ? payload.message : fallbackMessage);
        this.name = "ApiRequestError";
        this.code = payload.code;
        this.status = payload.status;
        this.context = payload.context;
        this.fields = normalizeFields(payload.fields);
        this.path = payload.path;
        this.traceId = payload.traceId;
        this.error = payload.error;
        this.timestamp = payload.timestamp;
    }
}

export function isApiStatus(error: unknown, statuses: number[]) {
    return error instanceof ApiRequestError && typeof error.status === "number" && statuses.includes(error.status);
}

export function isAuthExpired(error: unknown) {
    return isApiStatus(error, [401, 403]);
}

function normalizeFields(fields: ApiErrorPayload["fields"]): Record<string, string> {
    if (!fields || typeof fields !== "object") return {};
    return Object.fromEntries(
        Object.entries(fields).map(([field, value]) => [
            field,
            Array.isArray(value) ? value.find((item) => typeof item === "string") ?? "validation.failed" : value,
        ]).filter(([, value]) => typeof value === "string" && value.trim()),
    ) as Record<string, string>;
}
