package com.monopolyfun.modules.workthread.domain;

import java.time.Instant;
import java.util.List;

public record WorkResultEntity(
        String id,
        String resultNo,
        String workThreadId,
        String actorAccountId,
        String resultMarkdown,
        String summary,
        String prUrl,
        String testSummary,
        List<String> changedFiles,
        List<String> evidenceRefs,
        String runtime,
        String status,
        Instant createdAt
) {
    public WorkResultEntity {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
