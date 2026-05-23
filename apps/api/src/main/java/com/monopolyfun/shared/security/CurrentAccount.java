package com.monopolyfun.shared.security;

import java.io.Serializable;
import java.security.Principal;

public record CurrentAccount(
        String accountId,
        String handle,
        String displayName
) implements Principal, Serializable {
    @Override
    public String getName() {
        // 中文注释：Spring Session 用 principal name 建索引，统一采用稳定账号 id 便于全端登出。
        return accountId;
    }
}
