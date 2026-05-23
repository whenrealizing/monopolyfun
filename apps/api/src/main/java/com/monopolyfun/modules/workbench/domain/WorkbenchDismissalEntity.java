package com.monopolyfun.modules.workbench.domain;

import java.time.Instant;

public record WorkbenchDismissalEntity(
        String accountId,
        String itemKey,
        String reason,
        String subjectType,
        String subjectId,
        Instant dismissedAt
) {
}
