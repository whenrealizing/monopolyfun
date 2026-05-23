import type {AuthSession} from "@/lib/api";
import {saveSession} from "@/lib/client-preferences";

export function persistAuthSession(session: AuthSession) {
    // 中文注释：所有登录恢复路径共用同一份本地会话投影，避免 Shell、登录页和 OAuth 回调各自拼字段。
    saveSession({
        accountId: session.account.id,
        displayName: session.account.displaySkin.displayName ?? session.account.displayName,
        handle: session.account.displaySkin.displayHandle ?? session.account.handle,
        expiresAt: session.expiresAt,
    });
}
