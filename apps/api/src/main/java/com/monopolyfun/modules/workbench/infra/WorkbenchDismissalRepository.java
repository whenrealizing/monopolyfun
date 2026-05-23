package com.monopolyfun.modules.workbench.infra;

import com.monopolyfun.modules.workbench.domain.WorkbenchDismissalEntity;

import java.util.Set;

public interface WorkbenchDismissalRepository {
    Set<String> findItemKeysByAccountId(String accountId);

    WorkbenchDismissalEntity save(WorkbenchDismissalEntity dismissal);
}
