package com.monopolyfun.modules.workbench.infra.postgres;

import com.monopolyfun.modules.workbench.domain.WorkbenchDismissalEntity;
import com.monopolyfun.modules.workbench.infra.WorkbenchDismissalRepository;
import com.monopolyfun.shared.persistence.postgres.PostgresJson;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Set;

import static com.monopolyfun.generated.jooq.Tables.WORKBENCH_DISMISSALS;

@Repository
public class PostgresWorkbenchDismissalRepository implements WorkbenchDismissalRepository {
    private final DSLContext dsl;

    public PostgresWorkbenchDismissalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<String> findItemKeysByAccountId(String accountId) {
        return dsl.select(WORKBENCH_DISMISSALS.ITEM_KEY)
                .from(WORKBENCH_DISMISSALS)
                .where(WORKBENCH_DISMISSALS.ACCOUNT_ID.eq(accountId))
                .fetchSet(WORKBENCH_DISMISSALS.ITEM_KEY);
    }

    @Override
    public WorkbenchDismissalEntity save(WorkbenchDismissalEntity dismissal) {
        dsl.insertInto(WORKBENCH_DISMISSALS)
                .set(WORKBENCH_DISMISSALS.ACCOUNT_ID, dismissal.accountId())
                .set(WORKBENCH_DISMISSALS.ITEM_KEY, dismissal.itemKey())
                .set(WORKBENCH_DISMISSALS.REASON, dismissal.reason())
                .set(WORKBENCH_DISMISSALS.SUBJECT_TYPE, dismissal.subjectType())
                .set(WORKBENCH_DISMISSALS.SUBJECT_ID, dismissal.subjectId())
                .set(WORKBENCH_DISMISSALS.DISMISSED_AT, PostgresJson.offsetDateTime(dismissal.dismissedAt()))
                .onConflict(WORKBENCH_DISMISSALS.ACCOUNT_ID, WORKBENCH_DISMISSALS.ITEM_KEY)
                .doUpdate()
                .set(WORKBENCH_DISMISSALS.DISMISSED_AT, PostgresJson.offsetDateTime(dismissal.dismissedAt()))
                .execute();
        return dismissal;
    }
}
