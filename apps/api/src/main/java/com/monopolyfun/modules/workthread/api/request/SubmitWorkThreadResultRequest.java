package com.monopolyfun.modules.workthread.api.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SubmitWorkThreadResultRequest(
        @NotBlank String actorAccountId,
        @NotBlank String resultMarkdown,
        String summary,
        String prUrl,
        String testSummary,
        List<String> changedFiles,
        List<String> evidenceRefs,
        String runtime
) {
}
