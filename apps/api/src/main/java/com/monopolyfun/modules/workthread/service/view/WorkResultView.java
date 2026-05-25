package com.monopolyfun.modules.workthread.service.view;

import java.util.List;

public record WorkResultView(
        String id,
        String resultNo,
        String workThreadId,
        String actorAccountId,
        String summary,
        String prUrl,
        String testSummary,
        List<String> changedFiles,
        List<String> evidenceRefs,
        String runtime,
        String status,
        String createdAt
) {
}
